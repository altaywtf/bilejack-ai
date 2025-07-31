package wtf.altay.gptsmsrelay

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
                if (!isPhoneNumberAllowed(context, phoneNumber)) {
                    Log.w(tag, "🚫 Phone number $phoneNumber not in whitelist, ignoring SMS")
                    continue
                }

                // Start service if not running
                val serviceIntent = Intent(context, SmsRelayService::class.java)
                context.startForegroundService(serviceIntent)

                // Process message with retry logic for service availability
                CoroutineScope(Dispatchers.IO).launch {
                    var retries = 0
                    val maxRetries = context.resources.getInteger(R.integer.service_max_retries)
                    val retryDelay = context.resources.getInteger(R.integer.service_retry_delay_ms).toLong()
                    
                    while (retries < maxRetries) {
                        val serviceInstance = SmsRelayService.getInstance()
                        
                        if (serviceInstance != null) {
                            serviceInstance.processIncomingSms(phoneNumber, messageBody)
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
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error processing SMS", e)
        }
    }

    private fun isPhoneNumberAllowed(
        context: Context,
        phoneNumber: String,
    ): Boolean {
        try {
            val allowedNumbers = context.getString(R.string.allowed_phone_numbers)
            if (allowedNumbers.isBlank() || allowedNumbers == "your_phone_numbers_here") {
                Log.w(tag, "🚫 No phone numbers configured in whitelist")
                return false
            }

            val phoneList = allowedNumbers.split(",").map { it.trim() }

            // Check if the incoming number matches any in the whitelist
            for (allowedNumber in phoneList) {
                if (phoneNumber.contains(allowedNumber) || allowedNumber.contains(phoneNumber)) {
                    Log.d(tag, "✅ Phone number $phoneNumber matches whitelist entry: $allowedNumber")
                    return true
                }
            }

            Log.w(tag, "🚫 Phone number $phoneNumber not found in whitelist: $phoneList")
            return false
        } catch (e: Exception) {
            Log.e(tag, "Error checking phone number whitelist", e)
            return false
        }
    }
}
