package com.github.chsxthwik.gochat.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.chsxthwik.gochat.ChatViewModel
import com.github.chsxthwik.gochat.UiState
import com.github.chsxthwik.gochat.data.*
import com.github.chsxthwik.gochat.ui.theme.GoColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SUGGESTIONS = listOf(
    "Explain this codebase structure",
    "Write a small CLI tool in Go",
    "Debug: why is my list reversed?",
    "Draft a clean README section",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    ui: UiState,
    vm: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf(listOf<Attachment>()) }
    var modelSheet by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<MessageEntity?>(null) }
    var editing by remember { mutableStateOf<MessageEntity?>(null) }
    var editText by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    val conv = ui.conversations.find { it.id == ui.currentId }
    val streaming = ui.sending
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val snack = remember { SnackbarHostState() }
    fun toast(msg: String) = scope.launch { snack.showSnackbar(msg) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                var sample = 1
                while (opts.outWidth / sample > 1400 || opts.outHeight / sample > 1400) sample *= 2
                val bmp = BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample },
                ) ?: return@runCatching
                val out = ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
                val b64 = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "image.jpg"
                attachments = attachments + Attachment(name, "image/jpeg", imageBase64 = b64)
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "file.txt"
            val text = ctx.contentResolver.openInputStream(uri)?.use { input ->
                String(input.readBytes().take(64_000).toByteArray(), Charsets.UTF_8)
            } ?: return@runCatching
            attachments = attachments + Attachment(name, "text/plain", text = text)
        }
    }

    LaunchedEffect(ui.messages.size, ui.streamingText.length) {
        if (ui.messages.isNotEmpty() && listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 >= ui.messages.size - 2)
            listState.animateScrollToItem(ui.messages.size - 1)
    }

    Box(Modifier.fillMaxSize().background(GoColors.Bg)) {
    Column(Modifier.fillMaxSize().imePadding()) {
        // ── top bar ──────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, "chats", tint = GoColors.TextDim) }
            Column(Modifier.weight(1f).clickable { modelSheet = true }) {
                Text(
                    conv?.title ?: "GoChat",
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ui.model.ifBlank { "pick model" }, style = MaterialTheme.typography.labelSmall, color = GoColors.Accent)
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = GoColors.TextFaint, modifier = Modifier.size(14.dp))
                }
            }
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "more", tint = GoColors.TextDim) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Export chat") },
                    leadingIcon = { Icon(Icons.Default.Share, null) },
                    onClick = {
                        menuOpen = false
                        ui.currentId?.let { id ->
                            vm.exportChat(id) { md ->
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, md)
                                }
                                ctx.startActivity(Intent.createChooser(send, "Share chat"))
                            }
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                    onClick = { menuOpen = false; onOpenSettings() },
                )
            }
        }

        val topScrolled by remember {
            derivedStateOf {
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8
            }
        }
        AnimatedVisibility(visible = topScrolled) {
            HorizontalDivider(color = GoColors.GlassBorder)
        }

        // ── messages ─────────────────────────────────────────────
        Box(Modifier.weight(1f)) {
            if (ui.messages.isEmpty() && !streaming) {
                EmptyState { s -> input = s }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(ui.messages, key = { it.id }) { m ->
                        Box(Modifier.animateItem()) {
                            MessageRow(
                                m = m,
                                isStreaming = m.id == ui.streamingId,
                                streamText = if (m.id == ui.streamingId) ui.streamingText else "",
                                onActions = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); actionsFor = m },
                                onRetry = { vm.regenerate(m.id) },
                                onCopied = { toast("Copied") },
                            )
                        }
                    }
                    if (ui.thinking) item { ThinkingDots() }
                }
            }

            val showJump by remember {
                derivedStateOf {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    ui.messages.size - 1 - last > 2
                }
            }
            if (showJump) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(ui.messages.size - 1) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(36.dp),
                    containerColor = GoColors.Surface2,
                    contentColor = GoColors.Accent,
                ) { Icon(Icons.Default.KeyboardArrowDown, "latest") }
            }
        }

        // ── attachments preview ──────────────────────────────────
        if (attachments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEachIndexed { i, a ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = GoColors.Surface2,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoColors.GlassBorder),
                    ) {
                        Row(Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (a.imageBase64 != null) Icons.Default.Image else Icons.Default.Description,
                                null, tint = GoColors.Accent, modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = GoColors.TextDim, modifier = Modifier.widthIn(max = 140.dp))
                            IconButton(onClick = { attachments = attachments.toMutableList().also { it.removeAt(i) } }, modifier = Modifier.minimumInteractiveComponentSize().size(24.dp)) {
                                Icon(Icons.Default.Close, "remove", tint = GoColors.TextFaint, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── composer ─────────────────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            shape = RoundedCornerShape(18.dp),
            color = GoColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GoColors.GlassBorder),
        ) {
            Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = { filePicker.launch(arrayOf("text/*", "application/json", "application/xml", "*/*")) }) {
                    Icon(Icons.Default.AttachFile, "attach file", tint = GoColors.TextDim, modifier = Modifier.size(20.dp))
                }
                val modelVision = ui.models.find { it.id == ui.model }?.vision ?: GoCatalog.supportsVision(ui.model)
                if (modelVision) {
                    IconButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(Icons.Default.Image, "attach image", tint = GoColors.TextDim, modifier = Modifier.size(20.dp))
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message ${ui.model}", color = GoColors.TextFaint, fontSize = 14.5.sp) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                )
                Spacer(Modifier.width(4.dp))
                if (streaming) {
                    FilledIconButton(
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); vm.stop() },
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = GoColors.Surface2),
                    ) { Icon(Icons.Default.Stop, "stop", tint = GoColors.Error, modifier = Modifier.size(18.dp)) }
                } else {
                    FilledIconButton(
                        onClick = {
                            if (input.isNotBlank() || attachments.isNotEmpty()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.send(input, attachments)
                                input = ""; attachments = emptyList(); keyboard?.hide()
                            }
                        },
                        enabled = input.isNotBlank() || attachments.isNotEmpty(),
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = GoColors.Accent,
                            disabledContainerColor = GoColors.Surface2,
                        ),
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward, "send",
                            tint = if (input.isNotBlank() || attachments.isNotEmpty()) GoColors.Bg else GoColors.TextFaint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }

    SnackbarHost(
        hostState = snack,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .imePadding()
            .padding(bottom = 76.dp, start = 16.dp, end = 16.dp),
    ) { data ->
        Snackbar(
            data,
            shape = RoundedCornerShape(12.dp),
            containerColor = GoColors.Surface2,
            contentColor = GoColors.Text,
            actionColor = GoColors.Accent,
        )
    }
    }

    // ── model picker ─────────────────────────────────────────────
    if (modelSheet) {
        ModalBottomSheet(onDismissRequest = { modelSheet = false }, containerColor = GoColors.Surface) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Models", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                var q by remember(modelSheet) { mutableStateOf("") }
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search ${ui.models.size} models", color = GoColors.TextFaint, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = GoColors.TextFaint, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoColors.Accent.copy(alpha = 0.5f),
                        unfocusedBorderColor = GoColors.GlassBorder,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                val groups = ui.models
                    .filter { q.isBlank() || it.id.contains(q.trim(), ignoreCase = true) }
                    .groupBy { it.id.substringBefore('-') }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    groups.forEach { (family, models) ->
                        item {
                            Text(
                                family.uppercase(), style = MaterialTheme.typography.labelSmall,
                                color = GoColors.TextFaint,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                        items(models) { m ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        vm.selectModel(m.id); modelSheet = false
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(m.id, fontFamily = FontFamily.Monospace, fontSize = 13.5.sp, color = GoColors.Text, modifier = Modifier.weight(1f))
                                if (m.vision) Text("vision", style = MaterialTheme.typography.labelSmall, color = GoColors.TextFaint)
                                if (m.id == ui.model) Icon(Icons.Default.Check, null, tint = GoColors.Accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── message actions sheet ────────────────────────────────────
    if (actionsFor != null) {
        val m = actionsFor!!
        val clipboard = LocalClipboardManager.current
        ModalBottomSheet(onDismissRequest = { actionsFor = null }, containerColor = GoColors.Surface) {
            Column(Modifier.padding(bottom = 28.dp)) {
                ActionItem(Icons.Default.ContentCopy, "Copy text") {
                    clipboard.setText(AnnotatedString(m.content)); actionsFor = null; toast("Copied")
                }
                ActionItem(Icons.Default.Share, "Share") {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, m.content)
                    }
                    ctx.startActivity(Intent.createChooser(send, "Share")); actionsFor = null
                }
                if (m.role == "user") {
                    ActionItem(Icons.Default.Edit, "Edit & resend") {
                        editing = m; editText = m.content; actionsFor = null
                    }
                }
                if (m.role == "assistant") {
                    ActionItem(Icons.Default.Refresh, "Regenerate") {
                        vm.regenerate(m.id); actionsFor = null
                    }
                }
                ActionItem(Icons.Default.Delete, "Delete from here", GoColors.Error) {
                    vm.deleteFromHere(m.id); actionsFor = null
                }
            }
        }
    }

    if (editing != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            confirmButton = {
                TextButton(onClick = { vm.editAndResend(editing!!.id, editText); editing = null }) {
                    Text("Resend", color = GoColors.Accent)
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
        )
    }
}

@Composable
private fun ActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = GoColors.Text, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 14.5.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    m: MessageEntity,
    isStreaming: Boolean,
    streamText: String,
    onActions: () -> Unit,
    onRetry: () -> Unit,
    onCopied: () -> Unit,
) {
    val isUser = m.role == "user"
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth()) {
        if (isUser) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Column(
                    Modifier
                        .widthIn(max = 330.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = 16.dp, bottomEnd = 4.dp,
                            )
                        )
                        .background(GoColors.UserBubble)
                        .border(1.dp, GoColors.GlassBorder, RoundedCornerShape(16.dp))
                        .combinedClickable(onClick = {}, onLongClick = onActions)
                        .semantics { contentDescription = "Your message" }
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                ) {
                    val atts = remember(m.attachmentsJson) {
                        runCatching {
                            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                                .decodeFromString<List<Attachment>>(m.attachmentsJson)
                        }.getOrDefault(emptyList())
                    }
                    atts.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                            Icon(
                                if (a.imageBase64 != null) Icons.Default.Image else Icons.Default.Description,
                                null, tint = GoColors.Accent, modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(a.name, fontSize = 12.sp, color = GoColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (m.content.isNotBlank()) {
                        Text(m.content, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } else {
            // assistant: flat full-width reply, no card chrome
            Column(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = onActions)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = "Assistant reply"
                    }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                when (m.status) {
                    "ERROR" -> {
                        Text(
                            if (m.content.isBlank()) "Couldn't get a reply." else m.content,
                            color = GoColors.Error, style = MaterialTheme.typography.bodyMedium,
                        )
                        RetryChip("failed — tap to retry", onRetry)
                    }
                    else -> {
                        val text = if (isStreaming) streamText else m.content
                        if (text.isBlank() && isStreaming) ThinkingDots()
                        else MarkdownText(text, onCopied = { onCopied() })
                        if (m.status == "INTERRUPTED") RetryChip("stopped — tap to retry", onRetry)
                    }
                }
                if (!isStreaming && m.status != "ERROR") {
                    Row(
                        Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MetaAction(Icons.Default.ContentCopy, "Copy reply") {
                            clipboard.setText(AnnotatedString(m.content)); onCopied()
                        }
                        MetaAction(Icons.Default.Refresh, "Regenerate", onRetry)
                        MetaAction(Icons.Default.MoreHoriz, "More actions", onActions)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            buildString {
                                append(formatStamp(m.createdAt))
                                if (m.tokensOut > 0 || m.latencyMs > 0)
                                    append("  ·  ${m.tokensOut} tok · ${"%.1f".format(m.latencyMs / 1000f)}s")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = GoColors.TextFaint,
                        )
                    }
                }
            }
        }
        if (isUser) {
            Row(
                Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    formatStamp(m.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = GoColors.TextFaint,
                )
            }
        }
    }
}

@Composable
private fun RetryChip(label: String, onRetry: () -> Unit) {
    Row(
        Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(GoColors.Surface2)
            .clickable(onClick = onRetry)
            .semantics { role = Role.Button }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Refresh, null, tint = GoColors.Accent, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = GoColors.Accent, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MetaAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.minimumInteractiveComponentSize().size(30.dp),
    ) {
        Icon(icon, label, tint = GoColors.TextFaint, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun ThinkingDots() {
    val infinite = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val alpha by infinite.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 180, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "d$i",
            )
            Box(Modifier.size(6.dp).alpha(alpha).background(GoColors.Accent, CircleShape))
        }
    }
}

@Composable
private fun EmptyState(onSuggest: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(">_", color = GoColors.Accent, fontFamily = FontFamily.Monospace, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Ask anything.", style = MaterialTheme.typography.titleMedium)
        Text("Real answers, streamed.", style = MaterialTheme.typography.bodyMedium, color = GoColors.TextDim)
        Spacer(Modifier.height(24.dp))
        SUGGESTIONS.forEach { s ->
            Surface(
                onClick = { onSuggest(s) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = GoColors.Surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, GoColors.GlassBorder),
            ) {
                Text(
                    s, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = GoColors.TextDim,
                )
            }
        }
    }
}

private fun formatStamp(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
