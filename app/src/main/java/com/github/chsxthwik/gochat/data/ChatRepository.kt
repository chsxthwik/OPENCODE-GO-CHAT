package com.github.chsxthwik.gochat.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

data class ConversationSnapshot(
    val conversation: Conversation,
    val messages: List<MessageEntity>,
    val tasks: List<AgentTask>,
    val steps: Map<String, List<AgentStep>>,
)

class ChatRepository(private val db: ChatDatabase) {
    private val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true }

    fun conversations(): Flow<List<Conversation>> = dao.conversations()
    fun messages(convId: String): Flow<List<MessageEntity>> = dao.messages(convId)
    suspend fun messagesOnce(convId: String) = dao.messagesOnce(convId)
    suspend fun messageCount(convId: String) = dao.messageCount(convId)

    /** Newest [limit] messages, oldest-first — display window for long conversations. */
    fun messagesTail(convId: String, limit: Int): Flow<List<MessageEntity>> =
        dao.messagesTail(convId, limit).map { it.reversed() }
    suspend fun conversation(id: String) = dao.conversation(id)

    suspend fun createConversation(model: String): Conversation {
        val now = System.currentTimeMillis()
        val c = Conversation(UUID.randomUUID().toString(), "New chat", model, now, now)
        dao.upsertConversation(c)
        return c
    }

    suspend fun renameConversation(id: String, title: String) = dao.renameConversation(id, title)

    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, if (pinned) 1 else 0)
    suspend fun setArchived(id: String, archived: Boolean) = dao.setArchived(id, if (archived) 1 else 0)
    suspend fun setDraft(id: String, draft: String) = dao.setDraft(id, draft)

    suspend fun setConversationPrompt(id: String, prompt: String) = dao.setConversationPrompt(id, prompt)
    suspend fun conversationsMatching(query: String): List<String> =
        dao.conversationsMatching("%" + query.replace("%", "").replace("_", "") + "%")

    suspend fun deleteConversation(id: String) {
        dao.agentTasks(id).first().forEach { dao.deleteAgentSteps(it.id) }
        dao.deleteAgentTasks(id)
        dao.clearMessages(id)
        dao.deleteConversation(id)
    }

    /** Full snapshot for undoing a deletion — caller holds it, we just read/write. */
    suspend fun snapshotConversation(id: String): ConversationSnapshot? {
        val conv = dao.conversation(id) ?: return null
        val tasks = dao.agentTasks(id).first()
        return ConversationSnapshot(
            conversation = conv,
            messages = dao.messagesOnce(id),
            tasks = tasks,
            steps = tasks.associate { it.id to dao.agentStepsOnce(it.id) },
        )
    }

    suspend fun restoreConversation(s: ConversationSnapshot) {
        dao.upsertConversation(s.conversation)
        s.messages.forEach { dao.upsertMessage(it) }
        s.tasks.forEach { dao.upsertAgentTask(it) }
        s.steps.values.flatten().forEach { dao.upsertAgentStep(it) }
    }

    suspend fun addMessage(
        convId: String, role: Role, content: String, status: MessageStatus,
        model: String = "", attachments: List<Attachment> = emptyList(),
    ): MessageEntity {
        val m = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = convId,
            role = role.wire,
            content = content,
            createdAt = System.currentTimeMillis(),
            status = status.name,
            model = model,
            attachmentsJson = json.encodeToString(attachments),
        )
        dao.upsertMessage(m)
        dao.touchConversation(convId, m.createdAt)
        return m
    }

    suspend fun finishMessage(
        id: String, content: String, status: MessageStatus, tin: Int, tout: Int, latencyMs: Long,
    ) = dao.finishMessage(id, content, status.name, tin, tout, latencyMs)

    suspend fun maybeAutoTitle(convId: String, firstUserText: String) {
        val conv = dao.conversation(convId) ?: return
        if (conv.title != "New chat" || dao.messageCount(convId) > 2) return
        val title = firstUserText.lineSequence().firstOrNull { it.isNotBlank() }
            ?.take(48)?.trim() ?: return
        dao.renameConversation(convId, title)
    }

    /** Deletes messages from a timestamp and the agent runs that produced them. */
    suspend fun deleteFrom(convId: String, fromTs: Long) {
        dao.agentTaskIdsFrom(convId, fromTs).forEach { dao.deleteAgentSteps(it) }
        dao.deleteAgentTasksFrom(convId, fromTs)
        dao.deleteFrom(convId, fromTs)
    }

    suspend fun markStreamingInterrupted() = dao.markStreamingInterrupted()
    suspend fun setConversationModel(id: String, model: String) = dao.setConversationModel(id, model)
    suspend fun deleteMessage(id: String) = dao.deleteMessage(id)

    // ── agent tasks ────────────────────────────────────────────────
    fun agentTasks(convId: String) = dao.agentTasks(convId)
    fun agentStepsForConv(convId: String) = dao.agentStepsForConv(convId)
    fun agentSteps(taskId: String) = dao.agentSteps(taskId)
    suspend fun agentStepsOnce(taskId: String) = dao.agentStepsOnce(taskId)
    suspend fun agentTask(id: String) = dao.agentTask(id)
    suspend fun liveAgentTasks() = dao.liveAgentTasks()
    suspend fun upsertAgentTask(t: AgentTask) = dao.upsertAgentTask(t)
    suspend fun upsertAgentStep(s: AgentStep) = dao.upsertAgentStep(s)

    fun parseAttachments(raw: String): List<Attachment> =
        runCatching { json.decodeFromString<List<Attachment>>(raw) }.getOrDefault(emptyList())

    fun exportMarkdown(conv: Conversation, messages: List<MessageEntity>): String = buildString {
        appendLine("# ${conv.title}")
        appendLine("_GoChat · ${conv.model}_")
        appendLine()
        messages.forEach { m ->
            val who = if (m.role == "user") "**You**" else "**${m.model.ifBlank { conv.model }}**"
            appendLine("$who:")
            appendLine()
            parseAttachments(m.attachmentsJson).forEach { a ->
                a.text?.let { appendLine("```\n// ${a.name}\n$it\n```") }
                if (a.imageBase64 != null) appendLine("_[image: ${a.name}]_")
            }
            if (m.content.isNotBlank()) {
                appendLine(m.content)
                appendLine()
            }
        }
    }
}
