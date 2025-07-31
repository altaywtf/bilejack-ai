package ai.bilejack

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SmsRelayService : LifecycleService() {
    private val tag = "SmsRelayService"
    private val channelId = "gpt_sms_relay_channel"
    private val notificationId = 1

    private lateinit var gptClient: GptClient
    private var preferredSmsManager: SmsManager? = null
    private var subscriptionId: Int = -1
    private var selectedCarrierName: String = "Unknown"

    // Simple in-memory tracking - NO persistence bullshit
    private val currentlyProcessing = mutableSetOf<String>()

    companion object {
        @Volatile
        private var instance: SmsRelayService? = null

        fun getInstance(): SmsRelayService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        gptClient = GptClient(this)

        // Setup SIM management
        setupSmsManager()

        createNotificationChannel()
        startForeground(notificationId, createNotification())

        Log.d(tag, "🚀 Service created with SIM: $selectedCarrierName (ID: $subscriptionId)")
    }

    // Called by SmsReceiver - SYNCHRONOUS processing
    fun processIncomingSms(
        phoneNumber: String,
        messageBody: String,
    ) {
        val messageKey = "$phoneNumber:${messageBody.hashCode()}"

        Log.d(tag, "📨 Processing SMS: $messageKey")

        // Simple duplicate check - in memory only
        if (currentlyProcessing.contains(messageKey)) {
            Log.w(tag, "🚫 Already processing this message, skipping: $messageKey")
            return
        }

        currentlyProcessing.add(messageKey)

        // Process synchronously on background thread
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(tag, "🤖 Calling GPT for: $messageKey")
                val gptResponse = gptClient.sendMessage(messageBody)

                // Split and send SMS chunks with delays
                val smsChunks = splitMessageForSms(gptResponse)
                Log.d(tag, "📤 Sending ${smsChunks.size} SMS chunks")

                val chunkDelay = resources.getInteger(R.integer.sms_chunk_delay_ms).toLong()

                for ((index, chunk) in smsChunks.withIndex()) {
                    sendSmsMessage(phoneNumber, chunk)
                    Log.d(tag, "✅ Sent chunk ${index + 1}/${smsChunks.size}")

                    // Delay between chunks to prevent rate limiting
                    if (index < smsChunks.size - 1) {
                        delay(chunkDelay)
                    }
                }

                Log.d(tag, "🎉 All chunks sent successfully for: $messageKey")
            } catch (e: Exception) {
                Log.e(tag, "❌ Error processing $messageKey: ${e.message}", e)

                try {
                    sendSmsMessage(phoneNumber, "Error: Unable to process your message.")
                } catch (smsError: Exception) {
                    Log.e(tag, "Failed to send error SMS", smsError)
                }
            } finally {
                // Always clean up
                currentlyProcessing.remove(messageKey)
            }
        }
    }

    private fun setupSmsManager() {
        try {
            val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subscriptions = subscriptionManager.activeSubscriptionInfoList

            Log.d(tag, "📱 Found ${subscriptions?.size ?: 0} active SIM cards")

            if (subscriptions != null && subscriptions.isNotEmpty()) {
                // Log all available SIMs for debugging
                subscriptions.forEachIndexed { index, sub ->
                    Log.d(
                        tag,
                        "📋 SIM $index: Carrier='${sub.carrierName}', " +
                            "Display='${sub.displayName}', ID=${sub.subscriptionId}",
                    )
                }

                // Look for Turkcell SIM with multiple criteria
                val turkcellSim =
                    subscriptions.find { sub ->
                        val carrierName = sub.carrierName?.toString()?.lowercase() ?: ""
                        val displayName = sub.displayName?.toString()?.lowercase() ?: ""

                        carrierName.contains("turkcell") ||
                            displayName.contains("turkcell") ||
                            carrierName.contains("tcell") ||
                            displayName.contains("tcell")
                    }

                if (turkcellSim != null) {
                    Log.d(tag, "✅ Found Turkcell SIM: ${turkcellSim.carrierName}")
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

                    Log.w(tag, "⚠️ Turkcell SIM not found, using: $selectedCarrierName (ID: $subscriptionId)")
                }

                Log.d(tag, "📱 Selected SIM: $selectedCarrierName (ID: $subscriptionId)")
            } else {
                // Fallback to default SMS manager
                preferredSmsManager = SmsManager.getDefault()
                selectedCarrierName = "Default"
                Log.w(tag, "⚠️ No SIM cards found, using default SMS manager")
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Error setting up SMS manager", e)
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
        Log.d(tag, "🔄 Service started")
        return START_STICKY // Restart if killed
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(tag, "🛑 Service destroyed")
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

            if (preferredSmsManager != null) {
                preferredSmsManager!!.sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    // sentIntent - could add for delivery confirmation
                    null,
                    // deliveryIntent
                    null,
                )
                Log.d(tag, "📱 SMS sent via $selectedCarrierName")
            } else {
                SmsManager.getDefault().sendTextMessage(
                    phoneNumber,
                    null,
                    message,
                    null,
                    null,
                )
                Log.d(tag, "📱 SMS sent via default SIM")
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ SMS send failed to $phoneNumber: ${e.message}", e)

            // Log more details about the error
            when (e) {
                is IllegalArgumentException -> Log.e(tag, "📱 Invalid phone number or message format")
                is SecurityException -> Log.e(tag, "📱 Missing SMS permission or SIM card issue")
                else -> Log.e(tag, "📱 Unknown SMS error: ${e.javaClass.simpleName}")
            }

            throw e
        }
    }

    private fun splitMessageForSms(message: String): List<String> {
        val maxLength = resources.getInteger(R.integer.sms_max_length)
        val maxBatches = resources.getInteger(R.integer.sms_max_batches)

        if (message.length <= maxLength) {
            return listOf(message)
        }

        // Calculate prefix length dynamically based on max batches (e.g., "(3/3) " = 6 chars)
        val prefixLength = "($maxBatches/$maxBatches) ".length

        // Calculate maximum allowed characters for max batches
        val maxTotalChars = maxBatches * (maxLength - prefixLength)

        val processedMessage =
            if (message.length > maxTotalChars) {
                // Truncate and add indication (leave 20 chars for truncation note)
                val truncated = message.substring(0, maxTotalChars - 20)
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
            val availableLength = maxLength - prefixLength

            val chunkSize =
                if (remaining.length > availableLength) {
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
