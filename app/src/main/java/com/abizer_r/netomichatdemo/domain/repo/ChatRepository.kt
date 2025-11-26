package com.abizer_r.netomichatdemo.domain.repo

import com.abizer_r.netomichatdemo.data.socket.ConnectionState
import com.abizer_r.netomichatdemo.domain.model.ChatConversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    val conversations: StateFlow<List<ChatConversation>>
    val connectionState: StateFlow<ConnectionState>

    val errorEvents: Flow<String>

    suspend fun start(clientId: String)
    suspend fun sendUserMessage(text: String, clientId: String)

    fun onNetworkStatusChanged(isOnline: Boolean)
}
