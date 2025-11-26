package com.abizer_r.netomichatdemo.ui.chat

import com.abizer_r.netomichatdemo.data.socket.ConnectionState

data class ChatUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val conversations: List<ChatConversationItemUi> = emptyList(),
    val activeConversationId: String? = null,
    val messages: List<ChatMessageItemUi> = emptyList(),
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
    val isBot: Boolean
)