package wtf.altay.gptsmsrelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class SmsRelayService : LifecycleService() {
    private val tag = "SmsRelayService"
    private val channelId = "gpt_sms_relay_channel"
    private val notificationId = 1

    private lateinit var gptClient: GptClient
    private var preferredSmsManager: SmsManager? = null
    private var subscriptionId: Int = -1
    private var selectedCarrierName: String = "Unknown"
    
    // Simple in-memory tracking - no database bullshit
    private val processedMessages = mutableSetOf<String>() // phone_number:message_hash
    private val currentlyProcessing = mutableSetOf<String>()
    
    // Lightweight crash-resilient state
    private lateinit var prefs: SharedPreferences

    companion object {
        @Volatile
        private var instance: SmsRelayService? = null
        
        fun getInstance(): SmsRelayService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        gptClient = GptClient(this)
        prefs = getSharedPreferences("sms_relay_state", Context.MODE_PRIVATE)

        // Setup SIM management
        setupSmsManager()

        createNotificationChannel()
        startForeground(notificationId, createNotification())

        Log.d(tag, "Service created with SIM: $selectedCarrierName (ID: $subscriptionId)")
        
        // Clear any stuck processing state from crashes
        clearStuckProcessingState()
    }

    private fun clearStuckProcessingState() {
        val stuckMessages = prefs.getStringSet("processing", emptySet()) ?: emptySet()
        if (stuckMessages.isNotEmpty()) {
            Log.d(tag, "Clearing ${stuckMessages.size} stuck processing messages from previous crash")
            prefs.edit().remove("processing").apply()
        }
        
        // Restore processed messages to avoid reprocessing
        val processedFromPrefs = prefs.getStringSet("processed", emptySet()) ?: emptySet()
        processedMessages.addAll(processedFromPrefs)
        Log.d(tag, "Restored ${processedFromPrefs.size} previously processed messages")
        
        // Clean up old processed messages (keep only last 100 to prevent memory bloat)
        if (processedMessages.size > 100) {
            val toRemove = processedMessages.drop(processedMessages.size - 100)
            processedMessages.removeAll(toRemove.toSet())
            prefs.edit().putStringSet("processed", processedMessages).apply()
            Log.d(tag, "Cleaned up old processed messages, kept ${processedMessages.size}")
        }
    }

    // Called by SmsReceiver with the incoming message
    fun processIncomingSms(phoneNumber: String, messageBody: String) {
        val messageKey = "$phoneNumber:${messageBody.hashCode()}"
        
        synchronized(this) {
            if (processedMessages.contains(messageKey) || currentlyProcessing.contains(messageKey)) {
                Log.d(tag, "Message already processed or processing: $messageKey")
                return
            }
            currentlyProcessing.add(messageKey)
            
            // Track in crash-resilient storage
            val processing = prefs.getStringSet("processing", emptySet())?.toMutableSet() ?: mutableSetOf()
            processing.add(messageKey)
            prefs.edit().putStringSet("processing", processing).apply()
        }

        // Process on background thread to prevent ANR
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(tag, "Processing SMS from $phoneNumber: $messageBody")

                // Get GPT response
                val gptResponse = gptClient.sendMessage(messageBody)

                Log.d(tag, "GPT response: $gptResponse")

                // Switch back to main thread for UI updates but keep SMS sending on background
                withContext(Dispatchers.Main) {
                    Log.d(tag, "Starting SMS sending process")
                }

                // Brief pause to ensure system is ready
                Log.d(tag, "Waiting 1 second before starting SMS sending...")
                delay(1000)

                // Split and send SMS chunks (stay on IO thread)
                val smsChunks = splitMessageForSms(gptResponse)
                Log.d(tag, "📤 Ready to send ${smsChunks.size} SMS chunks to $phoneNumber")
                
                // Log all chunks that will be sent
                smsChunks.forEachIndexed { index, chunk ->
                    Log.d(tag, "📋 Chunk ${index + 1}/${smsChunks.size} prepared (${chunk.length} chars): $chunk")
                }

                var successfulChunks = 0
                var failedChunks = 0

                for ((index, chunk) in smsChunks.withIndex()) {
                    try {
                        Log.d(tag, "⏳ Attempting to send chunk ${index + 1}/${smsChunks.size}...")
                        val startTime = System.currentTimeMillis()
                        
                        sendSmsMessage(phoneNumber, chunk)
                        
                        val endTime = System.currentTimeMillis()
                        successfulChunks++
                        
                        Log.d(tag, "✅ Chunk ${index + 1}/${smsChunks.size} sent successfully in ${endTime - startTime}ms")
                        
                        // Add delay between chunks to prevent rate limiting (except for last chunk)
                        if (index < smsChunks.size - 1) {
                            Log.d(tag, "⏱️ Waiting 2 seconds before sending next chunk...")
                            delay(2000) // 2 second delay between chunks
                        }
                        
                    } catch (chunkError: Exception) {
                        failedChunks++
                        Log.e(tag, "❌ CHUNK ${index + 1}/${smsChunks.size} FAILED: ${chunkError.message}", chunkError)
                        
                        // Continue with other chunks even if one fails
                        try {
                            Log.d(tag, "🚨 Sending error notification for failed chunk ${index + 1}")
                            sendSmsMessage(phoneNumber, "Error: Failed to send message part ${index + 1}/${smsChunks.size}")
                        } catch (errorSmsError: Exception) {
                            Log.e(tag, "Failed to send error notification for chunk ${index + 1}", errorSmsError)
                        }
                    }
                }

                Log.d(tag, "📊 SMS BATCH COMPLETE: $successfulChunks✅ successful, $failedChunks❌ failed out of ${smsChunks.size} total chunks")

                // Mark as completed even if some chunks failed (to prevent reprocessing)
                // but log the outcome clearly
                val processingResult = when {
                    failedChunks == 0 -> "FULLY_SUCCESSFUL"
                    successfulChunks > 0 -> "PARTIALLY_SUCCESSFUL"
                    else -> "COMPLETELY_FAILED"
                }
                
                Log.d(tag, "📋 Message processing result: $processingResult")

                synchronized(this@SmsRelayService) {
                    processedMessages.add(messageKey)
                    currentlyProcessing.remove(messageKey)
                    
                    // Remove from crash-resilient storage and add to processed
                    val processing = prefs.getStringSet("processing", emptySet())?.toMutableSet() ?: mutableSetOf()
                    processing.remove(messageKey)
                    
                    val processed = prefs.getStringSet("processed", emptySet())?.toMutableSet() ?: mutableSetOf()
                    processed.add(messageKey)
                    
                    prefs.edit()
                        .putStringSet("processing", processing)
                        .putStringSet("processed", processed)
                        .apply()
                }

                Log.d(tag, "✅ Message from $phoneNumber marked as processed ($processingResult)")

            } catch (e: Exception) {
                Log.e(tag, "💥 FATAL ERROR processing message from $phoneNumber", e)

                // Only send error SMS if we haven't sent any chunks yet
                try {
                    Log.d(tag, "🚨 Sending generic error SMS due to fatal processing error")
                    sendSmsMessage(phoneNumber, "Error: Unable to process your message. Please try again later.")
                } catch (smsError: Exception) {
                    Log.e(tag, "Failed to send error SMS", smsError)
                }

                synchronized(this@SmsRelayService) {
                    currentlyProcessing.remove(messageKey)
                    
                    // Remove from crash-resilient storage (don't mark as processed since it truly failed)
                    val processing = prefs.getStringSet("processing", emptySet())?.toMutableSet() ?: mutableSetOf()
                    processing.remove(messageKey)
                    prefs.edit().putStringSet("processing", processing).apply()
                }
            }
        }
    }

    private fun setupSmsManager() {
        try {
            val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subscriptions = subscriptionManager.activeSubscriptionInfoList

            Log.d(tag, "Found ${subscriptions?.size ?: 0} active SIM cards")

            if (subscriptions != null && subscriptions.isNotEmpty()) {
                // Log all available SIMs for debugging
                subscriptions.forEachIndexed { index, sub ->
                    Log.d(
                        tag,
                        "SIM $index: Carrier='${sub.carrierName}', " +
                            "Display='${sub.displayName}', ID=${sub.subscriptionId}",
                    )
                }

                // Look for Turkcell SIM with multiple criteria
                val turkcellSim =
                    subscriptions.find { sub ->
                        val carrierName = sub.carrierName?.toString()?.lowercase() ?: ""
                        val displayName = sub.displayName?.toString()?.lowercase() ?: ""

                        Log.d(tag, "Checking SIM - Carrier: '$carrierName', Display: '$displayName'")

                        carrierName.contains("turkcell") ||
                            displayName.contains("turkcell") ||
                            carrierName.contains("tcell") ||
                            displayName.contains("tcell")
                    }

                if (turkcellSim != null) {
                    Log.d(tag, "Found Turkcell SIM: ${turkcellSim.carrierName}")
                    subscriptionId = turkcellSim.subscriptionId
                    selectedCarrierName = turkcellSim.carrierName?.toString() ?: "Turkcell"
                    preferredSmsManager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                } else {
                    // Use the second SIM if available (often the second slot is used for data/backup)
                    val selectedSim = if (subscriptions.size > 1) subscriptions[1] else subscriptions[0]
                    subscriptionId = selectedSim.subscriptionId
                    selectedCarrierName = selectedSim.carrierName?.toString()
                        ?: "SIM ${subscriptions.indexOf(selectedSim) + 1}"
                    preferredSmsManager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId)

                    Log.w(tag, "Turkcell SIM not found, using: $selectedCarrierName (ID: $subscriptionId)")
                }

                Log.d(tag, "Selected SIM: $selectedCarrierName (ID: $subscriptionId)")
            } else {
                // Fallback to default SMS manager
                preferredSmsManager = SmsManager.getDefault()
                selectedCarrierName = "Default"
                Log.w(tag, "No SIM cards found, using default SMS manager")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting up SMS manager", e)
            preferredSmsManager = SmsManager.getDefault()
            selectedCarrierName = "Default"
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(tag, "Service started")
        return START_STICKY // Restart if killed
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(tag, "Service destroyed")
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                channelId,
                "GPT SMS Relay",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Background service for SMS-GPT relay"
                setShowBadge(false)
            }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("GPT SMS Relay Active")
            .setContentText("Using: $selectedCarrierName")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun sendSmsMessage(
        phoneNumber: String,
        message: String,
    ) {
        try {
            // Validate inputs before sending
            if (phoneNumber.isBlank()) {
                throw IllegalArgumentException("Phone number cannot be blank")
            }
            if (message.isBlank()) {
                throw IllegalArgumentException("Message cannot be blank")
            }
            if (message.length > 160) {
                Log.w(tag, "📱 WARNING: Message length (${message.length}) exceeds typical SMS limit (160)")
            }
            
            Log.d(tag, "📱 Preparing to send SMS to $phoneNumber (${message.length} chars)")
            Log.d(tag, "📱 SMS content: $message")
            
            if (preferredSmsManager != null) {
                Log.d(tag, "📱 Using preferred SIM: $selectedCarrierName (ID: $subscriptionId)")
                
                preferredSmsManager!!.sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    null, // sentIntent - could add for delivery confirmation
                    null, // deliveryIntent
                )
                
                Log.d(tag, "📱 SMS dispatch successful via $selectedCarrierName")
            } else {
                Log.d(tag, "📱 Using default SMS manager")
                
                SmsManager.getDefault().sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    null,
                    null,
                )
                
                Log.d(tag, "📱 SMS dispatch successful via default SIM")
            }
            
            Log.d(tag, "📱 SMS sent successfully to $phoneNumber")
            
        } catch (e: Exception) {
            Log.e(tag, "📱 SMS SEND FAILED to $phoneNumber: ${e.javaClass.simpleName}: ${e.message}", e)
            
            // Log more details about the error
            when (e) {
                is IllegalArgumentException -> Log.e(tag, "📱 Invalid phone number or message format")
                is SecurityException -> Log.e(tag, "📱 Missing SMS permission or SIM card issue")
                else -> Log.e(tag, "📱 Unknown SMS error: ${e.javaClass.simpleName}")
            }
            
            throw e
        }
    }

    // Debug method to test SMS sending
    fun testSmsSending(phoneNumber: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(tag, "🧪 Testing SMS sending capability...")
                
                val testMessages = listOf(
                    "Test 1/3: First test message",
                    "Test 2/3: Second test message", 
                    "Test 3/3: Third test message"
                )
                
                for ((index, testMsg) in testMessages.withIndex()) {
                    Log.d(tag, "🧪 Sending test message ${index + 1}/3...")
                    sendSmsMessage(phoneNumber, testMsg)
                    Log.d(tag, "🧪 Test message ${index + 1}/3 sent successfully")
                    
                    if (index < testMessages.size - 1) {
                        delay(3000) // 3 second delay for testing
                    }
                }
                
                Log.d(tag, "🧪 SMS test completed successfully")
            } catch (e: Exception) {
                Log.e(tag, "🧪 SMS test failed", e)
            }
        }
    }

    private fun splitMessageForSms(message: String): List<String> {
        val maxLength = 150 // Leave some margin
        val maxBatches = 3 // Limit to maximum 3 batches as requested

        if (message.length <= maxLength) {
            return listOf(message)
        }

        // Calculate maximum allowed characters for 3 batches
        // Account for the "(X/Y) " prefix which takes about 6 characters
        val maxTotalChars = maxBatches * (maxLength - 6)
        
        val processedMessage = if (message.length > maxTotalChars) {
            // Truncate and add indication
            val truncated = message.substring(0, maxTotalChars - 20) // Leave space for truncation note
            "$truncated... [truncated]"
        } else {
            message
        }

        // First, split the message into chunks WITHOUT numbering
        val rawChunks = mutableListOf<String>()
        var remaining = processedMessage
        var partNumber = 1

        while (remaining.isNotEmpty() && partNumber <= maxBatches) {
            // Reserve space for numbering like "(X/Y) "
            val availableLength = maxLength - 6 // Reserve 6 chars for "(X/Y) "
            
            val chunkSize = if (remaining.length > availableLength) {
                // Find last space before availableLength to avoid cutting words
                val lastSpace = remaining.substring(0, availableLength).lastIndexOf(' ')
                if (lastSpace > 0) lastSpace else availableLength
            } else {
                remaining.length
            }

            val chunk = remaining.substring(0, chunkSize).trim()
            rawChunks.add(chunk)
            remaining = remaining.substring(chunkSize).trim()
            partNumber++
        }

        // Now we know the ACTUAL total parts, add numbering
        val totalParts = rawChunks.size
        
        return rawChunks.mapIndexed { index, chunk ->
            "(${index + 1}/$totalParts) $chunk"
        }
    }
}
