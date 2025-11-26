package com.abizer_r.netomichatdemo.domain.model

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val text: String,
    val timestamp: Long,
    val isMine: Boolean,
    val isBot: Boolean,
    val status: MessageStatus = MessageStatus.SENT
)