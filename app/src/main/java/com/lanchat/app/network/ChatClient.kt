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
    val serverIp: String,
    val port: Int,
    val userName: String,
    val userId: String,
    private val password: String? = null,
    private val listener: ClientListener
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
                // أرسل رسالة تعريف فور الاتصال
                val hello = JSONObject()
                hello.put("type", "hello")
                hello.put("id", UUID.randomUUID().toString())
                hello.put("sender", userName)
                hello.put("senderId", userId)
                hello.put("password", password ?: "")
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
                scheduleReconnect()
            }

            override fun onError(ex: Exception?) {
                listener.onError(ex?.message ?: "خطأ في الاتصال")
            }
        }
    }

    /**
     * يعيد محاولة الاتصال تلقائياً عند الانقطاع، بفاصل زمني متزايد (Backoff).
     * هذا مهم على شبكات أكبر بها عدة راوترات، حيث قد ينقطع الاتصال مؤقتاً
     * بسبب تبديل المسار بين الشبكات الفرعية.
     */
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
                listener.onError("فشل إعادة الاتصال")
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
        try {
            client.close()
        } catch (e: Exception) {
        }
    }

    fun sendMessage(text: String, id: String = UUID.randomUUID().toString(), replyToId: String? = null, replyToText: String? = null) {
        val json = JSONObject().apply {
            put("type", "message")
            put("id", id)
            put("sender", userName)
            put("senderId", userId)
            put("text", text)
            put("timestamp", System.currentTimeMillis())
            put("replyToId", replyToId)
            put("replyToText", replyToText)
        }
        try {
            if (client.isOpen) client.send(json.toString())
        } catch (e: Exception) {
            listener.onError("فشل إرسال الرسالة")
        }
    }

    fun sendFile(fileName: String, base64Data: String, id: String = UUID.randomUUID().toString()) {
        val json = JSONObject().apply {
            put("type", "file")
            put("id", id)
            put("sender", userName)
            put("senderId", userId)
            put("fileName", fileName)
            put("fileData", base64Data)
            put("text", "[ملف: $fileName]")
            put("timestamp", System.currentTimeMillis())
        }
        try {
            if (client.isOpen) client.send(json.toString())
        } catch (e: Exception) {
            listener.onError("فشل إرسال الملف")
        }
    }

    fun sendVoice(base64Data: String, id: String = UUID.randomUUID().toString()) {
        val json = JSONObject().apply {
            put("type", "voice")
            put("id", id)
            put("sender", userName)
            put("senderId", userId)
            put("fileData", base64Data)
            put("text", "[رسالة صوتية]")
            put("timestamp", System.currentTimeMillis())
        }
        try {
            if (client.isOpen) client.send(json.toString())
        } catch (e: Exception) {
            listener.onError("فشل إرسال الرسالة الصوتية")
        }
    }

    fun sendStatusUpdate(messageId: String, status: Int) {
        val json = JSONObject()
        json.put("type", "status_update")
        json.put("messageId", messageId)
        json.put("status", status)
        json.put("senderId", userId)
        try {
            if (client.isOpen) client.send(json.toString())
        } catch (e: Exception) {}
    }

    fun sendTypingStatus(isTyping: Boolean) {
        val json = JSONObject().apply {
            put("type", "typing")
            put("senderId", userId)
            put("sender", userName)
            put("isTyping", isTyping)
        }
        try {
            if (client.isOpen) client.send(json.toString())
        } catch (e: Exception) {}
    }

    /**
     * يرسل صورة مرمّزة بـ Base64 إلى السيرفر ليُعاد بثها لجميع المتصلين.
     * يجب ضغط الصورة قبل الإرسال (انظر ImageUtils) للحفاظ على سرعة الشبكة.
     */
    fun sendImage(base64Image: String, caption: String = "") {
        val json = JSONObject()
        json.put("type", "image")
        json.put("id", UUID.randomUUID().toString())
        json.put("sender", userName)
        json.put("senderId", userId)
        json.put("text", caption)
        json.put("image", base64Image)
        json.put("timestamp", System.currentTimeMillis())
        try {
            client.send(json.toString())
        } catch (e: Exception) {
            listener.onError("فشل إرسال الصورة")
        }
    }

    fun isOpen(): Boolean = client.isOpen

    companion object {
        // مهلة أطول لاستيعاب تأخير الشبكات الكبيرة متعددة الراوترات
        const val CONNECTION_LOST_TIMEOUT_SEC = 60
    }
}
