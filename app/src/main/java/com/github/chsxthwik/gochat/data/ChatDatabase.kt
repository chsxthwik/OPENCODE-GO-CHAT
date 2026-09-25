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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey val id: String,
    val title: String,
    val model: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Int = 0,
    val archived: Int = 0,
    val draft: String = "",
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
    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    fun conversations(): Flow<List<Conversation>>

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Int)

    @Query("UPDATE conversations SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Int)

    @Query("UPDATE conversations SET draft = :draft WHERE id = :id")
    suspend fun setDraft(id: String, draft: String)

    @Query("SELECT DISTINCT conversationId FROM messages WHERE content LIKE :pattern")
    suspend fun conversationsMatching(pattern: String): List<String>

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

    @Query("SELECT id FROM agent_tasks WHERE conversationId = :convId AND createdAt >= :fromTs")
    suspend fun agentTaskIdsFrom(convId: String, fromTs: Long): List<String>

    @Query("DELETE FROM agent_tasks WHERE conversationId = :convId AND createdAt >= :fromTs")
    suspend fun deleteAgentTasksFrom(convId: String, fromTs: Long)

    @Query("UPDATE messages SET status = 'INTERRUPTED' WHERE status = 'STREAMING'")
    suspend fun markStreamingInterrupted(): Int

    @Query("DELETE FROM messages WHERE conversationId = :convId")
    suspend fun clearMessages(convId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :convId")
    suspend fun messageCount(convId: String): Int

    @Query("UPDATE conversations SET model = :model WHERE id = :id")
    suspend fun setConversationModel(id: String, model: String)

    @Query("SELECT * FROM agent_tasks WHERE conversationId = :convId ORDER BY createdAt ASC")
    fun agentTasks(convId: String): Flow<List<AgentTask>>

    @Query("SELECT s.* FROM agent_steps s INNER JOIN agent_tasks t ON s.taskId = t.id WHERE t.conversationId = :convId ORDER BY s.taskId, s.seq")
    fun agentStepsForConv(convId: String): Flow<List<AgentStep>>

    @Query("SELECT * FROM agent_tasks WHERE id = :id")
    suspend fun agentTask(id: String): AgentTask?

    @Query("SELECT * FROM agent_tasks WHERE status IN ('RUNNING','AWAITING_APPROVAL')")
    suspend fun liveAgentTasks(): List<AgentTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAgentTask(t: AgentTask)

    @Query("DELETE FROM agent_tasks WHERE conversationId = :convId")
    suspend fun deleteAgentTasks(convId: String)

    @Query("SELECT * FROM agent_steps WHERE taskId = :taskId ORDER BY seq ASC")
    fun agentSteps(taskId: String): Flow<List<AgentStep>>

    @Query("SELECT * FROM agent_steps WHERE taskId = :taskId ORDER BY seq ASC")
    suspend fun agentStepsOnce(taskId: String): List<AgentStep>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAgentStep(s: AgentStep)

    @Query("DELETE FROM agent_steps WHERE taskId = :taskId")
    suspend fun deleteAgentSteps(taskId: String)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE conversations ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE conversations ADD COLUMN draft TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS agent_tasks (
                id TEXT NOT NULL PRIMARY KEY, conversationId TEXT NOT NULL,
                requestMessageId TEXT NOT NULL, assistantMessageId TEXT NOT NULL,
                status TEXT NOT NULL, phase TEXT NOT NULL,
                planSummary TEXT NOT NULL DEFAULT '', planApproved INTEGER NOT NULL DEFAULT 0,
                error TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_tasks_conversationId ON agent_tasks(conversationId)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS agent_steps (
                id TEXT NOT NULL PRIMARY KEY, taskId TEXT NOT NULL, seq INTEGER NOT NULL,
                title TEXT NOT NULL, kind TEXT NOT NULL, status TEXT NOT NULL,
                output TEXT NOT NULL DEFAULT '', error TEXT NOT NULL DEFAULT '',
                startedAt INTEGER NOT NULL DEFAULT 0, finishedAt INTEGER NOT NULL DEFAULT 0)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_agent_steps_taskId ON agent_steps(taskId, seq)")
    }
}

@Database(
    entities = [Conversation::class, MessageEntity::class, AgentTask::class, AgentStep::class],
    version = 2,
    exportSchema = false,
)
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
                ).addMigrations(MIGRATION_1_2).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
