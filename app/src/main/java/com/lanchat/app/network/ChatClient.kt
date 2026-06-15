package com.lanchat.app.network

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.util.UUID

/**
 * عميل WebSocket يتصل بسيرفر المحادثة المحلي (سواء كان على جهاز آخر،
 * أو على نفس الجهاز عند تشغيل السيرفر محلياً).
 */
class ChatClient(
    serverIp: String,
    port: Int,
    val userName: String,
    val userId: String = UUID.randomUUID().toString(),
    private val listener: ClientListener
) {

    interface ClientListener {
        fun onConnected()
        fun onDisconnected()
        fun onMessage(json: JSONObject)
        fun onError(message: String)
    }

    private val client: WebSocketClient

    init {
        val uri = URI("ws://$serverIp:$port")
        client = object : WebSocketClient(uri) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                // أرسل رسالة تعريف فور الاتصال
                val hello = JSONObject()
                hello.put("type", "hello")
                hello.put("id", UUID.randomUUID().toString())
                hello.put("sender", userName)
                hello.put("senderId", userId)
                hello.put("text", "")
                hello.put("timestamp", System.currentTimeMillis())
                send(hello.toString())
                listener.onConnected()
            }

            override fun onMessage(message: String?) {
                if (message == null) return
                try {
                    val json = JSONObject(message)
                    listener.onMessage(json)
                } catch (e: Exception) {
                    // تجاهل رسائل غير صالحة
                }
            }

            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                listener.onDisconnected()
            }

            override fun onError(ex: Exception?) {
                listener.onError(ex?.message ?: "خطأ في الاتصال")
            }
        }
    }

    fun connect() {
        client.connectionLostTimeout = 30
        client.connect()
    }

    fun close() {
        try {
            client.close()
        } catch (e: Exception) {
        }
    }

    fun sendMessage(text: String) {
        val json = JSONObject()
        json.put("type", "message")
        json.put("id", UUID.randomUUID().toString())
        json.put("sender", userName)
        json.put("senderId", userId)
        json.put("text", text)
        json.put("timestamp", System.currentTimeMillis())
        try {
            client.send(json.toString())
        } catch (e: Exception) {
            listener.onError("فشل إرسال الرسالة")
        }
    }

    fun isOpen(): Boolean = client.isOpen
}
