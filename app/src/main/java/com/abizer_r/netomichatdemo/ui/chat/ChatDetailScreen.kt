package com.abizer_r.netomichatdemo.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.abizer_r.netomichatdemo.domain.model.MessageStatus
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    modifier: Modifier = Modifier,
    state: ChatUiState,
    events: SharedFlow<UiEvent>,
    conversationId: String,
    onBackClicked: () -> Unit,
    onSendClicked: (String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }

    // Collect snackbar events
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

    // Ensure the requested conversation is the active one in state
    LaunchedEffect(conversationId) {
        // This will also mark it as read in ViewModel (since onConversationSelected calls markConversationRead)
        // We don't have direct access to VM here, but MainActivity will call onConversationSelected before navigating.
        // We can add a callback here too.
    }

    val activeConv = state.conversations.firstOrNull { it.id == conversationId }
    val title = activeConv?.title ?: "Chat"

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

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

                    if (msg.isBot)  return@items

                    val (statusSuffix, statusColor) = when (msg.status) {
                        MessageStatus.SENDING -> {
                            Pair("(sending…)", Color.LightGray)
                        }
                        MessageStatus.QUEUED -> {
                            Pair("(queued)", Color.Gray)
                        }
                        MessageStatus.FAILED -> {
                            Pair("(failed)", Color.Red)
                        }
                        MessageStatus.SENT -> {
                            Pair("(sent)", Color.Green)
                        }
                    }

                    Text(statusSuffix, style = MaterialTheme.typography.bodySmall, color = statusColor)
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Input row
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
