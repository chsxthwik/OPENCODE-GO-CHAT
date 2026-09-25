package com.github.chsxthwik.gochat.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class ChatEvent {
    data class Delta(val text: String) : ChatEvent()
    data class Done(val tokensIn: Int, val tokensOut: Int) : ChatEvent()
    data class Failure(val error: GoError) : ChatEvent()
}

data class GoError(val kind: Kind, val httpCode: Int = 0, val detail: String = "") {
    enum class Kind { INVALID_KEY, RATE_LIMITED, NO_CONNECTION, QUOTA, SERVER, UNKNOWN }

    fun friendly(): String = when (kind) {
        Kind.INVALID_KEY -> "Invalid API key — check it on opencode.ai/zen"
        Kind.RATE_LIMITED -> "Rate-limited — Go's 5-hour window is throttling; retry soon"
        Kind.NO_CONNECTION -> "No connection — check your network"
        Kind.QUOTA -> "Plan limit reached — weekly/monthly Go quota exhausted"
        Kind.SERVER -> "Gateway error $httpCode — try again"
        Kind.UNKNOWN -> detail.ifBlank { "Something went wrong" }
    }
}

class GoErrorException(val error: GoError) : Exception(error.friendly())
fun GoError.toException() = GoErrorException(this)

data class WireMessage(
    val role: String,
    val text: String,
    val images: List<String> = emptyList(), // base64 data URLs
)

/** Normalize a user-entered gateway URL: https implied, trailing slashes stripped. */
fun normalizeGatewayBase(raw: String): String? {
    var u = raw.trim()
    if (u.isEmpty()) return null
    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
    u = u.trimEnd('/')
    return if (u.toHttpUrlOrNull() != null) u else null
}

class GoApi {
    var base = "https://opencode.ai/zen/go/v1"
    val json = Json { ignoreUnknownKeys = true }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun authed(path: String, apiKey: String, sessionId: String): Request.Builder =
        Request.Builder()
            .url("$base$path")
            .header("Authorization", "Bearer $apiKey")
            .header("x-opencode-session", sessionId)
            .header("User-Agent", "GoChat-Android/0.3")
            .header("Accept", "application/json")

    suspend fun listModels(apiKey: String, sessionId: String): List<GoModel> =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val req = authed("/models", apiKey, sessionId).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw errorFor(resp.code, resp.body?.string().orEmpty()).toException()
                val body = resp.body?.string().orEmpty()
                val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
                    ?: return@withContext emptyList()
                data.map {
                    val id = it.jsonObject["id"]!!.jsonPrimitive.content
                    GoModel(id, GoCatalog.endpointFor(id), GoCatalog.supportsVision(id))
                }
            }
        }

    /** Errors that can never succeed on retry (auth, quota, malformed request). */
    private fun GoError.isPermanent() =
        kind == GoError.Kind.INVALID_KEY || kind == GoError.Kind.QUOTA ||
            (kind == GoError.Kind.UNKNOWN && httpCode in 400..499)

    /** Errors worth one bounded backoff-retry on the same endpoint. */
    private fun GoError.isRetryable() =
        kind == GoError.Kind.NO_CONNECTION || kind == GoError.Kind.SERVER || kind == GoError.Kind.RATE_LIMITED

    /**
     * Streams a reply. Tries the model's primary protocol first, then falls back
     * across the other two Go endpoints on protocol-level failures. Permanent
     * errors (bad key, quota, 4xx) abort immediately — they'd fail identically
     * on every endpoint. Retryable errors get one exponential-backoff retry per
     * endpoint before falling through.
     */
    fun streamChat(
        apiKey: String,
        sessionId: String,
        model: GoModel,
        messages: List<WireMessage>,
        systemPrompt: String,
        temperature: Float,
    ): Flow<ChatEvent> = flow {
        val order = listOf(model.protocol) + ApiProtocol.entries.filter { it != model.protocol }
        var lastError: GoError? = null
        for (proto in order) {
            var retries = 0
            while (true) {
                try {
                    var done: ChatEvent.Done? = null
                    streamOnce(apiKey, sessionId, model.id, proto, messages, systemPrompt, temperature)
                        .collect { ev ->
                            when (ev) {
                                is ChatEvent.Delta -> emit(ev)
                                is ChatEvent.Done -> done = ev
                                is ChatEvent.Failure -> throw ev.error.toException()
                            }
                        }
                    emit(done ?: ChatEvent.Done(0, 0))
                    return@flow
                } catch (e: GoErrorException) {
                    lastError = e.error
                    if (e.error.isPermanent()) {
                        emit(ChatEvent.Failure(e.error)); return@flow
                    }
                    if (e.error.isRetryable() && retries < 1) {
                        retries++
                        delay(if (e.error.kind == GoError.Kind.RATE_LIMITED) 1500 else 500)
                        continue
                    }
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    // raw transport failures (refused, timeout, DNS, TLS) —
                    // retry once like any other transient error
                    lastError = GoError(GoError.Kind.NO_CONNECTION)
                    if (retries < 1) {
                        retries++
                        delay(500)
                        continue
                    }
                    break
                } catch (e: Exception) {
                    lastError = GoError(GoError.Kind.UNKNOWN, detail = e.message.orEmpty().take(120))
                    break
                }
            }
        }
        emit(ChatEvent.Failure(lastError ?: GoError(GoError.Kind.UNKNOWN)))
    }.flowOn(Dispatchers.IO)

    fun errorFor(code: Int, body: String): GoError {
        val detail = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.content
        }.getOrNull()?.take(160).orEmpty()
        val kind = when (code) {
            401, 403 -> if (detail.contains("quota", true) || detail.contains("limit", true))
                GoError.Kind.QUOTA else GoError.Kind.INVALID_KEY
            402 -> GoError.Kind.QUOTA
            429 -> GoError.Kind.RATE_LIMITED
            in 500..599 -> GoError.Kind.SERVER
            else -> GoError.Kind.UNKNOWN
        }
        return GoError(kind, code, detail)
    }
}
