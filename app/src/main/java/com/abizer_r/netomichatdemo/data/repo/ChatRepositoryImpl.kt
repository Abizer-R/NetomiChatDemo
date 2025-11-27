package com.abizer_r.netomichatdemo.data.repo

import androidx.lifecycle.viewModelScope
import com.abizer_r.netomichatdemo.data.socket.BOT_ID
import com.abizer_r.netomichatdemo.data.socket.CHANNEL_ID
import com.abizer_r.netomichatdemo.data.socket.ChatPayload
import com.abizer_r.netomichatdemo.data.socket.ChatSocketClient
import com.abizer_r.netomichatdemo.data.socket.ConnectionState
import com.abizer_r.netomichatdemo.domain.model.ChatConversation
import com.abizer_r.netomichatdemo.domain.model.ChatMessage
import com.abizer_r.netomichatdemo.domain.model.MessageStatus
import com.abizer_r.netomichatdemo.domain.repo.ChatRepository
import com.abizer_r.netomichatdemo.ui.chat.UiEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class ChatRepositoryImpl(
    private val socketClient: ChatSocketClient,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ChatRepository {

    private val _conversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    override val conversations: StateFlow<List<ChatConversation>> = _conversations

    override val connectionState: StateFlow<ConnectionState> = socketClient.connectionState

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val errorEvents = _errorEvents.asSharedFlow()

    // in-memory map for quick updates
    private val conversationMap = LinkedHashMap<String, ChatConversation>()

    private val pendingQueue = mutableListOf<QueuedMessage>()

    private var isOnline: Boolean = true

    override suspend fun start(clientId: String) {
        try {
            socketClient.connect()
        } catch (t: Throwable) {
            _errorEvents.emit("Failed to connect to server: ${t.message ?: "Unknown error"}")
        }

        // Collect incoming events
        scope.launch(ioDispatcher) {
            socketClient.events.collectLatest { payload ->
                handleIncomingPayload(payload = payload, clientId = clientId)
            }
        }

        scope.launch(ioDispatcher) {
            socketClient.errorEvents.collectLatest { payload ->
                _errorEvents.emit("$payload")
            }
        }

        // Observe connection state for errors (optional, but nice)
        scope.launch(ioDispatcher) {
            connectionState.collectLatest { state ->
                if (state is ConnectionState.Error) {
                    _errorEvents.emit("Connection error: ${state.message ?: "Unknown"}")
                }
            }
        }
    }

    override suspend fun sendUserMessage(
        text: String,
        conversationId: String,
        clientId: String
    ) {
        if (text.isBlank()) return

        val now = System.currentTimeMillis()

        // 1) user message payload
        val userPayload = ChatPayload(
            type = "user_message",
            conversationId = conversationId,
            text = text,
            senderId = clientId,
            timestamp = now
        )

        val messageId = generateMessageId(userPayload)
        val initialStatus = if (isOnline) MessageStatus.SENDING else MessageStatus.QUEUED

        // Add local message immediately (optimistic UI)
        val localMessage = ChatMessage(
            id = messageId,
            conversationId = userPayload.conversationId,
            text = userPayload.text,
            timestamp = userPayload.timestamp,
            isMine = true,
            isBot = false,
            status = initialStatus
        )
        upsertMessageLocal(localMessage)

        if (!isOnline) {
            // queue and exit; will be retried when we go online
            pendingQueue.add(QueuedMessage(userPayload, messageId))
            _errorEvents.tryEmit("You are offline. Message queued.")
            return
        }

        // If online, attempt to send now
        try {
            socketClient.send(userPayload)
            // Do NOT change status here, let server echo update to SENT
            // Generate bot reply only once we think the user message has gone out
            val botPayload = buildBotReply(userPayload)
            socketClient.send(botPayload)
        } catch (t: Throwable) {
            // sending failed; mark as queued
            t.printStackTrace()
            pendingQueue.add(QueuedMessage(userPayload, messageId))
            updateMessageStatus(userPayload.conversationId, messageId, MessageStatus.QUEUED)
            _errorEvents.emit("Failed to send. Message queued for retry.")
        }
    }

    override fun onNetworkStatusChanged(isOnline: Boolean) {
        val wentOnline = !this.isOnline && isOnline
        this.isOnline = isOnline

        if (!isOnline) {
            // Just went offline
            scope.launch {
                _errorEvents.emit("No internet connection. Messages will be queued.")
            }
        }

        if (wentOnline) {
            // When we go from offline -> online, retry pending messages
            scope.launch(ioDispatcher) {
                _errorEvents.emit("Back online. Retrying queued messages...")
                retryPending()
            }
        }
    }

    override suspend fun markConversationRead(conversationId: String) {
        val existing = conversationMap[conversationId] ?: return
        if (existing.unreadCount == 0) return

        conversationMap[conversationId] = existing.copy(unreadCount = 0)
        _conversations.value = conversationMap.values.toList()
    }



    private suspend fun retryPending() {
        // Iterate over a copy to avoid concurrent modification issues
        val iterator = pendingQueue.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            try {
                socketClient.send(item.payload)
                // Remove from queue on success
                iterator.remove()
                // Let server echo update the message to SENT via handleIncomingPayload

                // Also send bot reply now that the user message actually went out
                val botPayload = buildBotReply(item.payload)
                socketClient.send(botPayload)
            } catch (t: Throwable) {
                // TODO: we can add a backoff here (try 3 times before giving up)
                // Mark as failed; keep in queue for future attempts
                updateMessageStatus(
                    conversationId = item.payload.conversationId,
                    messageId = item.messageId,
                    status = MessageStatus.FAILED
                )
                _errorEvents.emit("Retry failed for a message. Will try again later.")
            }
        }
    }

    override suspend fun createConversation(conversationId: String, ) {
        // If it already exists, don’t recreate
        if (conversationMap.containsKey(conversationId)) return

        val index = conversationMap.size + 1

        val conv = ChatConversation(
            id = conversationId,
            title = "ChatId: $conversationId",
            lastMessage = null,
            messages = emptyList(),
            unreadCount = 0
        )

        conversationMap[conversationId] = conv
        _conversations.value = conversationMap.values.toList()
    }


    private fun handleIncomingPayload(payload: ChatPayload, clientId: String) {
        val isBot = payload.senderId == BOT_ID || payload.type == "bot_message"
        val isMine = payload.senderId == clientId && !isBot

        val messageId = generateMessageId(payload)

        val existingConv = conversationMap[payload.conversationId]
        val existingMessages = existingConv?.messages.orEmpty()
        val existingIndex = existingMessages.indexOfFirst { it.id == messageId }

        val incoming = ChatMessage(
            id = messageId,
            conversationId = payload.conversationId,
            text = payload.text,
            timestamp = payload.timestamp,
            isMine = isMine,
            isBot = isBot,
            status = MessageStatus.SENT
        )

        // unread logic:
        val baseUnread = existingConv?.unreadCount ?: 0
        val newUnread = if (isMine) baseUnread else baseUnread + 1

        val newMessages = if (existingIndex >= 0) {
            existingMessages.toMutableList().apply {
                this[existingIndex] = incoming
            }
        } else {
            existingMessages + incoming
        }

        val updatedConversation = existingConv?.copy(
            lastMessage = incoming,
            messages = newMessages,
            unreadCount = newUnread
        )
            ?: ChatConversation(
                id = payload.conversationId,
                title = "ChatId: ${payload.conversationId}",
                lastMessage = incoming,
                messages = newMessages,
                unreadCount = if (isMine) 0 else 1
            )

        conversationMap[payload.conversationId] = updatedConversation
        _conversations.value = conversationMap.values.toList()
    }

    private fun upsertMessageLocal(message: ChatMessage) {
        val existingConv = conversationMap[message.conversationId]
        val existingMessages = existingConv?.messages.orEmpty()
        val existingIndex = existingMessages.indexOfFirst { it.id == message.id }

        val newMessages = if (existingIndex >= 0) {
            existingMessages.toMutableList().apply {
                this[existingIndex] = message
            }
        } else {
            existingMessages + message
        }

        // For local messages, we do NOT increment unreadCount
        val unread = existingConv?.unreadCount ?: 0

        val updatedConversation = existingConv?.copy(
            lastMessage = message,
            messages = newMessages,
            unreadCount = unread
        )
            ?: ChatConversation(
                id = message.conversationId,
                title = "ChatId: ${message.conversationId}",
                lastMessage = message,
                messages = newMessages,
                unreadCount = unread
            )

        conversationMap[message.conversationId] = updatedConversation
        _conversations.value = conversationMap.values.toList()
    }

    private fun updateMessageStatus(
        conversationId: String,
        messageId: String,
        status: MessageStatus
    ) {
        val conv = conversationMap[conversationId] ?: return
        val messages = conv.messages
        val idx = messages.indexOfFirst { it.id == messageId }
        if (idx < 0) return

        val updatedMessage = messages[idx].copy(status = status)
        val updatedMessages = messages.toMutableList().apply {
            this[idx] = updatedMessage
        }

        conversationMap[conversationId] = conv.copy(
            lastMessage = updatedMessages.lastOrNull(),
            messages = updatedMessages
        )
        _conversations.value = conversationMap.values.toList()
    }

    private fun generateMessageId(payload: ChatPayload): String {
        return "${payload.senderId}-${payload.timestamp}-${abs(payload.text.hashCode())}"
    }

    private fun buildBotReply(userMessage: ChatPayload): ChatPayload {
        val replyText = when {
            userMessage.text.contains("hello", ignoreCase = true) ->
                "Hello! How can I help you today?"
            userMessage.text.contains("time", ignoreCase = true) ->
                "The current timestamp is ${System.currentTimeMillis()}."
            userMessage.text.contains("help", ignoreCase = true) ->
                "I'm a simple demo bot. Try saying 'hello' or 'time'."
            else ->
                "You said: \"${userMessage.text}\""
        }

        return ChatPayload(
            type = "bot_message",
            conversationId = userMessage.conversationId,
            text = replyText,
            senderId = BOT_ID,
            timestamp = System.currentTimeMillis()
        )
    }

    private data class QueuedMessage(
        val payload: ChatPayload,
        val messageId: String
    )
}
