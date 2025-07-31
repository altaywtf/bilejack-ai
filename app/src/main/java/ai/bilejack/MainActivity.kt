package ai.bilejack

import ai.bilejack.data.AppDatabase
import ai.bilejack.data.Message
import ai.bilejack.data.MessageRepository
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val tag = "MainActivity"
    private val requestCode = 1001

    private lateinit var database: AppDatabase
    private lateinit var messageRepository: MessageRepository
    private lateinit var whitelistManager: WhitelistManager
    private lateinit var llmModelManager: LlmModelManager

    // UI Components
    private lateinit var statsReceived: TextView
    private lateinit var statsSent: TextView
    private lateinit var statsErrors: TextView
    private lateinit var statusLlm: TextView
    private lateinit var statusNetwork: TextView
    private lateinit var messagesList: ListView
    private lateinit var messagesAdapter: MonochromeArrayAdapter

    // Whitelist UI components
    private lateinit var whitelistSummary: TextView
    private lateinit var editPhoneNumber: EditText

    // LLM Model UI components
    private lateinit var modelSummary: TextView

    // RxJava disposables for reactive updates
    private val disposables = CompositeDisposable()

    private val requiredPermissions =
        arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Configure system UI for dark theme with proper safe area handling
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.black)

        // Use modern window insets instead of deprecated system UI visibility flags
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }

        database = AppDatabase.getDatabase(this)
        messageRepository = MessageRepository(this)
        whitelistManager = WhitelistManager(this)
        llmModelManager = LlmModelManager(this)

        initializeUI()
        checkPermissions()
        startService()

        // Test log to verify Logcat is working
        Log.d(tag, "🚀 MainActivity created successfully - Logcat test!")
        Log.i(tag, "📱 App version: ${packageManager.getPackageInfo(packageName, 0).versionName}")

        // Start reactive subscriptions for real-time updates
        startReactiveUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    private fun initializeUI() {
        statsReceived = findViewById(R.id.stats_received)
        statsSent = findViewById(R.id.stats_sent)
        statsErrors = findViewById(R.id.stats_errors)
        statusLlm = findViewById(R.id.status_llm)
        statusNetwork = findViewById(R.id.status_network)
        messagesList = findViewById(R.id.messages_list)

        // Whitelist UI elements
        whitelistSummary = findViewById(R.id.whitelist_summary)
        editPhoneNumber = findViewById(R.id.edit_phone_number)

        // LLM Model UI elements
        modelSummary = findViewById(R.id.model_summary)

        messagesAdapter = MonochromeArrayAdapter(this, mutableListOf())
        messagesList.adapter = messagesAdapter

        findViewById<Button>(R.id.btn_clear_log).setOnClickListener { clearLog() }
        findViewById<Button>(R.id.btn_restart_service).setOnClickListener { restartService() }
        findViewById<Button>(R.id.btn_test_llm).setOnClickListener { testLlmConnection() }

        // Whitelist management buttons
        findViewById<Button>(R.id.btn_add_number).setOnClickListener { addPhoneNumber() }
        findViewById<Button>(R.id.btn_manage_whitelist).setOnClickListener { showWhitelistDialog() }
        findViewById<Button>(R.id.btn_clear_whitelist).setOnClickListener { clearWhitelist() }

        // LLM Model management buttons
        findViewById<Button>(R.id.btn_select_model).setOnClickListener { showModelSelectionDialog() }

        // Update summaries initially
        updateWhitelistSummary()
        updateModelSummary()
    }

    private fun checkPermissions() {
        val missingPermissions =
            requiredPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), requestCode)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == this.requestCode) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                showMonochromeToast("PERMISSIONS REQUIRED")
            }
            // Reactive updates will automatically refresh when permissions are granted
        }
    }

    private fun startService() {
        val serviceIntent = Intent(this, SmsRelayService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun startReactiveUpdates() {
        Log.d(tag, "Starting reactive subscriptions for real-time updates")

        // Subscribe to message statistics for real-time updates
        val statsDisposable =
            messageRepository.getStatsStream()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { stats ->
                        Log.d(tag, "Received stats update: $stats")
                        updateStatsUI(stats)
                    },
                    { error ->
                        Log.e(tag, "Error in stats stream", error)
                        showMonochromeToast("ERROR: ${error.message}")
                    },
                )

        // Subscribe to messages list for real-time updates
        val messagesDisposable =
            messageRepository.getAllMessagesStream()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { messages ->
                        Log.d(tag, "📜 Received messages update: ${messages.size} messages")
                        if (messages.isNotEmpty()) {
                            Log.d(
                                tag,
                                "📜 Latest message: ${messages.first().phoneNumber} - ${messages.first().incomingSms}",
                            )
                        }
                        updateMessagesUI(messages)
                    },
                    { error ->
                        Log.e(tag, "Error in messages stream", error)
                        showMonochromeToast("ERROR: ${error.message}")
                    },
                )

        // Subscribe to network and GPT status updates (refresh every 30 seconds)
        val statusDisposable =
            Flowable.interval(30, java.util.concurrent.TimeUnit.SECONDS, Schedulers.io())
                .startWith(Flowable.just(0L)) // Emit immediately on subscribe
                .map {
                    // Check network and GPT status
                    val isNetworkAvailable =
                        try {
                            checkNetworkConnectivity()
                        } catch (e: Exception) {
                            Log.e(tag, "Network check failed", e)
                            false
                        }
                    val isLlmApiReachable = llmModelManager.isConfigured()

                    isNetworkAvailable to isLlmApiReachable
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { (isNetworkAvailable: Boolean, isLlmApiReachable: Boolean) ->
                        updateStatusUI(isNetworkAvailable, isLlmApiReachable)
                    },
                    { error ->
                        Log.e(tag, "Error checking status", error)
                    },
                )

        disposables.addAll(statsDisposable, messagesDisposable, statusDisposable)
        Log.d(tag, "Reactive subscriptions started successfully")
    }

    private fun updateStatsUI(stats: MessageRepository.MessageStats) {
        statsReceived.text = "📩 ${stats.totalMessages}"
        statsSent.text = "📤 ${stats.processedMessages}"
        statsErrors.text = "❌ ${stats.errorMessages}"
    }

    private fun updateMessagesUI(messages: List<Message>) {
        val messageStrings = messages.map { formatMessage(it) }
        Log.d(tag, "Updating UI with ${messageStrings.size} messages")
        messagesAdapter.clear()
        messagesAdapter.addAll(messageStrings)
        messagesAdapter.notifyDataSetChanged()
    }

    private fun updateStatusUI(
        isNetworkAvailable: Boolean,
        isLlmApiReachable: Boolean,
    ) {
        statusLlm.text = if (isLlmApiReachable) "✅ LLM API" else "❌ LLM API"
        statusNetwork.text = if (isNetworkAvailable) "✅ NETWORK" else "❌ NETWORK"
    }

    private fun checkNetworkConnectivity(): Boolean {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val hasValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            Log.d(tag, "Network check: hasInternet=$hasInternet, hasValidated=$hasValidated")
            hasInternet && hasValidated
        } catch (e: Exception) {
            Log.e(tag, "Network connectivity check failed", e)
            false
        }
    }

    private fun formatMessage(message: Message): String {
        val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        val time = dateFormat.format(Date(message.timestamp))
        val status =
            when {
                message.hasError -> "❌"
                message.isProcessed -> "✅"
                else -> "⏳"
            }

        val truncatedMessage =
            if (message.incomingSms.length > 50) {
                message.incomingSms.take(50) + "..."
            } else {
                message.incomingSms
            }

        return "$status $time\n${message.phoneNumber}\n$truncatedMessage"
    }

    private fun clearLog() {
        showConfirmDialog(
            title = "CLEAR MESSAGE HISTORY",
            message = "Delete all message history?",
            positiveText = "DELETE",
            onConfirm = {
                lifecycleScope.launch {
                    messageRepository.clearAllMessages()
                    showMonochromeToast("LOG CLEARED")
                }
            },
        )
    }

    private fun restartService() {
        stopService(Intent(this, SmsRelayService::class.java))
        startService()
        showMonochromeToast("SERVICE RESTARTED")
    }

    private fun testLlmConnection() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    showMonochromeToast("🧪 TESTING LLM...")
                }

                Log.d(tag, "🗣️ Starting LLM connection test...")
                val isConnected = llmModelManager.testConnection()
                Log.d(tag, "🎆 LLM connection test result: $isConnected")

                withContext(Dispatchers.Main) {
                    val message = if (isConnected) "✅ LLM CONNECTION OK" else "❌ LLM CONNECTION FAILED"
                    showMonochromeToast(message)
                    // Status will update automatically via reactive streams
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ LLM connection test failed with exception: ${e.javaClass.simpleName}: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showMonochromeToast("❌ LLM TEST FAILED: ${e.message}")
                    // Status will update automatically via reactive streams
                }
            }
        }
    }

    private fun showConfirmDialog(
        title: String,
        message: String,
        positiveText: String,
        onConfirm: () -> Unit,
    ) {
        AlertDialog.Builder(this, R.style.MonochromeAlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onConfirm() }
            .setNegativeButton("CANCEL", null)
            .setCancelable(true)
            .show()
    }

    private fun showMonochromeToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Whitelist Management Functions

    private fun updateWhitelistSummary() {
        whitelistSummary.text = whitelistManager.getWhitelistSummary()
    }

    private fun addPhoneNumber() {
        val phoneNumber = editPhoneNumber.text.toString()

        // Validate phone number
        val validationError = whitelistManager.validatePhoneNumber(phoneNumber)
        if (validationError != null) {
            showMonochromeToast("❌ $validationError")
            return
        }

        // Try to add the number
        if (whitelistManager.addPhoneNumber(phoneNumber)) {
            showMonochromeToast("✅ Added: $phoneNumber")
            editPhoneNumber.setText("")
            updateWhitelistSummary()
        } else {
            showMonochromeToast("⚠️ Number already exists")
        }
    }

    private fun clearWhitelist() {
        showConfirmDialog(
            title = "CLEAR WHITELIST",
            message = "Remove ALL allowed phone numbers? This will block all incoming SMS until you add new numbers.",
            positiveText = "CLEAR ALL",
            onConfirm = {
                whitelistManager.saveAllowedNumbers("")
                updateWhitelistSummary()
                showMonochromeToast("🗑️ Whitelist cleared")
            },
        )
    }

    private fun showWhitelistDialog() {
        val numbers = whitelistManager.getAllowedNumbers()

        if (numbers.isEmpty()) {
            showMonochromeToast("📝 No numbers in whitelist")
            return
        }

        val numbersArray = numbers.toTypedArray()
        val checkedItems = BooleanArray(numbers.size) { false }

        AlertDialog.Builder(this, R.style.MonochromeAlertDialog)
            .setTitle("MANAGE WHITELIST")
            .setMultiChoiceItems(numbersArray, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("DELETE SELECTED") { _, _ ->
                val toDelete = mutableListOf<String>()
                for (i in checkedItems.indices) {
                    if (checkedItems[i]) {
                        toDelete.add(numbersArray[i])
                    }
                }

                if (toDelete.isNotEmpty()) {
                    showConfirmDialog(
                        title = "DELETE NUMBERS",
                        message = "Delete ${toDelete.size} selected number(s)?",
                        positiveText = "DELETE",
                        onConfirm = {
                            var deletedCount = 0
                            for (number in toDelete) {
                                if (whitelistManager.removePhoneNumber(number)) {
                                    deletedCount++
                                }
                            }
                            updateWhitelistSummary()
                            showMonochromeToast("🗑️ Deleted $deletedCount number(s)")
                        },
                    )
                } else {
                    showMonochromeToast("No numbers selected")
                }
            }
            .setNegativeButton("CANCEL", null)
            .setCancelable(true)
            .show()
    }

    // LLM Model Management Functions
    private fun updateModelSummary() {
        modelSummary.text = llmModelManager.getModelSummary()
    }

    private fun showModelSelectionDialog() {
        val models = llmModelManager.getAvailableModels()
        val currentModel = llmModelManager.getSelectedModel()

        var selectedIndex = models.indexOf(currentModel)
        if (selectedIndex == -1) selectedIndex = 0

        AlertDialog.Builder(this, R.style.MonochromeAlertDialog)
            .setTitle("🤖 SELECT MODEL")
            .setSingleChoiceItems(models.toTypedArray(), selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("SELECT") { _, _ ->
                if (selectedIndex >= 0 && selectedIndex < models.size) {
                    val selectedModel = models[selectedIndex]
                    llmModelManager.setSelectedModel(selectedModel)
                    updateModelSummary()
                    showMonochromeToast("🤖 Selected: $selectedModel")
                }
            }
            .setNegativeButton("CANCEL", null)
            .setCancelable(true)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Reactive streams will automatically update the UI
    }

    // Custom ArrayAdapter for monochrome theme
    private class MonochromeArrayAdapter(
        private val context: Context,
        private val objects: MutableList<String>,
    ) : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, objects) {
        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View {
            val textView =
                TextView(context).apply {
                    text = getItem(position)
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    setBackgroundColor(ContextCompat.getColor(context, R.color.gray2))
                    textSize = 12f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setPadding(16, 12, 16, 12)
                    maxLines = 3
                }
            return textView
        }
    }
}
