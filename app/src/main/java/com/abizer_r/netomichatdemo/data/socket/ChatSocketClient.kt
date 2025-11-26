package com.abizer_r.netomichatdemo.data.socket

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ChatSocketClient {
    val events: Flow<ChatPayload>
    val connectionState: StateFlow<ConnectionState>

    suspend fun connect()
    fun disconnect()
    suspend fun send(payload: ChatPayload)
}
