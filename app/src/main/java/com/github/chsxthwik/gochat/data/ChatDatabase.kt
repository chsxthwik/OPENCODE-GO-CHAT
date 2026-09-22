package com.github.chsxthwik.gochat.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val id: String,
    val title: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    indices = [Index("conversationId", "createdAt")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val status: String = "DONE",
    val tokensIn: Int = 0,
    val tokensOut: Int = 0,
    val latencyMs: Long = 0,
    val model: String = "",
    val attachmentsJson: String = "[]",
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun conversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun conversation(id: String): Conversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(c: Conversation)

    @Query("UPDATE conversations SET title = :title WHERE id = :id")
    suspend fun renameConversation(id: String, title: String)

    @Query("UPDATE conversations SET updatedAt = :ts WHERE id = :id")
    suspend fun touchConversation(id: String, ts: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY createdAt ASC, rowid ASC")
    fun messages(convId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY createdAt ASC, rowid ASC")
    suspend fun messagesOnce(convId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(m: MessageEntity)

    @Query("UPDATE messages SET content = :content, status = :status, tokensIn = :tin, tokensOut = :tout, latencyMs = :lat WHERE id = :id")
    suspend fun finishMessage(id: String, content: String, status: String, tin: Int, tout: Int, lat: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :convId AND createdAt >= :fromTs")
    suspend fun deleteFrom(convId: String, fromTs: Long)

    @Query("DELETE FROM messages WHERE conversationId = :convId")
    suspend fun clearMessages(convId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :convId")
    suspend fun messageCount(convId: String): Int
}

@Database(entities = [Conversation::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun dao(): ChatDao

    companion object {
        @Volatile private var instance: ChatDatabase? = null

        fun get(context: Context): ChatDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "gochat.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
