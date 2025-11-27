package com.abizer_r.netomichatdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.abizer_r.netomichatdemo.data.repo.ChatRepositoryImpl
import com.abizer_r.netomichatdemo.data.socket.PieSocketChatSocketClient
import com.abizer_r.netomichatdemo.domain.repo.ChatRepository
import com.abizer_r.netomichatdemo.ui.chat.ChatDetailScreen
import com.abizer_r.netomichatdemo.ui.chat.ChatListScreen
import com.abizer_r.netomichatdemo.ui.chat.ChatViewModel
import com.abizer_r.netomichatdemo.ui.theme.NetomiChatDemoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

            val navController = rememberNavController()

            // TODO: replace with hilt injection
            val viewModel = ViewModelProvider(
                owner = this,
                factory = ChatViewModelFactory(appScope)
            )[ChatViewModel::class.java]

            val state by viewModel.uiState.collectAsState()


            NetomiChatDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    NavHost(
                        modifier = Modifier.padding(innerPadding),
                        navController = navController,
                        startDestination = "chatList"
                    ) {
                        composable("chatList") {
                            ChatListScreen(
                                state = state,
                                onStartNewChat = {
                                    val newId = viewModel.startNewChat()
                                    navController.navigate("chatDetail/$newId")
                                },
                                onConversationClicked = { id ->
                                    viewModel.onConversationSelected(id)
                                    navController.navigate("chatDetail/$id")
                                },
                                onOnlineToggle = viewModel::onOnlineToggle
                            )
                        }

                        composable(
                            route = "chatDetail/{conversationId}",
                            arguments = listOf(
                                navArgument("conversationId") {
                                    type = NavType.StringType
                                }
                            )
                        ) { backStackEntry ->
                            val convId = backStackEntry.arguments?.getString("conversationId") ?: ""

                            // Ensure VM knows which conversation is active (in case of deep link / back navigation)
                            if (state.activeConversationId != convId && convId.isNotBlank()) {
                                viewModel.onConversationSelected(convId)
                            }

                            ChatDetailScreen(
                                state = state,
                                events = viewModel.events,
                                conversationId = convId,
                                onBackClicked = {
                                    navController.popBackStack()
                                },
                                onSendClicked = viewModel::onSendMessage
                            )
                        }
                    }
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
