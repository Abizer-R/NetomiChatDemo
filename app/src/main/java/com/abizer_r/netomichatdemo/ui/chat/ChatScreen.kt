package com.abizer_r.netomichatdemo.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    state: ChatUiState,
    onSendClicked: (String) -> Unit,
    onConversationClicked: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
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

        if (state.conversations.isEmpty()) {
            Text(
                text = "No chats available",
                style = MaterialTheme.typography.bodyMedium
            )
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

        // Messages for active conversation
        Text("Messages:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
        ) {
            items(state.messages, key = { it.id }) { msg ->
                val prefix = when {
                    msg.isBot -> "Bot:"
                    msg.isMine -> "Me:"
                    else -> "User:"
                }
                Text("$prefix ${msg.text}")
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
