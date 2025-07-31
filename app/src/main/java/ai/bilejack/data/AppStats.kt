package ai.bilejack.data

data class AppStats(
    val smsReceived: Int = 0,
    val smsSent: Int = 0,
    val llmErrors: Int = 0,
    val lastError: String? = null,
    val isLlmApiReachable: Boolean = false,
    val isNetworkAvailable: Boolean = false,
)
