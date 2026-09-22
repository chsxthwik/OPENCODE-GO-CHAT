package com.github.chsxthwik.gochat.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal fun GoApi.requestBody(
    proto: ApiProtocol,
    modelId: String,
    messages: List<WireMessage>,
    systemPrompt: String,
    temperature: Float,
): JsonObject = when (proto) {
    ApiProtocol.CHAT_COMPLETIONS -> buildJsonObject {
        put("model", modelId)
        put("stream", true)
        put("temperature", temperature)
        putJsonObject("stream_options") { put("include_usage", true) }
        putJsonArray("messages") {
            if (systemPrompt.isNotBlank()) addJsonObject {
                put("role", "system"); put("content", systemPrompt)
            }
            messages.forEach { m ->
                addJsonObject {
                    put("role", m.role)
                    if (m.images.isEmpty()) {
                        put("content", m.text)
                    } else {
                        putJsonArray("content") {
                            addJsonObject { put("type", "text"); put("text", m.text) }
                            m.images.forEach { img ->
                                addJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") { put("url", img) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    ApiProtocol.RESPONSES -> buildJsonObject {
        put("model", modelId)
        put("stream", true)
        put("temperature", temperature)
        if (systemPrompt.isNotBlank()) put("instructions", systemPrompt)
        putJsonArray("input") {
            messages.forEach { m ->
                addJsonObject {
                    put("role", m.role)
                    putJsonArray("content") {
                        val textType = if (m.role == "assistant") "output_text" else "input_text"
                        addJsonObject { put("type", textType); put("text", m.text) }
                        m.images.forEach { img ->
                            addJsonObject {
                                put("type", "input_image")
                                put("image_url", img)
                            }
                        }
                    }
                }
            }
        }
    }

    ApiProtocol.MESSAGES -> buildJsonObject {
        put("model", modelId)
        put("stream", true)
        put("temperature", temperature)
        put("max_tokens", 8192)
        if (systemPrompt.isNotBlank()) put("system", systemPrompt)
        putJsonArray("messages") {
            messages.filter { it.role != "system" }.forEach { m ->
                addJsonObject {
                    put("role", if (m.role == "assistant") "assistant" else "user")
                    putJsonArray("content") {
                        addJsonObject { put("type", "text"); put("text", m.text) }
                        m.images.forEach { img ->
                            val media = img.substringAfter("data:", "").substringBefore(";")
                            val data = img.substringAfter("base64,", "")
                            addJsonObject {
                                put("type", "image")
                                putJsonObject("source") {
                                    put("type", "base64")
                                    put("media_type", media.ifBlank { "image/png" })
                                    put("data", data)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
