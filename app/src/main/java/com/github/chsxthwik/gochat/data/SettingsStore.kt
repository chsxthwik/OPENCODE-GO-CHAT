package com.github.chsxthwik.gochat.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "gochat_settings")

internal const val DEFAULT_GATEWAY_BASE = "https://opencode.ai/zen/go/v1"

class SettingsStore(private val context: Context) {

    private object K {
        val ENCRYPTED_KEY = stringPreferencesKey("encrypted_api_key")
        val SESSION_ID = stringPreferencesKey("gateway_session_id")
        val MODEL = stringPreferencesKey("model")
        val MODEL_CACHE = stringPreferencesKey("model_cache_json")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val CONTEXT_LIMIT = intPreferencesKey("context_limit")
        val LAST_CHAT = stringPreferencesKey("last_chat_id")
        val GATEWAY_BASE = stringPreferencesKey("gateway_base")
        val NOTIF_ASKED = booleanPreferencesKey("notif_asked")
        val DEFAULT_MODEL = "glm-5.3-flash"
    }

    val hasKey: Flow<Boolean> = context.dataStore.data.map { it[K.ENCRYPTED_KEY] != null }
    val model: Flow<String> = context.dataStore.data.map { it[K.MODEL] ?: K.DEFAULT_MODEL }
    val systemPrompt: Flow<String> = context.dataStore.data.map { it[K.SYSTEM_PROMPT] ?: "" }
    val temperature: Flow<Float> = context.dataStore.data.map { it[K.TEMPERATURE] ?: 0.7f }
    val contextLimit: Flow<Int> = context.dataStore.data.map { it[K.CONTEXT_LIMIT] ?: 20 }
    val lastChatId: Flow<String?> = context.dataStore.data.map { it[K.LAST_CHAT] }
    val modelCacheJson: Flow<String?> = context.dataStore.data.map { it[K.MODEL_CACHE] }
    val gatewayBase: Flow<String> = context.dataStore.data.map { it[K.GATEWAY_BASE] ?: DEFAULT_GATEWAY_BASE }
    val notifAsked: Flow<Boolean> = context.dataStore.data.map { it[K.NOTIF_ASKED] ?: false }

    fun apiKey(): Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[K.ENCRYPTED_KEY]?.let { KeyVault.decrypt(it) }
    }

    suspend fun apiKeyOnce(): String? = apiKey().first()

    suspend fun saveApiKey(plain: String) {
        context.dataStore.edit { it[K.ENCRYPTED_KEY] = KeyVault.encrypt(plain.trim()) }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { it.remove(K.ENCRYPTED_KEY) }
    }

    suspend fun setModel(model: String) {
        context.dataStore.edit { it[K.MODEL] = model }
    }

    suspend fun setModelCache(json: String) {
        context.dataStore.edit { it[K.MODEL_CACHE] = json }
    }

    suspend fun setSystemPrompt(prompt: String) {
        context.dataStore.edit { it[K.SYSTEM_PROMPT] = prompt }
    }

    suspend fun setTemperature(t: Float) {
        context.dataStore.edit { it[K.TEMPERATURE] = t.coerceIn(0f, 2f) }
    }

    suspend fun setContextLimit(n: Int) {
        context.dataStore.edit { it[K.CONTEXT_LIMIT] = n.coerceIn(1, 100) }
    }

    suspend fun setGatewayBase(url: String) {
        context.dataStore.edit { it[K.GATEWAY_BASE] = url }
    }

    suspend fun markNotifAsked() {
        context.dataStore.edit { it[K.NOTIF_ASKED] = true }
    }

    suspend fun setLastChat(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(K.LAST_CHAT) else prefs[K.LAST_CHAT] = id
        }
    }

    /** Stable per-install conversation-group id the gateway uses for routing/prompt-cache. */
    suspend fun sessionId(): String {
        val existing = context.dataStore.data.first()[K.SESSION_ID]
        if (existing != null) return existing
        val fresh = UUID.randomUUID().toString()
        context.dataStore.edit { it[K.SESSION_ID] = fresh }
        return fresh
    }
}
