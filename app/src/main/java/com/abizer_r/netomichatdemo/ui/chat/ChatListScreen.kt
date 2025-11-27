package com.abizer_r.netomichatdemo.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abizer_r.netomichatdemo.data.socket.ConnectionState

@Composable
fun ChatListScreen(
    modifier: Modifier = Modifier,
    state: ChatUiState,
    onStartNewChat: () -> Unit,
    onConversationClicked: (String) -> Unit,
    onOnlineToggle: (Boolean) -> Unit
) {
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

        Spacer(Modifier.height(8.dp))

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

        Text(
            text = "Chats",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(4.dp))

        if (state.conversations.isEmpty()) {
            Text(
                text = "No chats available",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))

            if (!state.isOnline) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You are offline. The chat will be created and queued messages retried when online.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            items(state.conversations, key = { it.id }) { conv ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onConversationClicked(conv.id) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            conv.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (conv.lastMessagePreview.isNotBlank()) {
                            Text(
                                conv.lastMessagePreview,
                                style = if (conv.unreadCount > 0)
                                    MaterialTheme.typography.bodyMedium
                                else
                                    MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (conv.unreadCount > 0) {
                        Text(
                            text = "${conv.unreadCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { onStartNewChat() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.connectionState is ConnectionState.Connected
                ) {
                    Text("Start New Chat")
                }
            }
        }
    }
}