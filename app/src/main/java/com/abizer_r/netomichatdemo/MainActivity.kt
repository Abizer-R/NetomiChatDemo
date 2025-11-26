package com.abizer_r.netomichatdemo

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.abizer_r.netomichatdemo.data.repo.ChatRepositoryImpl
import com.abizer_r.netomichatdemo.data.socket.PieSocketChatSocketClient
import com.abizer_r.netomichatdemo.domain.repo.ChatRepository
import com.abizer_r.netomichatdemo.ui.chat.ChatScreen
import com.abizer_r.netomichatdemo.ui.chat.ChatViewModel
import com.abizer_r.netomichatdemo.ui.theme.NetomiChatDemoTheme
import com.piesocket.channels.Channel
import com.piesocket.channels.PieSocket
import com.piesocket.channels.misc.PieSocketEvent
import com.piesocket.channels.misc.PieSocketEventListener
import com.piesocket.channels.misc.PieSocketOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import kotlin.apply

private const val PREFS_NAME = "chat_prefs"
private const val PREF_CLIENT_ID = "client_id"
private const val BOT_ID = "BOT"
private const val CHANNEL_ID = "bot-1"           // single bot conversation id
private const val EVENT_NAME = "new-message"    // socket event name


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        setContent {

            NetomiChatDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    // TODO: replace with hilt injection
                    val viewModel = ViewModelProvider(
                        owner = this,
                        factory = ChatViewModelFactory(appScope)
                    )[ChatViewModel::class.java]

                    val state by viewModel.uiState.collectAsState()

                    ChatScreen(
                        modifier = Modifier.padding(innerPadding),
                        state = state,
                        events = viewModel.events,
                        onSendClicked = viewModel::onSendMessage,
                        onConversationClicked = viewModel::onConversationSelected,
                        onOnlineToggle = viewModel::onOnlineToggle
                    )
                }
            }
        }
    }
}

class ChatViewModelFactory(
    private val appScope: CoroutineScope
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Wire up data layer
        val socketClient = PieSocketChatSocketClient(
            scope = appScope
        )
        val repository: ChatRepository = ChatRepositoryImpl(
            socketClient = socketClient,
            scope = appScope
        )
        return ChatViewModel(repository) as T
    }
}
