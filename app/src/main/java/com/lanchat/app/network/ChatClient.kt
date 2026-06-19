package com.lanchat.app.network

import com.lanchat.app.data.ChatMessage
import com.lanchat.app.data.MessageStatus
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.util.UUID

/**
 * Professional WebSocket Client for LanChat.
 */
class ChatClient(
    val serverIp: String,
    val port: Int,
    val userName: String,
    val userId: String,
    private val listener: ClientListener,
    private val password: String? = null,
    private val avatar: String? = null
) {

    interface ClientListener {
        fun onConnected()
        fun onDisconnected()
        fun onMessage(json: JSONObject)
        fun onError(message: String)
    }

    private var client: WebSocketClient
    private var reconnectEnabled = true
    private val reconnectHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var reconnectAttempts = 0

    init {
        client = buildClient()
    }

    private fun buildClient(): WebSocketClient {
        val uri = URI("ws://$serverIp:$port")
        return object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                reconnectAttempts = 0
                // Send Identity Handshake
                val hello = JSONObject().apply {
                    put("type", ChatMessage.TYPE_HELLO)
                    put("id", UUID.randomUUID().toString())
                    put("sender", userName)
                    put("senderId", userId)
                    put("password", password ?: "")
                    put("avatar", avatar ?: "")
                    put("timestamp", System.currentTimeMillis())
                }
                send(hello.toString())
                listener.onConnected()
            }

            override fun onMessage(message: String?) {
                if (message == null) return
                try {
                    val json = JSONObject(message)
                    listener.onMessage(json)
                } catch (e: Exception) {}
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                listener.onDisconnected()
                scheduleReconnect()
            }

            override fun onError(ex: Exception?) {
                listener.onError(ex?.message ?: "Connection Error")
            }
        }
    }

    private fun scheduleReconnect() {
        if (!reconnectEnabled) return
        reconnectAttempts++
        val delayMs = (1000L * reconnectAttempts).coerceAtMost(10000L)
        reconnectHandler.postDelayed({
            if (!reconnectEnabled) return@postDelayed
            try {
                client = buildClient()
                client.connectionLostTimeout = CONNECTION_LOST_TIMEOUT_SEC
                client.connect()
            } catch (e: Exception) {
                listener.onError("Reconnect failed")
            }
        }, delayMs)
    }

    fun connect() {
        client.connectionLostTimeout = CONNECTION_LOST_TIMEOUT_SEC
        client.connect()
    }

    fun close() {
        reconnectEnabled = false
        reconnectHandler.removeCallbacksAndMessages(null)
        try { client.close() } catch (e: Exception) {}
    }

    fun sendMessage(text: String, id: String = UUID.randomUUID().toString(), replyToId: String? = null, replyToText: String? = null, replyToSender: String? = null) {
        val json = JSONObject().apply {
            put("type", ChatMessage.TYPE_MESSAGE)
            put("id", id)
            put("sender", userName)
            put("senderId", userId)
            put("text", text)
            put("timestamp", System.currentTimeMillis())
            put("replyToId", replyToId)
            put("replyToText", replyToText)
            put("replyToSender", replyToSender)
            put("avatar", avatar)
        }
        sendSafely(json)
    }

    fun sendFile(fileName: String, base64Data: String, id: String = UUID.randomUUID().toString()) {
        val json = JSONObject().apply {
            put("type", ChatMessage.TYPE_FILE)
            put("id", id)
            put("sender", userName)
            put("senderId", userId)
            put("fileName", fileName)
            put("fileData", base64Data)
            put("text", "Sent a file: $fileName")
            put("timestamp", System.currentTimeMillis())
            put("avatar", avatar)
        }
        sendSafely(json)
    }

    fun sendVoice(base64Data: String, id: String = UUID.randomUUID().toString()) {
        val json = JSONObject().apply {
            put("type", ChatMessage.TYPE_VOICE)
            put("id", id)
            put("sender", userName)
            put("senderId", userId)
            put("voiceData", base64Data)
            put("text", "Voice message")
            put("timestamp", System.currentTimeMillis())
            put("avatar", avatar)
        }
        sendSafely(json)
    }

    fun sendStatusUpdate(messageId: String, status: Int) {
        val json = JSONObject().apply {
            put("type", ChatMessage.TYPE_STATUS_UPDATE)
            put("messageId", messageId)
            put("status", status)
            put("senderId", userId)
        }
        sendSafely(json)
    }

    fun sendTypingStatus(isTyping: Boolean) {
        val json = JSONObject().apply {
            put("type", ChatMessage.TYPE_TYPING)
            put("senderId", userId)
            put("sender", userName)
            put("isTyping", isTyping)
        }
        sendSafely(json)
    }

    fun sendImage(base64Image: String, caption: String = "") {
        val json = JSONObject().apply {
            put("type", ChatMessage.TYPE_IMAGE)
            put("id", UUID.randomUUID().toString())
            put("sender", userName)
            put("senderId", userId)
            put("text", caption)
            put("imageData", base64Image)
            put("timestamp", System.currentTimeMillis())
            put("avatar", avatar)
        }
        sendSafely(json)
    }

    private fun sendSafely(json: JSONObject) {
        try {
            if (client.isOpen) client.send(json.toString())
        } catch (e: Exception) {
            listener.onError("Failed to send data")
        }
    }

    fun isOpen(): Boolean = client.isOpen

    companion object {
        const val CONNECTION_LOST_TIMEOUT_SEC = 60
    }
}
