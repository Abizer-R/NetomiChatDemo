package com.abizer_r.netomichatdemo.data.socket

import org.json.JSONException
import org.json.JSONObject

const val BOT_ID = "BOT"
const val CHANNEL_ID = "bot-1"
const val EVENT_NAME = "new-message"

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
                null
            }
        }
    }
}

sealed class ConnectionState {
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Disconnected : ConnectionState()
    data class Error(val message: String?) : ConnectionState()
}
