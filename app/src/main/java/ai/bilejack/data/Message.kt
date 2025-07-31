package ai.bilejack.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val incomingSms: String,
    val gptResponse: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isProcessed: Boolean = false,
    // Track messages currently being processed
    val isProcessing: Boolean = false,
    // Track which chunks have been sent (comma-separated indices)
    val sentChunks: String = "",
    val hasError: Boolean = false,
    val errorMessage: String? = null,
)
