package com.abizer_r.netomichatdemo.data.repo

import com.abizer_r.netomichatdemo.data.socket.BOT_ID
import com.abizer_r.netomichatdemo.data.socket.CHANNEL_ID
import com.abizer_r.netomichatdemo.data.socket.ChatPayload
import com.abizer_r.netomichatdemo.data.socket.ChatSocketClient
import com.abizer_r.netomichatdemo.data.socket.ConnectionState
import com.abizer_r.netomichatdemo.domain.model.ChatConversation
import com.abizer_r.netomichatdemo.domain.model.ChatMessage
import com.abizer_r.netomichatdemo.domain.model.MessageStatus
import com.abizer_r.netomichatdemo.domain.repo.ChatRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val conversationMap = LinkedHashMap<String, ChatConversation>()

    override suspend fun start(clientId: String) {
        socketClient.connect()

        scope.launch(ioDispatcher) {
            socketClient.events.collectLatest { payload ->
                handleIncomingPayload(payload = payload, clientId = clientId)
            }
        }
    }

    override suspend fun sendUserMessage(text: String, clientId: String) {
        if (text.isBlank()) return

        val now = System.currentTimeMillis()

        // 1) user message payload
        val userPayload = ChatPayload(
            type = "user_message",
            conversationId = CHANNEL_ID,
            text = text,
            senderId = clientId,
            timestamp = now
        )

        socketClient.send(userPayload)

        // 2) bot reply
        val botPayload = buildBotReply(userPayload)
        socketClient.send(botPayload)
    }

    private fun handleIncomingPayload(payload: ChatPayload, clientId: String) {
        val isBot = payload.senderId == BOT_ID || payload.type == "bot_message"
        val isMine = payload.senderId == clientId && !isBot

        val messageId = "${payload.senderId}-${payload.timestamp}-${abs(payload.text.hashCode())}"

        val message = ChatMessage(
            id = messageId,
            conversationId = payload.conversationId,
            text = payload.text,
            timestamp = payload.timestamp,
            isMine = isMine,
            isBot = isBot,
            status = MessageStatus.SENT
        )

        val existing = conversationMap[payload.conversationId]
        val updatedConversation = existing?.copy(
            lastMessage = message,
            messages = existing.messages + message
        )
            ?: ChatConversation(
                id = payload.conversationId,
                title = "Bot chat", // can later be dynamic / multiple bots
                lastMessage = message,
                messages = listOf(message)
            )

        conversationMap[payload.conversationId] = updatedConversation
        _conversations.value = conversationMap.values.toList()
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
}
