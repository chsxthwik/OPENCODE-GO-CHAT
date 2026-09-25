package com.github.chsxthwik.gochat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.chsxthwik.gochat.data.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(
    val ready: Boolean = false,
    val hasKey: Boolean = false,
    val connecting: Boolean = false,
    val connectError: String? = null,
    val models: List<GoModel> = emptyList(),
    val model: String = "",
    val conversations: List<Conversation> = emptyList(),
    val currentId: String? = null,
    val messages: List<MessageEntity> = emptyList(),
    val streamingText: String = "",
    val streamingId: String? = null,
    val thinking: Boolean = false,
    val sending: Boolean = false,
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val contextLimit: Int = 20,
    val agentMode: Boolean = false,
    val agentTasks: List<AgentTask> = emptyList(),
    val agentSteps: Map<String, List<AgentStep>> = emptyMap(),
    val offline: Boolean = false,
    val contextTokens: Int = 0,
    val contextDropped: Int = 0,
    val draft: String = "",
    val earlierCount: Int = 0,
    val pendingChatSearch: String? = null,
    val gatewayBase: String = "",
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val settings: SettingsStore,
    private val api: GoApi,
    private val repo: ChatRepository,
    private val connectivity: Connectivity,
    private val engine: AgentEngine,
) : ViewModel() {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var apiKey: String? = null
    private var sessionId: String = ""
    private var sendJob: Job? = null
    private var messagesJob: Job? = null
    private val historyWindow = MutableStateFlow(HISTORY_PAGE)
    private var agentJob: Job? = null
    private val approvals = mutableMapOf<String, CompletableDeferred<Boolean>>()

    init {
        sessionId = ""
        viewModelScope.launch {
            sessionId = settings.sessionId()
            apiKey = settings.apiKeyOnce()
            val has = apiKey != null
            _ui.update { it.copy(hasKey = has) }
            settings.systemPrompt.collect { p -> _ui.update { s -> s.copy(systemPrompt = p) } }
        }
        viewModelScope.launch { settings.temperature.collect { t -> _ui.update { s -> s.copy(temperature = t) } } }
        viewModelScope.launch { settings.contextLimit.collect { n -> _ui.update { s -> s.copy(contextLimit = n) } } }
        viewModelScope.launch {
            settings.gatewayBase.collect { b ->
                api.base = b
                _ui.update { s -> s.copy(gatewayBase = b) }
            }
        }
        viewModelScope.launch { settings.model.collect { m -> _ui.update { s -> s.copy(model = m) } } }
        viewModelScope.launch {
            repo.conversations().collect { list ->
                _ui.update { s -> s.copy(conversations = list, ready = true) }
            }
        }
        viewModelScope.launch {
            settings.lastChatId.collect { id ->
                if (id != null && _ui.value.currentId == null) openChat(id)
            }
        }
        viewModelScope.launch {
            connectivity.online.collect { on -> _ui.update { s -> s.copy(offline = !on) } }
        }
        viewModelScope.launch {
            // runs left RUNNING by process death become resumable
            repo.liveAgentTasks().forEach { t ->
                repo.upsertAgentTask(t.copy(status = AgentTaskStatus.INTERRUPTED.name, updatedAt = System.currentTimeMillis()))
            }
            // messages stuck mid-stream across a restart render as interrupted, not empty
            repo.markStreamingInterrupted()
        }
    }

    fun connect(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        _ui.update { it.copy(connecting = true, connectError = null) }
        viewModelScope.launch {
            try {
                val models = api.listModels(trimmed, sessionId)
                settings.saveApiKey(trimmed)
                apiKey = trimmed
                _ui.update {
                    it.copy(
                        hasKey = true, connecting = false, models = models,
                        model = if (it.model.isBlank()) models.firstOrNull()?.id ?: "" else it.model,
                    )
                }
                settings.setModelCache(models.joinToString(",") { m -> m.id })
            } catch (e: GoErrorException) {
                _ui.update { it.copy(connecting = false, connectError = e.error.friendly()) }
            } catch (e: Exception) {
                _ui.update { it.copy(connecting = false, connectError = "Couldn't reach gateway — check connection") }
            }
        }
    }

    fun refreshModels() {
        val key = apiKey ?: return
        viewModelScope.launch {
            try {
                val models = api.listModels(key, sessionId)
                _ui.update { it.copy(models = models) }
            } catch (_: Exception) { /* keep cached list */ }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            settings.clearApiKey()
            apiKey = null
            _ui.update { it.copy(hasKey = false, models = emptyList()) }
        }
    }

    fun selectModel(id: String) {
        _ui.update { it.copy(model = id) }
        viewModelScope.launch {
            settings.setModel(id)
            // keep the open conversation's model in sync — it drives the drawer subtitle
            _ui.value.currentId?.let { repo.setConversationModel(it, id) }
        }
    }

    fun setSystemPrompt(p: String) = viewModelScope.launch { settings.setSystemPrompt(p) }

    /** Save a custom gateway base; returns false when the URL can't be normalized. */
    fun setGatewayBase(raw: String): Boolean {
        val url = normalizeGatewayBase(raw) ?: return false
        viewModelScope.launch { settings.setGatewayBase(url) }
        return true
    }

    fun resetGatewayBase() = viewModelScope.launch { settings.setGatewayBase(DEFAULT_GATEWAY_BASE) }
    fun setTemperature(t: Float) = viewModelScope.launch { settings.setTemperature(t) }
    fun setContextLimit(n: Int) = viewModelScope.launch { settings.setContextLimit(n) }

    fun newChat() {
        viewModelScope.launch {
            val c = repo.createConversation(_ui.value.model)
            openChat(c.id)
        }
    }

    fun openChat(id: String, searchQuery: String? = null) {
        messagesJob?.cancel()
        historyWindow.value = HISTORY_PAGE
        _ui.update { it.copy(currentId = id, messages = emptyList(), streamingText = "", streamingId = null, agentTasks = emptyList(), agentSteps = emptyMap(), draft = "", earlierCount = 0, pendingChatSearch = searchQuery) }
        viewModelScope.launch { settings.setLastChat(id) }
        messagesJob = viewModelScope.launch {
            launch {
                historyWindow.flatMapLatest { repo.messagesTail(id, it) }.collect { msgs ->
                    val total = repo.messageCount(id)
                    val dropped = (total - _ui.value.contextLimit).coerceAtLeast(0)
                    val window = if (dropped > 0) msgs.takeLast(_ui.value.contextLimit) else msgs
                    _ui.update { s ->
                        s.copy(
                            messages = msgs,
                            earlierCount = (total - msgs.size).coerceAtLeast(0),
                            contextDropped = dropped,
                            contextTokens = ContextBudget.estimateTokens(window, ::parseAttachments),
                        )
                    }
                }
            }
            launch {
                combine(repo.agentTasks(id), repo.agentStepsForConv(id)) { tasks, steps ->
                    tasks to steps.groupBy { it.taskId }
                }.collect { (tasks, steps) ->
                    _ui.update { s -> s.copy(agentTasks = tasks, agentSteps = steps) }
                }
            }
            repo.conversation(id)?.let { c -> _ui.update { s -> s.copy(draft = c.draft) } }
        }
    }

    fun closeChat() {
        messagesJob?.cancel()
        historyWindow.value = HISTORY_PAGE
        _ui.update { it.copy(currentId = null, messages = emptyList(), agentTasks = emptyList(), agentSteps = emptyMap(), earlierCount = 0) }
        viewModelScope.launch { settings.setLastChat(null) }
    }

    /** Pull the next page of older messages into the display window. */
    fun loadEarlier() {
        if (_ui.value.earlierCount > 0) historyWindow.value += HISTORY_PAGE
    }

    fun loadAllHistory() {
        if (_ui.value.earlierCount > 0) historyWindow.value = Int.MAX_VALUE
    }

    fun saveDraft(text: String) {
        val id = _ui.value.currentId ?: return
        if (text == _ui.value.draft) return
        _ui.update { it.copy(draft = text) }
        viewModelScope.launch { repo.setDraft(id, text) }
    }

    fun setPinned(id: String, pinned: Boolean) = viewModelScope.launch { repo.setPinned(id, pinned) }
    fun setArchived(id: String, archived: Boolean) = viewModelScope.launch { repo.setArchived(id, archived) }
    suspend fun searchConversations(query: String): List<String> = repo.conversationsMatching(query)
    fun setAgentMode(on: Boolean) = _ui.update { it.copy(agentMode = on) }

    fun consumePendingSearch() = _ui.update { it.copy(pendingChatSearch = null) }

    /** Per-chat instructions win over the app-wide system prompt when set. */
    private fun effectiveSystemPrompt(): String {
        val conv = _ui.value.conversations.firstOrNull { it.id == _ui.value.currentId }
        return conv?.systemPrompt?.takeIf { it.isNotBlank() } ?: _ui.value.systemPrompt
    }

    fun setChatSystemPrompt(prompt: String) {
        val id = _ui.value.currentId ?: return
        viewModelScope.launch { repo.setConversationPrompt(id, prompt.trim()) }
    }

    private var lastDeleted: ConversationSnapshot? = null

    fun deleteChat(id: String) {
        viewModelScope.launch {
            lastDeleted = repo.snapshotConversation(id)
            repo.deleteConversation(id)
            if (_ui.value.currentId == id) closeChat()
        }
    }

    fun undoDelete() {
        val s = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch { repo.restoreConversation(s) }
    }

    fun forkFrom(messageId: String) {
        val convId = _ui.value.currentId ?: return
        viewModelScope.launch {
            repo.forkConversation(convId, messageId)?.let { openChat(it) }
        }
    }

    fun renameChat(id: String, title: String) =
        viewModelScope.launch { repo.renameConversation(id, title.trim().ifBlank { "New chat" }) }

    fun send(text: String, attachments: List<Attachment> = emptyList()) {
        val convId = _ui.value.currentId ?: return
        val key = apiKey ?: return
        val content = text.trim()
        if (content.isEmpty() && attachments.isEmpty()) return
        if (_ui.value.sending) return

        if (_ui.value.agentMode) {
            sendAgent(convId, key, content, attachments)
            return
        }
        sendJob = viewModelScope.launch {
            val model = _ui.value.model
            repo.addMessage(convId, Role.USER, content, MessageStatus.DONE, attachments = attachments)
            repo.maybeAutoTitle(convId, content)
            val pending = repo.addMessage(convId, Role.ASSISTANT, "", MessageStatus.STREAMING, model = model)
            _ui.update { it.copy(sending = true, thinking = true, streamingId = pending.id, streamingText = "") }
            streamInto(convId, pending.id, model, key)
        }
    }

    // ── agent runs ─────────────────────────────────────────────────

    private fun sendAgent(convId: String, key: String, content: String, attachments: List<Attachment>) {
        agentJob = viewModelScope.launch {
            val model = _ui.value.model
            val userMsg = repo.addMessage(convId, Role.USER, content, MessageStatus.DONE, attachments = attachments)
            repo.maybeAutoTitle(convId, content)
            val pending = repo.addMessage(convId, Role.ASSISTANT, "", MessageStatus.STREAMING, model = model)
            val task = AgentTask(
                id = java.util.UUID.randomUUID().toString(),
                conversationId = convId,
                requestMessageId = userMsg.id,
                assistantMessageId = pending.id,
                status = AgentTaskStatus.RUNNING.name,
                phase = AgentPhase.UNDERSTAND.name,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            repo.upsertAgentTask(task)
            _ui.update { it.copy(sending = true, thinking = true, streamingId = pending.id, streamingText = "") }
            runAgent(task, pending.id, key)
        }
    }

    private suspend fun runAgent(task: AgentTask, assistantId: String, key: String) {
        val convId = task.conversationId
        val reqMsg = repo.messagesOnce(convId).find { it.id == task.requestMessageId } ?: return
        val history = repo.messagesOnce(convId)
            .filter { it.id != reqMsg.id && it.id != assistantId }
            .filter { it.status == MessageStatus.DONE.name }
            .takeLast(_ui.value.contextLimit)
        val model = _ui.value.models.find { it.id == _ui.value.model }
            ?: GoModel(_ui.value.model, GoCatalog.endpointFor(_ui.value.model), GoCatalog.supportsVision(_ui.value.model))
        val buf = StringBuilder()
        try {
            engine.execute(
                AgentEngine.Request(
                    convId = convId,
                    requestMessageId = reqMsg.id,
                    assistantMessageId = assistantId,
                    requestText = reqMsg.content,
                    attachments = parseAttachments(reqMsg.attachmentsJson),
                    history = history,
                    model = model,
                    apiKey = key,
                    sessionId = sessionId,
                    systemPrompt = effectiveSystemPrompt(),
                    temperature = _ui.value.temperature,
                ),
                taskId = task.id,
                awaitApproval = {
                    val gate = CompletableDeferred<Boolean>()
                    approvals[task.id] = gate
                    gate.await()
                },
                onReportDelta = { d ->
                    buf.append(d)
                    _ui.update { it.copy(thinking = false, streamingText = buf.toString()) }
                },
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            repo.finishMessage(assistantId, buf.toString(), MessageStatus.INTERRUPTED, 0, 0, 0)
            throw e
        } finally {
            approvals.remove(task.id)
            val finalTask = repo.agentTask(task.id)
            if (finalTask?.status == AgentTaskStatus.FAILED.name) {
                repo.finishMessage(assistantId, buf.toString().ifBlank { finalTask.error }, MessageStatus.ERROR, 0, 0, 0)
            } else if (finalTask?.status == AgentTaskStatus.CANCELLED.name) {
                repo.deleteMessage(assistantId)
            }
            _ui.update { it.copy(sending = false, thinking = false, streamingId = null, streamingText = "") }
        }
    }

    fun approvePlan(taskId: String) {
        approvals[taskId]?.complete(true)
    }

    fun rejectPlan(taskId: String) {
        approvals[taskId]?.complete(false)
    }

    fun resumeAgent(taskId: String) {
        if (_ui.value.sending) return
        val key = apiKey ?: return
        agentJob = viewModelScope.launch {
            val task = repo.agentTask(taskId) ?: return@launch
            _ui.update { it.copy(sending = true, thinking = true, streamingId = task.assistantMessageId, streamingText = "") }
            runAgent(task, task.assistantMessageId, key)
        }
    }

    fun stopAgent() {
        agentJob?.cancel()
        agentJob = null
    }

    fun regenerate(assistantMsgId: String, modelId: String? = null) {
        val convId = _ui.value.currentId ?: return
        if (_ui.value.sending) return
        sendJob = viewModelScope.launch {
            val msgs = repo.messagesOnce(convId)
            val target = msgs.find { it.id == assistantMsgId } ?: return@launch
            val useModel = modelId ?: target.model.ifBlank { _ui.value.model }
            repo.deleteMessage(assistantMsgId)
            val pending = repo.addMessage(convId, Role.ASSISTANT, "", MessageStatus.STREAMING, model = useModel)
            _ui.update { it.copy(sending = true, thinking = true, streamingId = pending.id, streamingText = "") }
            streamInto(convId, pending.id, useModel, apiKey ?: return@launch)
        }
    }

    fun editAndResend(userMsgId: String, newText: String) {
        val convId = _ui.value.currentId ?: return
        if (_ui.value.sending || newText.isBlank()) return
        sendJob = viewModelScope.launch {
            val msgs = repo.messagesOnce(convId)
            val target = msgs.find { it.id == userMsgId } ?: return@launch
            repo.deleteFrom(convId, target.createdAt)
            send(newText)
        }
    }

    fun deleteFromHere(msgId: String) {
        val convId = _ui.value.currentId ?: return
        viewModelScope.launch {
            val msgs = repo.messagesOnce(convId)
            msgs.find { it.id == msgId }?.let { repo.deleteFrom(convId, it.createdAt) }
        }
    }

    fun stop() {
        if (agentJob?.isActive == true) stopAgent() else {
            sendJob?.cancel()
            sendJob = null
        }
    }

    private suspend fun streamInto(convId: String, msgId: String, modelId: String, key: String) {
        val started = System.currentTimeMillis()
        val history = repo.messagesOnce(convId)
            .filter { it.id != msgId }
            .filter { it.status != MessageStatus.ERROR.name }
            .takeLast(_ui.value.contextLimit)

        val goModel = _ui.value.models.find { it.id == modelId }
            ?: GoModel(modelId, GoCatalog.endpointFor(modelId), GoCatalog.supportsVision(modelId))

        val wire = history.mapNotNull { m ->
            when (m.role) {
                Role.USER.wire -> WireMessage(
                    "user", m.content,
                    if (goModel.vision) parseAttachments(m.attachmentsJson).mapNotNull { it.imageBase64 } else emptyList(),
                )
                Role.ASSISTANT.wire -> if (m.content.isNotBlank()) WireMessage("assistant", m.content) else null
                else -> null
            }
        }

        val buf = StringBuilder()
        var lastEmit = 0L
        try {
            api.streamChat(key, sessionId, goModel, wire, effectiveSystemPrompt(), _ui.value.temperature)
                .collect { ev ->
                    when (ev) {
                        is ChatEvent.Delta -> {
                            buf.append(ev.text)
                            // throttle recomposition to ~16/s — token bursts arrive far faster
                            val now = System.currentTimeMillis()
                            if (now - lastEmit >= EMIT_MS) {
                                lastEmit = now
                                _ui.update { it.copy(thinking = false, streamingText = buf.toString()) }
                            }
                        }
                        is ChatEvent.Done -> {
                            _ui.update { it.copy(streamingText = buf.toString()) }
                            repo.finishMessage(
                                msgId, buf.toString(), MessageStatus.DONE,
                                ev.tokensIn, ev.tokensOut,
                                System.currentTimeMillis() - started,
                            )
                        }
                        is ChatEvent.Failure -> {
                            val status = if (buf.isNotEmpty()) MessageStatus.INTERRUPTED else MessageStatus.ERROR
                            repo.finishMessage(msgId, buf.toString(), status, 0, 0, System.currentTimeMillis() - started)
                            _ui.update { it.copy(streamingText = "") }
                        }
                    }
                }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // user pressed stop — preserve whatever arrived as INTERRUPTED
            val status = if (buf.isNotEmpty()) MessageStatus.INTERRUPTED else MessageStatus.ERROR
            repo.finishMessage(msgId, buf.toString(), status, 0, 0, System.currentTimeMillis() - started)
            throw e
        } finally {
            _ui.update { it.copy(sending = false, thinking = false, streamingId = null, streamingText = "") }
        }
    }

    fun exportChat(convId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val conv = repo.conversation(convId) ?: return@launch
            onReady(exportMarkdown(conv, repo.messagesOnce(convId)))
        }
    }

    companion object {
        private const val HISTORY_PAGE = 60
        private const val EMIT_MS = 60L

        fun factory(app: GoChatApp) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(app.settings, app.api, app.repo, app.connectivity, app.agentEngine) as T
        }
    }
}
