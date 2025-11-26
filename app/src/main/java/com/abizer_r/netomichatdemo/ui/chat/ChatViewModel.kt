package com.abizer_r.netomichatdemo.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abizer_r.netomichatdemo.domain.repo.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val clientId: String = UUID.randomUUID().toString() // for now; can be injected

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    // TODO: Wire the send/queue logic
    private val _isOnline = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            repository.start(clientId)
        }

        // Combine repository flows + online flag into a single UI state
        viewModelScope.launch {
            combine(
                repository.conversations,
                repository.connectionState,
                _isOnline
            ) { conversations, connection, isOnline ->

                val currState = _uiState.value

                val activeId = _uiState.value.activeConversationId
                    ?: conversations.firstOrNull()?.id

                val activeMessages = conversations
                    .firstOrNull { it.id == activeId }
                    ?.messages
                    ?.map {
                        ChatMessageItemUi(
                            id = it.id,
                            text = it.text,
                            isMine = it.isMine,
                            isBot = it.isBot
                        )
                    }.orEmpty()

                ChatUiState(
                    connectionState = connection,
                    conversations = conversations.map { conv ->
                        ChatConversationItemUi(
                            id = conv.id,
                            title = conv.title,
                            lastMessagePreview = conv.lastMessage?.text.orEmpty()
                        )
                    },
                    activeConversationId = activeId,
                    messages = activeMessages,
                    isOnline = isOnline,
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun onSendMessage(text: String) {
        viewModelScope.launch {
            repository.sendUserMessage(text, clientId)
        }
    }

    fun onConversationSelected(id: String) {
        _uiState.value = _uiState.value.copy(activeConversationId = id)
    }

    fun onOnlineToggle(isOnline: Boolean) {
        _isOnline.value = isOnline
    }
}
