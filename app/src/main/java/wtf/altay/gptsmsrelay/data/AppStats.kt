package wtf.altay.gptsmsrelay.data

data class AppStats(
    val smsReceived: Int = 0,
    val smsSent: Int = 0,
    val gptErrors: Int = 0,
    val lastError: String? = null,
    val isGptApiReachable: Boolean = false,
    val isNetworkAvailable: Boolean = false,
)
