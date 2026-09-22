package com.github.chsxthwik.gochat.data

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ChatRepository(private val db: ChatDatabase) {
    private val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true }

    fun conversations(): Flow<List<Conversation>> = dao.conversations()
    fun messages(convId: String): Flow<List<MessageEntity>> = dao.messages(convId)
    suspend fun messagesOnce(convId: String) = dao.messagesOnce(convId)
    suspend fun conversation(id: String) = dao.conversation(id)

    suspend fun createConversation(model: String): Conversation {
        val now = System.currentTimeMillis()
        val c = Conversation(UUID.randomUUID().toString(), "New chat", model, now, now)
        dao.upsertConversation(c)
        return c
    }

    suspend fun renameConversation(id: String, title: String) = dao.renameConversation(id, title)

    suspend fun deleteConversation(id: String) {
        dao.clearMessages(id)
        dao.deleteConversation(id)
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

    suspend fun deleteFrom(convId: String, fromTs: Long) = dao.deleteFrom(convId, fromTs)
    suspend fun deleteMessage(id: String) = dao.deleteMessage(id)

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
