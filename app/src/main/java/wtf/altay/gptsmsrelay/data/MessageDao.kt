package wtf.altay.gptsmsrelay.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.reactivex.rxjava3.core.Flowable

@Dao
interface MessageDao {
    // Reactive queries for real-time updates
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessagesObservable(): Flowable<List<Message>>

    @Query("SELECT COUNT(*) FROM messages")
    fun getMessageCountObservable(): Flowable<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE isProcessed = 1")
    fun getProcessedCountObservable(): Flowable<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE hasError = 1")
    fun getErrorCountObservable(): Flowable<Int>

    // Reactive stream for unprocessed messages that aren't being processed
    @Query("SELECT * FROM messages WHERE isProcessed = 0 AND isProcessing = 0 ORDER BY timestamp ASC")
    fun getUnprocessedMessagesObservable(): Flowable<List<Message>>

    // Suspend functions for service operations
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    suspend fun getAllMessages(): List<Message>

    @Query("SELECT * FROM messages WHERE isProcessed = 0 AND isProcessing = 0 ORDER BY timestamp ASC LIMIT 1")
    suspend fun getNextUnprocessedMessage(): Message?

    @Insert suspend fun insertMessage(message: Message): Long

    @Update suspend fun updateMessage(message: Message)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getMessageCount(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE isProcessed = 1")
    suspend fun getProcessedCount(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE hasError = 1")
    suspend fun getErrorCount(): Int

    // Reset processing states (in case service crashed)
    @Query("UPDATE messages SET isProcessing = 0 WHERE isProcessing = 1 AND isProcessed = 0")
    suspend fun resetStuckProcessingMessages()
}
