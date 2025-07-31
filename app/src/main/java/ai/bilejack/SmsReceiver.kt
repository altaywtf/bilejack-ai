package ai.bilejack

import ai.bilejack.data.Message
import ai.bilejack.data.MessageRepository
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val tag = "SmsReceiver"

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") {
            return
        }

        try {
            val bundle = intent.extras ?: return
            val pdus = bundle.get("pdus") as Array<*>? ?: return

            for (pdu in pdus) {
                val smsMessage = SmsMessage.createFromPdu(pdu as ByteArray)
                val phoneNumber = smsMessage.originatingAddress ?: continue
                val messageBody = smsMessage.messageBody ?: continue

                Log.d(tag, "📨 Received SMS from $phoneNumber") // Don't log content here to avoid duplication

                // Check if phone number is in whitelist
                val whitelistManager = WhitelistManager(context)
                if (!whitelistManager.isPhoneNumberAllowed(phoneNumber)) {
                    Log.w(tag, "🚫 Phone number $phoneNumber not in whitelist, ignoring SMS")
                    continue
                }

                // Save message to database
                val messageRepository = MessageRepository(context)
                val message =
                    Message(
                        phoneNumber = phoneNumber,
                        incomingSms = messageBody,
                        isProcessed = false,
                        isProcessing = false,
                    )

                // Start service if not running
                val serviceIntent = Intent(context, SmsRelayService::class.java)
                context.startForegroundService(serviceIntent)

                // Save message to database and process it
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Save to database first
                        val messageId = messageRepository.insertMessage(message)
                        Log.d(tag, "💾 Message saved to database with ID: $messageId")

                        // Then process with service
                        var retries = 0
                        val maxRetries = context.resources.getInteger(R.integer.service_max_retries)
                        val retryDelay = context.resources.getInteger(R.integer.service_retry_delay_ms).toLong()

                        while (retries < maxRetries) {
                            val serviceInstance = SmsRelayService.getInstance()

                            if (serviceInstance != null) {
                                serviceInstance.processIncomingSms(phoneNumber, messageBody, messageId)
                                Log.d(tag, "✅ SMS processed successfully")
                                break
                            } else {
                                retries++
                                Log.w(tag, "⏳ Service not ready, retry $retries/$maxRetries")
                                delay(retryDelay)
                            }
                        }

                        if (retries >= maxRetries) {
                            Log.e(tag, "❌ Failed to process SMS - service unavailable after $maxRetries retries")
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "❌ Error saving message to database", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error processing SMS", e)
        }
    }
}
