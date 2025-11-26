package com.abizer_r.netomichatdemo.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abizer_r.netomichatdemo.domain.repo.ChatRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

sealed class UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent()
}

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val clientId: String = UUID.randomUUID().toString() // for now; can be injected

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events

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

                val activeId = currState.activeConversationId
                    ?: conversations.firstOrNull()?.id

                val activeMessages = conversations
                    .firstOrNull { it.id == activeId }
                    ?.messages
                    ?.map {
                        ChatMessageItemUi(
                            id = it.id,
                            text = it.text,
                            isMine = it.isMine,
                            isBot = it.isBot,
                            status = it.status
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

        // Collect repository error messages and convert to UiEvents
        viewModelScope.launch {
            repository.errorEvents.collectLatest { msg ->
                _events.emit(UiEvent.ShowSnackbar(msg))
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
        repository.onNetworkStatusChanged(isOnline)
    }
}
