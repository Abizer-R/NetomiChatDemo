package com.abizer_r.netomichatdemo.domain.model

data class ChatConversation(
    val id: String,
    val title: String,
    val lastMessage: ChatMessage?,
    val messages: List<ChatMessage>,
    val unreadCount: Int = 0          // <- NEW
)
