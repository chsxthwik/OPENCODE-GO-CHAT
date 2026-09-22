package com.github.chsxthwik.gochat.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal fun GoApi.pathFor(p: ApiProtocol) = when (p) {
    ApiProtocol.CHAT_COMPLETIONS -> "/chat/completions"
    ApiProtocol.RESPONSES -> "/responses"
    ApiProtocol.MESSAGES -> "/messages"
}

internal fun GoApi.streamOnce(
    apiKey: String,
    sessionId: String,
    modelId: String,
    proto: ApiProtocol,
    messages: List<WireMessage>,
    systemPrompt: String,
    temperature: Float,
): Flow<ChatEvent> = flow {
    val body = requestBody(proto, modelId, messages, systemPrompt, temperature)
        .toString()
        .toRequestBody("application/json".toMediaType())
    val req = Request.Builder()
        .url("$base${pathFor(proto)}")
        .header("Authorization", "Bearer $apiKey")
        .header("x-opencode-session", sessionId)
        .header("User-Agent", "GoChat-Android/0.3")
        .header("Accept", "text/event-stream")
        .post(body)
        .build()

    client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) {
            emit(ChatEvent.Failure(errorFor(resp.code, resp.body?.string().orEmpty())))
            return@flow
        }
        val source = resp.body?.source() ?: run {
            emit(ChatEvent.Failure(GoError(GoError.Kind.UNKNOWN, detail = "empty body")))
            return@flow
        }
        var tin = 0
        var tout = 0
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break
            val el = runCatching { json.parseToJsonElement(payload) }.getOrNull() ?: continue
            when (proto) {
                ApiProtocol.CHAT_COMPLETIONS -> {
                    el.jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.content
                        ?.let { emit(ChatEvent.Delta(it)) }
                    el.jsonObject["usage"]?.jsonObject?.let { u ->
                        tin = u["prompt_tokens"]?.jsonPrimitive?.int ?: tin
                        tout = u["completion_tokens"]?.jsonPrimitive?.int ?: tout
                    }
                }
                ApiProtocol.RESPONSES -> {
                    when (el.jsonObject["type"]?.jsonPrimitive?.content) {
                        "response.output_text.delta" ->
                            el.jsonObject["delta"]?.jsonPrimitive?.content?.let { emit(ChatEvent.Delta(it)) }
                        "response.completed" ->
                            el.jsonObject["response"]?.jsonObject?.get("usage")?.jsonObject?.let { u ->
                                tin = u["input_tokens"]?.jsonPrimitive?.int ?: tin
                                tout = u["output_tokens"]?.jsonPrimitive?.int ?: tout
                            }
                    }
                }
                ApiProtocol.MESSAGES -> {
                    when (el.jsonObject["type"]?.jsonPrimitive?.content) {
                        "content_block_delta" ->
                            el.jsonObject["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content?.let { emit(ChatEvent.Delta(it)) }
                        "message_start" ->
                            el.jsonObject["message"]?.jsonObject?.get("usage")?.jsonObject?.let { u ->
                                tin = u["input_tokens"]?.jsonPrimitive?.int ?: tin
                            }
                        "message_delta" ->
                            el.jsonObject["usage"]?.jsonObject?.let { u ->
                                tout = u["output_tokens"]?.jsonPrimitive?.int ?: tout
                            }
                    }
                }
            }
        }
        emit(ChatEvent.Done(tin, tout))
    }
}
