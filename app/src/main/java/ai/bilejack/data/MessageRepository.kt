package ai.bilejack.data

import android.content.Context
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reactive repository for managing message data with real-time updates
 */
class MessageRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val messageDao = database.messageDao()

    /**
     * Get real-time stream of all messages
     */
    fun getAllMessagesStream(): Flowable<List<Message>> {
        return messageDao.getAllMessagesObservable()
            .subscribeOn(Schedulers.io())
    }

    /**
     * Get real-time stream of message statistics
     */
    fun getStatsStream(): Flowable<MessageStats> {
        return Flowable.combineLatest(
            messageDao.getMessageCountObservable(),
            messageDao.getProcessedCountObservable(),
            messageDao.getErrorCountObservable(),
        ) { totalCount: Int, processedCount: Int, errorCount: Int ->
            MessageStats(
                totalMessages = totalCount,
                processedMessages = processedCount,
                errorMessages = errorCount,
                // Will show via messages stream instead
                lastError = null,
            )
        }.subscribeOn(Schedulers.io())
    }

    /**
     * Get real-time stream of unprocessed messages ready for processing
     */
    fun getUnprocessedMessagesStream(): Flowable<List<Message>> {
        return messageDao.getUnprocessedMessagesObservable()
            .subscribeOn(Schedulers.io())
    }

    /**
     * Reset any messages stuck in processing state (e.g., after service crash)
     */
    suspend fun resetStuckProcessingMessages() {
        withContext(Dispatchers.IO) {
            messageDao.resetStuckProcessingMessages()
        }
    }

    /**
     * Insert a new message (from SMS receiver)
     */
    suspend fun insertMessage(message: Message): Long {
        return withContext(Dispatchers.IO) {
            messageDao.insertMessage(message)
        }
    }

    /**
     * Update message status (from service)
     */
    suspend fun updateMessage(message: Message) {
        withContext(Dispatchers.IO) {
            messageDao.updateMessage(message)
        }
    }

    /**
     * Get next unprocessed message for service
     */
    suspend fun getNextUnprocessedMessage(): Message? {
        return withContext(Dispatchers.IO) {
            messageDao.getNextUnprocessedMessage()
        }
    }

    /**
     * Clear all messages
     */
    suspend fun clearAllMessages() {
        withContext(Dispatchers.IO) {
            messageDao.clearAllMessages()
        }
    }

    /**
     * Data class for reactive stats
     */
    data class MessageStats(
        val totalMessages: Int,
        val processedMessages: Int,
        val errorMessages: Int,
        val lastError: String?,
    )
}
