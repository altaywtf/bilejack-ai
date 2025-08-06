package ai.bilejack

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val tag = "MainActivity"
    private val requestCode = 1001

    private lateinit var whitelistedNumbersManager: WhitelistedNumbersManager
    private lateinit var openRouterModelManager: OpenRouterModelManager

    private lateinit var statusLlm: TextView
    private lateinit var statusNetwork: TextView
    private lateinit var whitelistSummary: TextView
    private lateinit var editPhoneNumber: EditText
    private lateinit var modelSummary: TextView

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

        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.black)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }

        whitelistedNumbersManager = WhitelistedNumbersManager(this)
        openRouterModelManager = OpenRouterModelManager(this)

        initializeUI()
        checkPermissions()
        startService()

        Log.d(tag, "🚀 MainActivity created successfully")
        Log.i(tag, "📱 App version: ${packageManager.getPackageInfo(packageName, 0).versionName}")

        startStatusUpdates()
    }

    private fun initializeUI() {
        statusLlm = findViewById(R.id.status_llm)
        statusNetwork = findViewById(R.id.status_network)
        whitelistSummary = findViewById(R.id.whitelist_summary)
        editPhoneNumber = findViewById(R.id.edit_phone_number)
        modelSummary = findViewById(R.id.model_summary)

        findViewById<Button>(R.id.btn_restart_service).setOnClickListener { restartService() }
        findViewById<Button>(R.id.btn_test_llm).setOnClickListener { testLlmConnection() }

        findViewById<Button>(R.id.btn_add_number).setOnClickListener { addPhoneNumber() }
        findViewById<Button>(R.id.btn_manage_whitelist).setOnClickListener { showWhitelistDialog() }
        findViewById<Button>(R.id.btn_clear_whitelist).setOnClickListener { clearWhitelist() }

        findViewById<Button>(R.id.btn_select_model).setOnClickListener { showModelSelectionDialog() }

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
        }
    }

    private fun startService() {
        val serviceIntent = Intent(this, SmsRelayService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun startStatusUpdates() {
        lifecycleScope.launch {
            updateStatusUI()
        }
    }

    private fun updateStatusUI() {
        val isNetworkAvailable = checkNetworkConnectivity()
        val isLlmApiReachable = openRouterModelManager.isConfigured()

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
                val isConnected = openRouterModelManager.testConnection()
                Log.d(tag, "🎆 LLM connection test result: $isConnected")

                withContext(Dispatchers.Main) {
                    val message = if (isConnected) "✅ LLM CONNECTION OK" else "❌ LLM CONNECTION FAILED"
                    showMonochromeToast(message)
                }
            } catch (e: Exception) {
                Log.e(tag, "❌ LLM connection test failed with exception: ${e.javaClass.simpleName}: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showMonochromeToast("❌ LLM TEST FAILED: ${e.message}")
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

    private fun updateWhitelistSummary() {
        whitelistSummary.text = whitelistedNumbersManager.getWhitelistSummary()
    }

    private fun addPhoneNumber() {
        val phoneNumber = editPhoneNumber.text.toString()

        val validationError = whitelistedNumbersManager.validatePhoneNumber(phoneNumber)
        if (validationError != null) {
            showMonochromeToast("❌ $validationError")
            return
        }

        if (whitelistedNumbersManager.addPhoneNumber(phoneNumber)) {
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
            message = "Remove ALL whitelisted numbers? This will block all incoming SMS until you add new numbers.",
            positiveText = "CLEAR ALL",
            onConfirm = {
                whitelistedNumbersManager.saveAllowedNumbers("")
                updateWhitelistSummary()
                showMonochromeToast("🗑️ Whitelist cleared")
            },
        )
    }

    private fun showWhitelistDialog() {
        val numbers = whitelistedNumbersManager.getAllowedNumbers()

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
                                if (whitelistedNumbersManager.removePhoneNumber(number)) {
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

    private fun updateModelSummary() {
        modelSummary.text = openRouterModelManager.getModelSummary()
    }

    private fun showModelSelectionDialog() {
        val models = openRouterModelManager.getAvailableModels()
        val currentModel = openRouterModelManager.getSelectedModel()

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
                    openRouterModelManager.setSelectedModel(selectedModel)
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
        lifecycleScope.launch {
            updateStatusUI()
        }
    }
}
