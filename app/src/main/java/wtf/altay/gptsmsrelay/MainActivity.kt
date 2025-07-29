package wtf.altay.gptsmsrelay

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
import wtf.altay.gptsmsrelay.data.AppDatabase
import wtf.altay.gptsmsrelay.data.Message
import wtf.altay.gptsmsrelay.data.MessageRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val tag = "MainActivity"
    private val requestCode = 1001

    private lateinit var database: AppDatabase
    private lateinit var gptClient: GptClient
    private lateinit var messageRepository: MessageRepository

    // UI Components
    private lateinit var statsReceived: TextView
    private lateinit var statsSent: TextView
    private lateinit var statsErrors: TextView
    private lateinit var statusGpt: TextView
    private lateinit var statusNetwork: TextView
    private lateinit var messagesList: ListView
    private lateinit var messagesAdapter: MonochromeArrayAdapter

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

        // Configure system UI for dark theme with safe area handling
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.black)
        
        // Ensure content renders properly with system bars
        window.decorView.systemUiVisibility = (
            window.decorView.systemUiVisibility or 
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        database = AppDatabase.getDatabase(this)
        gptClient = GptClient(this)
        messageRepository = MessageRepository(this)

        initializeUI()
        checkPermissions()
        startService()

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
        statusGpt = findViewById(R.id.status_gpt)
        statusNetwork = findViewById(R.id.status_network)
        messagesList = findViewById(R.id.messages_list)

        messagesAdapter = MonochromeArrayAdapter(this, mutableListOf())
        messagesList.adapter = messagesAdapter

        findViewById<Button>(R.id.btn_clear_log).setOnClickListener { clearLog() }
        findViewById<Button>(R.id.btn_restart_service).setOnClickListener { restartService() }
        findViewById<Button>(R.id.btn_test_gpt).setOnClickListener { testGptConnection() }
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
                        Log.d(tag, "Received messages update: ${messages.size} messages")
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
                    val isGptApiReachable = gptClient.isApiKeyConfigured()

                    isNetworkAvailable to isGptApiReachable
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { (isNetworkAvailable: Boolean, isGptApiReachable: Boolean) ->
                        updateStatusUI(isNetworkAvailable, isGptApiReachable)
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
        isGptApiReachable: Boolean,
    ) {
        statusGpt.text = if (isGptApiReachable) "✅ GPT API" else "❌ GPT API"
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
            title = "CLEAR LOG",
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

    private fun testGptConnection() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    showMonochromeToast("TESTING GPT...")
                }

                Log.d(tag, "Starting GPT connection test...")
                val isConnected = gptClient.testConnection()
                Log.d(tag, "GPT connection test result: $isConnected")

                withContext(Dispatchers.Main) {
                    val message = if (isConnected) "✅ GPT CONNECTION OK" else "❌ GPT CONNECTION FAILED"
                    showMonochromeToast(message)
                    // Status will update automatically via reactive streams
                }
            } catch (e: Exception) {
                Log.e(tag, "GPT connection test failed with exception: ${e.javaClass.simpleName}: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showMonochromeToast("❌ GPT TEST FAILED: ${e.message}")
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
                    setBackgroundColor(ContextCompat.getColor(context, R.color.surface_color))
                    textSize = 14f
                    typeface = android.graphics.Typeface.MONOSPACE
                    setPadding(16, 12, 16, 12)
                    maxLines = 3
                }
            return textView
        }
    }
}
