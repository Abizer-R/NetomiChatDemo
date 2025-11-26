package com.abizer_r.netomichatdemo.data.socket

import android.util.Log
import com.piesocket.channels.Channel
import com.piesocket.channels.PieSocket
import com.piesocket.channels.misc.PieSocketEvent
import com.piesocket.channels.misc.PieSocketEventListener
import com.piesocket.channels.misc.PieSocketOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LOG_TAG = "PieSocketClient"

class PieSocketChatSocketClient(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope
) : ChatSocketClient {

    private val _events = MutableSharedFlow<ChatPayload>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState = _connectionState.asStateFlow()

    private var channel: Channel? = null
    private var pieSocket: PieSocket? = null

    override suspend fun connect() = withContext(ioDispatcher) {
        if (channel != null) {
            // already connected or connecting
            return@withContext
        }

        _connectionState.value = ConnectionState.Connecting

        val options = PieSocketOptions().apply {
            // TODO: hide clusterId and apiKey
            clusterId = "s15479.blr1"
            apiKey = "EKktzzwLK0gnXos0qO3Fg9LtfUuWrcQ4XAWKr5jO"
            enableLogs = true
        }

        try {
            val socket = PieSocket(options)
            pieSocket = socket

            val joinedChannel = socket.join(CHANNEL_ID)
            channel = joinedChannel

            // System connection event
            joinedChannel.listen("system:connected", object : PieSocketEventListener() {
                override fun handleEvent(event: PieSocketEvent) {
                    Log.d(LOG_TAG, "system:connected: $event")
                    scope.launch {
                        _connectionState.value = ConnectionState.Connected
                    }
                }
            })

            // Our main chat event
            joinedChannel.listen(EVENT_NAME, object : PieSocketEventListener() {
                override fun handleEvent(event: PieSocketEvent) {
                    val raw = event.data
                    Log.d(LOG_TAG, "EVENT $EVENT_NAME: $raw")

                    val payload = ChatPayload.fromJsonOrNull(raw)
                    if (payload != null) {
                        scope.launch {
                            _events.emit(payload)
                        }
                    } else {
                        Log.e(LOG_TAG, "Failed to parse payload: $raw")
                    }
                }
            })

        } catch (t: Throwable) {
            Log.e(LOG_TAG, "Error connecting to PieSocket", t)
            _connectionState.value = ConnectionState.Error(t.message)
        }
    }

    override fun disconnect() {
        try {
            pieSocket?.leave(CHANNEL_ID)
        } catch (t: Throwable) {
            Log.e(LOG_TAG, "Error leaving channel", t)
        } finally {
            pieSocket = null
            channel = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    override suspend fun send(payload: ChatPayload) = withContext(ioDispatcher) {
        val ch = channel ?: run {
            Log.w(LOG_TAG, "send: channel is null")
            return@withContext
        }

        val event = PieSocketEvent(EVENT_NAME)
        event.setData(payload.toJson())

        try {
            ch.publish(event)
            Log.d(LOG_TAG, "Published ${payload.type}: ${payload.text}")
        } catch (t: Throwable) {
            Log.e(LOG_TAG, "Failed to publish payload", t)
            throw t
        }
    }
}
