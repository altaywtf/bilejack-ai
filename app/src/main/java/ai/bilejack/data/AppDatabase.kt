package ai.bilejack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Message::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE messages ADD COLUMN isProcessing INTEGER NOT NULL DEFAULT 0")
                }
            }

        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE messages ADD COLUMN sentChunks TEXT NOT NULL DEFAULT ''")
                }
            }

        fun getDatabase(context: Context): AppDatabase {
            return instance
                ?: synchronized(this) {
                    val database =
                        Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "llm_sms_relay_database",
                        )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build()
                    instance = database
                    database
                }
        }
    }
}
