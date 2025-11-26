package com.abizer_r.netomichatdemo

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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

class MainActivity : ComponentActivity() {

    private var channel: Channel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetomiChatDemoTheme {

                // UI state for our tiny POC
                val messages = remember { mutableStateListOf("App started") }
                var connectionStatus by remember { mutableStateOf("Connecting…") }

                // Initialize PieSocket exactly once
                LaunchedEffect(Unit) {
                    initPieSocket(
                        onStatusChange = { status ->
                            connectionStatus = status
                            messages.add("STATUS: $status")
                        },
                        onMessage = { text ->
                            messages.add(text)
                        }
                    )
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    PieSocketDummyScreen(
                        modifier = Modifier.padding(innerPadding),
                        connectionStatus = connectionStatus,
                        messages = messages,
                        onSendClick = {
                            sendTestMessage()
                        }
                    )
                }
            }
        }
    }

    private fun initPieSocket(
        onStatusChange: (String) -> Unit,
        onMessage: (String) -> Unit
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

        // Event: Channel connected
        joinedChannel.listen("system:connected", object : PieSocketEventListener() {
            override fun handleEvent(event: PieSocketEvent) {
                Log.d("test", "system:connected: ${event.data}")
                runOnUiThread {
                    onStatusChange("Connected")
                }
            }
        })

        // Event: A user has joined
        joinedChannel.listen("system:member_joined", object : PieSocketEventListener() {
            override fun handleEvent(event: PieSocketEvent) {
                Log.d("test", "system:member_joined: ${event.data}")
            }
        })

        // Event: A user has left
        joinedChannel.listen("system:member_left", object : PieSocketEventListener() {
            override fun handleEvent(event: PieSocketEvent) {
                Log.d("test", "system:member_left: ${event.data}")
            }
        })

        // Event: Message arrived on socket connection
        joinedChannel.listen("system:message", object : PieSocketEventListener() {
            override fun handleEvent(event: PieSocketEvent) {
                Log.d("test", "system:message: ${event.data}")
            }
        })

        // Event: An error occurred
        joinedChannel.listen("system:error", object : PieSocketEventListener() {
            override fun handleEvent(event: PieSocketEvent) {
                Log.d("test", "system:error: ${event.data}")
            }
        })

        // Event: WebSocekt closed
        joinedChannel.listen("system:closed", object : PieSocketEventListener() {
            override fun handleEvent(event: PieSocketEvent) {
                Log.d("test", "system:closed: ${event.data}")
                runOnUiThread {
                    onStatusChange("Connection Closed")
                }
            }
        })

        // All Events
        joinedChannel.listen("*", object : PieSocketEventListener() {
            override fun handleEvent(event: PieSocketEvent) {
                Log.i("test", "all events: ${event.data}")
            }
        })

        // Our own event for sending messages
        joinedChannel.listen("new-message", object : PieSocketEventListener() {
            override fun handleEvent(event: PieSocketEvent) {
                Log.d("test", "system:message: ${event.data}")
                runOnUiThread {
                    onMessage("INCOMING: ${event.data}")
                }
            }
        })
    }

    private fun sendTestMessage() {
        val currentChannel = channel ?: return

        val event = PieSocketEvent("new-message")
        event.setData("Hello from Android at ${System.currentTimeMillis()}")

        try {
            currentChannel.publish(event)
            Log.d("test", "Sent test event")
        } catch (t: Throwable) {
            Log.e("test", "Failed to send event", t)
        }
    }
}

@Composable
fun PieSocketDummyScreen(
    modifier: Modifier = Modifier,
    connectionStatus: String,
    messages: List<String>,
    onSendClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "PieSocket POC",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Status: $connectionStatus",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        Button(onClick = onSendClick) {
            Text("Send test message")
        }
        Spacer(Modifier.height(16.dp))

        Text("Events:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(messages) { msg ->
                Text(msg)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}


@PreviewLightDark
@Composable
private fun Preview() {
    NetomiChatDemoTheme {
        PieSocketDummyScreen(
            connectionStatus = "Connecting",
            messages = emptyList(),
            onSendClick = {}
        )
    }
}