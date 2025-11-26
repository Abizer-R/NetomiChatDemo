package com.abizer_r.netomichatdemo.ui.chat

import com.abizer_r.netomichatdemo.data.socket.ConnectionState
import com.abizer_r.netomichatdemo.domain.model.MessageStatus

data class ChatUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val conversations: List<ChatConversationItemUi> = emptyList(),
    val activeConversationId: String? = null,
    val messages: List<ChatMessageItemUi> = emptyList(),
    val isOnline: Boolean = true,          // simulated online/offline flag
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class ChatConversationItemUi(
    val id: String,
    val title: String,
    val lastMessagePreview: String
)

data class ChatMessageItemUi(
    val id: String,
    val text: String,
    val isMine: Boolean,
    val isBot: Boolean,
    val status: MessageStatus
)