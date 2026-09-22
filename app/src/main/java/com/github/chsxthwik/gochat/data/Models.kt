package com.github.chsxthwik.gochat.data

import kotlinx.serialization.Serializable

enum class ApiProtocol { CHAT_COMPLETIONS, RESPONSES, MESSAGES }

data class GoModel(
    val id: String,
    val protocol: ApiProtocol,
    val vision: Boolean,
)

object GoCatalog {
    private val RESPONSES_MODELS = setOf("gpt-5.6-luna")
    private val MESSAGES_MODELS = setOf<String>()

    fun endpointFor(modelId: String): ApiProtocol = when {
        RESPONSES_MODELS.contains(modelId) -> ApiProtocol.RESPONSES
        MESSAGES_MODELS.contains(modelId) -> ApiProtocol.MESSAGES
        else -> ApiProtocol.CHAT_COMPLETIONS
    }

    fun supportsVision(modelId: String): Boolean =
        modelId.contains("vision") || modelId.contains("omni") || modelId.contains("grok")
}

enum class MessageStatus { DONE, STREAMING, ERROR, INTERRUPTED }

enum class Role(val wire: String) {
    USER("user"), ASSISTANT("assistant"), SYSTEM("system")
}

@Serializable
data class Attachment(
    val name: String,
    val mime: String,
    val text: String? = null,
    val imageBase64: String? = null,
)
