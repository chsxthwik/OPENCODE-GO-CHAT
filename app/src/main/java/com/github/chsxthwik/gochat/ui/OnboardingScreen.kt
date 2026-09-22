package com.github.chsxthwik.gochat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.chsxthwik.gochat.UiState
import com.github.chsxthwik.gochat.ui.theme.GoColors

@Composable
fun OnboardingScreen(ui: UiState, onConnect: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(GoColors.Bg).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // GoChat mark
        Box(
            Modifier
                .size(72.dp)
                .background(GoColors.Surface, RoundedCornerShape(20.dp))
                .border(1.dp, GoColors.GlassBorder, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(">_", color = GoColors.Accent, fontFamily = FontFamily.Monospace, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Text("GoChat", style = MaterialTheme.typography.titleLarge, fontSize = 26.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Chat with every OpenCode Go model.\nOne key, forty models, on-device only.",
            style = MaterialTheme.typography.bodyMedium,
            color = GoColors.TextDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Paste your Go API key", color = GoColors.TextFaint, fontFamily = FontFamily.Monospace) },
            leadingIcon = { Icon(Icons.Default.Key, null, tint = GoColors.TextDim) },
            trailingIcon = {
                IconButton(onClick = { reveal = !reveal }) {
                    Icon(
                        if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null, tint = GoColors.TextDim,
                    )
                }
            },
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoColors.Accent,
                unfocusedBorderColor = GoColors.GlassBorder,
                cursorColor = GoColors.Accent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConnect(key) }),
        )

        ui.connectError?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = GoColors.Error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onConnect(key) },
            enabled = key.isNotBlank() && !ui.connecting,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoColors.Accent, contentColor = GoColors.Bg),
        ) {
            if (ui.connecting) CircularProgressIndicator(Modifier.size(20.dp), color = GoColors.Bg, strokeWidth = 2.dp)
            else Text("Connect", fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "opencode.ai/zen → subscribe to Go → copy key",
            style = MaterialTheme.typography.labelSmall,
            color = GoColors.TextFaint,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "key stays encrypted on this device",
            style = MaterialTheme.typography.labelSmall,
            color = GoColors.TextFaint,
        )
    }
}
