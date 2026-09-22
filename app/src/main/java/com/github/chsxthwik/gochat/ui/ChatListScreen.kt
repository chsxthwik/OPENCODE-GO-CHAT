package com.github.chsxthwik.gochat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.chsxthwik.gochat.data.Conversation
import com.github.chsxthwik.gochat.ui.theme.GoColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    conversations: List<Conversation>,
    currentId: String?,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<Conversation?>(null) }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }

    val shown = remember(conversations, query) {
        if (query.isBlank()) conversations
        else conversations.filter { it.title.contains(query, true) }
    }

    Column(Modifier.fillMaxSize().background(GoColors.Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(">_", color = GoColors.Accent, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("GoChat", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "settings", tint = GoColors.TextDim) }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search chats", color = GoColors.TextFaint, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = GoColors.TextFaint, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoColors.Accent.copy(alpha = 0.5f),
                unfocusedBorderColor = GoColors.GlassBorder,
            ),
        )

        Surface(
            onClick = onNew,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = GoColors.Surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GoColors.GlassBorder),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, null, tint = GoColors.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("New chat", color = GoColors.Accent, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { conv ->
                val active = conv.id == currentId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) GoColors.Surface2 else GoColors.Bg)
                        .combinedClickable(
                            onClick = { onOpen(conv.id) },
                            onLongClick = { menuFor = conv },
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            conv.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (active) GoColors.Text else GoColors.TextDim,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            fontSize = 14.5.sp,
                        )
                        Text(
                            formatTime(conv.updatedAt) + " · " + conv.model,
                            style = MaterialTheme.typography.labelSmall,
                            color = GoColors.TextFaint,
                        )
                    }
                }
            }
        }
    }

    if (menuFor != null) {
        AlertDialog(onDismissRequest = { menuFor = null }) {
            Column {
                TextButton(onClick = { renaming = menuFor; renameText = menuFor!!.title; menuFor = null }) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("Rename")
                }
                TextButton(onClick = { onDelete(menuFor!!.id); menuFor = null }) {
                    Icon(Icons.Default.Delete, null, tint = GoColors.Error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp)); Text("Delete", color = GoColors.Error)
                }
            }
        }
    }

    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { renaming = null },
            confirmButton = {
                TextButton(onClick = { onRename(renaming!!.id, renameText); renaming = null }) { Text("Save", color = GoColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Chat name") },
                )
            },
        )
    }
}

private fun formatTime(ts: Long): String {
    val now = System.currentTimeMillis()
    val fmt = if (now - ts < 24 * 3600_000) SimpleDateFormat("HH:mm", Locale.getDefault())
    else SimpleDateFormat("MMM d", Locale.getDefault())
    return fmt.format(Date(ts))
}
