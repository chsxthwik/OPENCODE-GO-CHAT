package com.github.chsxthwik.gochat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.chsxthwik.gochat.UiState
import com.github.chsxthwik.gochat.ui.theme.GoColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    ui: UiState,
    onSystemPrompt: (String) -> Unit,
    onTemperature: (Float) -> Unit,
    onContextLimit: (Int) -> Unit,
    onRefreshModels: () -> Unit,
    onDisconnect: () -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = GoColors.Surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 10.dp))

            Text("System prompt", style = MaterialTheme.typography.labelMedium, color = GoColors.TextDim)
            Spacer(Modifier.height(6.dp))
            var prompt by remember(ui.systemPrompt) { mutableStateOf(ui.systemPrompt) }
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it; onSystemPrompt(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. You are a concise senior engineer.", color = GoColors.TextFaint) },
                minLines = 2, maxLines = 4,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Temperature", style = MaterialTheme.typography.labelMedium, color = GoColors.TextDim, modifier = Modifier.weight(1f))
                Text("%.2f".format(ui.temperature), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = GoColors.Accent)
            }
            Slider(
                value = ui.temperature,
                onValueChange = { onTemperature((it * 20).roundToInt() / 20f) },
                valueRange = 0f..2f,
                colors = SliderDefaults.colors(thumbColor = GoColors.Accent, activeTrackColor = GoColors.Accent),
            )
            Text("lower = sharper, higher = more creative", style = MaterialTheme.typography.labelSmall, color = GoColors.TextFaint)

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Context messages", style = MaterialTheme.typography.labelMedium, color = GoColors.TextDim, modifier = Modifier.weight(1f))
                Text("${ui.contextLimit}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = GoColors.Accent)
            }
            Slider(
                value = ui.contextLimit.toFloat(),
                onValueChange = { onContextLimit(it.roundToInt()) },
                valueRange = 4f..60f,
                steps = 27,
                colors = SliderDefaults.colors(thumbColor = GoColors.Accent, activeTrackColor = GoColors.Accent),
            )
            Text("fewer = cheaper requests, more = longer memory", style = MaterialTheme.typography.labelSmall, color = GoColors.TextFaint)

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${ui.models.size} models", style = MaterialTheme.typography.labelMedium, color = GoColors.TextDim)
                    Text("from opencode.ai/zen/go", style = MaterialTheme.typography.labelSmall, color = GoColors.TextFaint)
                }
                TextButton(onClick = onRefreshModels) { Text("Refresh", color = GoColors.Accent) }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = GoColors.GlassBorder)
            Spacer(Modifier.height(8.dp))
            var confirmDisconnect by remember { mutableStateOf(false) }
            TextButton(onClick = { confirmDisconnect = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Logout, null, tint = GoColors.Error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Disconnect key", color = GoColors.Error)
            }
            if (confirmDisconnect) {
                AlertDialog(
                    onDismissRequest = { confirmDisconnect = false },
                    title = { Text("Disconnect key?") },
                    text = { Text("Your API key is removed from this device. Chats stay saved.", color = GoColors.TextDim) },
                    confirmButton = {
                        TextButton(onClick = { confirmDisconnect = false; onDisconnect() }) {
                            Text("Disconnect", color = GoColors.Error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } },
                )
            }
        }
    }
}
