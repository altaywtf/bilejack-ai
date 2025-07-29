package wtf.altay.gptsmsrelay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    private val tag = "BootReceiver"

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(tag, "Device booted, starting SMS Relay Service")

            val serviceIntent = Intent(context, SmsRelayService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
