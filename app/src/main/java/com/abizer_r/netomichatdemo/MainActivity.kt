package com.abizer_r.netomichatdemo

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.abizer_r.netomichatdemo.ui.theme.NetomiChatDemoTheme
import com.piesocket.channels.Channel
import com.piesocket.channels.PieSocket
import com.piesocket.channels.misc.PieSocketEvent
import com.piesocket.channels.misc.PieSocketEventListener
import com.piesocket.channels.misc.PieSocketOptions
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

    private var channel: Channel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val clientId = getOrCreateClientId()

            NetomiChatDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChatCoreScreen(
                        modifier = Modifier.padding(innerPadding),
                        clientId = clientId,
                        initSocket = { onStatusChange, onMessagePayload ->
                            initPieSocket(
                                onStatusChange = onStatusChange,
                                onMessagePayload = onMessagePayload
                            )
                        },
                        sendUserMessage = { text ->
                            sendUserMessage(text = text, clientId = clientId)
                        }
                    )
                }
            }
        }
    }

    private fun initPieSocket(
        onStatusChange: (String) -> Unit,
        onMessagePayload: (ChatPayload) -> Unit
    ) {
        val options = PieSocketOptions().apply {
            // TODO: hide clusterId and apiKey
            clusterId = "s15479.blr1"
            apiKey = "EKktzzwLK0gnXos0qO3Fg9LtfUuWrcQ4XAWKr5jO"
            enableLogs = true

        }
        val piesocket = PieSocket(options)

        val joinedChannel = piesocket.join("chat-room-dummy")
        channel = joinedChannel

        onStatusChange("Joined channel, waiting for connection…")

        // Connection event
        joinedChannel.listen("system:connected", object : PieSocketEventListener() {
            override fun handleEvent(event: PieSocketEvent) {
                Log.d("test", "system:connected: ${event.getData()}")
                runOnUiThread {
                    onStatusChange("Connected")
                }
            }
        })

        // Our chat event: all user & bot messages go through here
        joinedChannel.listen(EVENT_NAME, object : PieSocketEventListener() {
            override fun handleEvent(event: PieSocketEvent) {
                val raw = event.getData()
                Log.d("test", "EVENT $EVENT_NAME: $raw")

                val payload = ChatPayload.fromJsonOrNull(raw)
                if (payload != null) {
                    runOnUiThread {
                        onMessagePayload(payload)
                    }
                } else {
                    Log.e("test", "Failed to parse payload: $raw")
                }
            }
        })
    }

    private fun sendUserMessage(text: String, clientId: String) {
        if (text.isBlank()) return

        val now = System.currentTimeMillis()

        // 1) User message
        val userPayload = ChatPayload(
            type = "user_message",
            conversationId = CHANNEL_ID,
            text = text,
            senderId = clientId,
            timestamp = now
        )

        publishPayload(userPayload)

        // 2) Bot reply – generated locally, but sent via WebSocket
        val botPayload = buildBotReply(userPayload)
        publishPayload(botPayload)
    }

    private fun publishPayload(payload: ChatPayload) {
        val currentChannel = channel ?: run {
            Log.w("test", "publishPayload: channel is null")
            return
        }

        val event = PieSocketEvent(EVENT_NAME)
        event.setData(payload.toJson())

        try {
            currentChannel.publish(event)
            Log.d("test", "Published ${payload.type}: ${payload.text}")
        } catch (t: Throwable) {
            Log.e("test", "Failed to publish payload", t)
        }
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

    private fun getOrCreateClientId(): String {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val existing = prefs.getString(PREF_CLIENT_ID, null)
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(PREF_CLIENT_ID, newId).apply()
        return newId
    }
}


data class ChatPayload(
    val type: String,
    val conversationId: String,
    val text: String,
    val senderId: String,
    val timestamp: Long
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("type", type)
        obj.put("conversationId", conversationId)
        obj.put("text", text)
        obj.put("senderId", senderId)
        obj.put("timestamp", timestamp)
        return obj.toString()
    }

    companion object {
        fun fromJsonOrNull(json: String?): ChatPayload? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                ChatPayload(
                    type = obj.optString("type"),
                    conversationId = obj.optString("conversationId"),
                    text = obj.optString("text"),
                    senderId = obj.optString("senderId"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            } catch (e: JSONException) {
                e.printStackTrace()
                null
            }
        }
    }
}

data class ChatMessageUi(
    val id: String,
    val text: String,
    val isMine: Boolean,
    val isBot: Boolean,
    val timestamp: Long
)

@Composable
fun ChatCoreScreen(
    modifier: Modifier = Modifier,
    clientId: String,
    initSocket: (
        onStatusChange: (String) -> Unit,
        onMessagePayload: (ChatPayload) -> Unit
    ) -> Unit,
    sendUserMessage: (String) -> Unit
) {
    val messages = remember { mutableStateListOf<ChatMessageUi>() }
    var connectionStatus by remember { mutableStateOf("Connecting…") }
    var inputText by remember { mutableStateOf("") }

    // Initialize PieSocket once when the Composable enters composition
    LaunchedEffect(Unit) {
        initSocket(
            { status -> /* onStatusChange() */
                connectionStatus = status
            },
            { payload ->    /* onMessagePayload() */
                val isBot = payload.senderId == BOT_ID || payload.type == "bot_message"
                val isMine = payload.senderId == clientId && !isBot
                val messageId = "${payload.senderId}-${payload.timestamp}-${payload.text.hashCode()}"

                messages.add(
                    ChatMessageUi(
                        id = messageId,
                        text = payload.text,
                        isMine = isMine,
                        isBot = isBot,
                        timestamp = payload.timestamp
                    )
                )
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "PieSocket Chat Core POC",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Status: $connectionStatus",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Client ID: $clientId",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageRow(msg = msg)
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Type a message") }
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val text = inputText.trim()
                    if (text.isNotEmpty()) {
                        sendUserMessage(text)
                        inputText = ""
                    }
                }
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
fun ChatMessageRow(msg: ChatMessageUi) {
    val prefix = when {
        msg.isBot -> "Bot:"
        msg.isMine -> "Me:"
        else -> "User:"
    }

    Text(
        text = "$prefix ${msg.text}",
        style = MaterialTheme.typography.bodyMedium
    )
}


@PreviewLightDark
@Composable
private fun Preview() {
    NetomiChatDemoTheme {
        ChatCoreScreen(
            clientId = "dummyId",
            initSocket = {_, _ -> },
            sendUserMessage = {}
        )
    }
}