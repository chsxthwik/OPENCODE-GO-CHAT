package com.github.chsxthwik.gochat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.chsxthwik.gochat.ui.*
import com.github.chsxthwik.gochat.ui.theme.GoChatTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GoChatApp
        setContent {
            GoChatTheme {
                val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(app))
                val ui by vm.ui.collectAsState()
                val drawer = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var settingsOpen by remember { mutableStateOf(false) }

                LaunchedEffect(ui.hasKey, ui.models.isEmpty()) {
                    if (ui.hasKey && ui.models.isEmpty()) vm.refreshModels()
                }

                if (!ui.hasKey) {
                    OnboardingScreen(ui = ui, onConnect = vm::connect)
                } else {
                    ModalNavigationDrawer(
                        drawerState = drawer,
                        drawerContent = {
                            ModalDrawerSheet(
                                modifier = Modifier.width(300.dp).fillMaxHeight(),
                                drawerContainerColor = com.github.chsxthwik.gochat.ui.theme.GoColors.Bg,
                            ) {
                                ChatListScreen(
                                    conversations = ui.conversations,
                                    currentId = ui.currentId,
                                    onOpen = { id -> vm.openChat(id); scope.launch { drawer.close() } },
                                    onNew = { vm.newChat(); scope.launch { drawer.close() } },
                                    onRename = vm::renameChat,
                                    onDelete = vm::deleteChat,
                                    onUndoDelete = vm::undoDelete,
                                    onPin = vm::setPinned,
                                    onArchive = vm::setArchived,
                                    searchMessages = vm::searchConversations,
                                    onSettings = { settingsOpen = true; scope.launch { drawer.close() } },
                                )
                            }
                        },
                    ) {
                        if (ui.currentId == null) {
                            // fresh landing → open a chat immediately for zero-tap start
                            LaunchedEffect(Unit) { vm.newChat() }
                        }
                        ChatScreen(
                            ui = ui,
                            vm = vm,
                            onOpenDrawer = { scope.launch { drawer.open() } },
                            onOpenSettings = { settingsOpen = true },
                        )
                    }
                }

                if (settingsOpen) {
                    SettingsSheet(
                        ui = ui,
                        onSystemPrompt = vm::setSystemPrompt,
                        onTemperature = vm::setTemperature,
                        onContextLimit = vm::setContextLimit,
                        onRefreshModels = vm::refreshModels,
                        onDisconnect = { vm.disconnect(); settingsOpen = false },
                        onClose = { settingsOpen = false },
                    )
                }
            }
        }
    }
}
