package com.abizer_r.netomichatdemo.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abizer_r.netomichatdemo.data.socket.ConnectionState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import com.abizer_r.netomichatdemo.domain.model.MessageStatus
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    state: ChatUiState,
    events: SharedFlow<UiEvent>,
    onSendClicked: (String) -> Unit,
    onConversationClicked: (String) -> Unit,
    onOnlineToggle: (Boolean) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Listen for one-off UI events (snackbars)
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->

        var inputText by remember { mutableStateOf("") }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            Text(
                text = "Netomi Chat Demo",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))

            val statusText = when (state.connectionState) {
                is ConnectionState.Connecting -> "Connecting…"
                is ConnectionState.Connected -> "Connected"
                is ConnectionState.Disconnected -> "Disconnected"
                is ConnectionState.Error -> "Error: ${(state.connectionState as ConnectionState.Error).message}"
            }
            Text(
                text = "Status: $statusText",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (state.isOnline) "Online (simulated)" else "Offline (simulated)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = state.isOnline,
                    onCheckedChange = { onOnlineToggle(it) }
                )
            }

            Spacer(Modifier.height(8.dp))

            if (!state.isOnline) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "No internet connection (simulated). Messages will be queued and retried.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (state.conversations.isEmpty()) {
                Text(
                    text = "No chats available",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!state.isOnline) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Connect to the internet and send a message to start a chat.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                ) {
                    items(state.conversations, key = { it.id }) { conv ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConversationClicked(conv.id) }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(conv.title, style = MaterialTheme.typography.titleMedium)
                            if (conv.lastMessagePreview.isNotBlank()) {
                                Text(
                                    conv.lastMessagePreview,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Messages
            Text("Messages:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    val prefix = when {
                        msg.isBot -> "Bot:"
                        msg.isMine -> "Me:"
                        else -> "User:"
                    }
                    Text("$prefix ${msg.text}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))

                    val statusSuffix = when (msg.status) {
                        MessageStatus.SENDING -> "(sending…)"
                        MessageStatus.QUEUED -> "(queued)"
                        MessageStatus.FAILED -> "(failed)"
                        MessageStatus.SENT -> "(sent)"
                    }

                    // TODO: use different color for different status
                    Text(statusSuffix, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Type a message") }
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val trimmed = inputText.trim()
                        if (trimmed.isNotEmpty()) {
                            onSendClicked(trimmed)
                            inputText = ""
                        }
                    }
                ) {
                    Text("Send")
                }
            }
        }
    }


}
