package com.lanchat.app.network

import fi.iki.elonen.NanoWSD
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * سيرفر WebSocket محلي يعمل على الجهاز "المستضيف".
 * كل الأجهزة الأخرى المتصلة بنفس الشبكة (أو الهوتسبوت) تتصل به مباشرة عبر IP:PORT.
 * السيرفر يقوم بإعادة بث (broadcast) كل رسالة لجميع العملاء المتصلين،
 * بما فيهم الجهاز المستضيف نفسه (الذي يُعامل كعميل محلي أيضاً).
 */
class ChatServer(
    port: Int,
    private val hostName: String,
    private var serverPassword: String? = null,
    private val listener: ServerListener
) : NanoWSD(port) {

    companion object {
        const val DEFAULT_PORT = 8765
        private const val SOCKET_READ_TIMEOUT = 60000
    }

    private var hostId: String? = null

    fun setPassword(password: String?) {
        this.serverPassword = password
    }

    fun setHostId(id: String) {
        this.hostId = id
    }

    interface ServerListener {
        fun onMessageReceived(message: JSONObject)
        fun onUserListChanged(users: List<Pair<String, String>>) // id -> name
        fun onClientConnected(id: String, name: String)
        fun onClientDisconnected(id: String, name: String)
    }

    // معرف -> (الاسم، السوكيت)
    private val clients = ConcurrentHashMap<String, ClientInfo>()

    data class ClientInfo(var name: String, val socket: ClientSocket)

    override fun openWebSocket(handshake: fi.iki.elonen.NanoHTTPD.IHTTPSession): WebSocket {
        return ClientSocket(handshake)
    }

    private var pingExecutor: java.util.concurrent.ScheduledExecutorService? = null

    /**
     * يبدأ السيرفر مع تفعيل نبضات اتصال دورية (Ping) لكل العملاء.
     * هذا ضروري عند عبور الاتصال لعدة راوترات/شبكات فرعية، حيث تقوم
     * أجهزة التوجيه عادة بإسقاط الاتصالات الخاملة بعد فترة قصيرة (NAT timeout).
     */
    fun startWithPing() {
        start(SOCKET_READ_TIMEOUT, true)
        pingExecutor?.shutdownNow()
        pingExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        pingExecutor?.scheduleWithFixedDelay({
            for (client in clients.values) {
                try {
                    client.socket.ping(ByteArray(0))
                } catch (e: Exception) {
                    // سيتم تنظيف العميل عند onException/onClose
                }
            }
        }, 25, 25, java.util.concurrent.TimeUnit.SECONDS)
    }

    override fun stop() {
        pingExecutor?.shutdownNow()
        pingExecutor = null
        super.stop()
    }

    /** يبث رسالة JSON لكل العملاء المتصلين */
    fun broadcast(json: JSONObject) {
        val text = json.toString()
        for (client in clients.values) {
            try {
                client.socket.send(text)
            } catch (e: IOException) {
                // تجاهل أخطاء الإرسال للعملاء المنقطعين، ستتم إزالتهم عند onClose
            }
        }
    }

    /** قائمة الأجهزة المتصلة حالياً */
    fun getConnectedUsers(): List<Pair<String, String>> =
        clients.map { it.key to it.value.name }

    fun getLocalIpAddress(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is InetAddress && !addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * يعيد كل عناوين IPv4 المحلية المتاحة على كل واجهات الشبكة (Wi-Fi، هوتسبوت، إيثرنت...).
     * مفيد عند وجود عدة راوترات/شبكات فرعية متصلة بالجهاز المستضيف، حيث قد يكون
     * للجهاز أكثر من عنوان IP على واجهات مختلفة.
     */
    fun getAllLocalIpAddresses(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is InetAddress && !addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                        result.add(addr.hostAddress)
                    }
                }
            }
        } catch (e: Exception) {
        }
        return result
    }

    inner class ClientSocket(handshake: fi.iki.elonen.NanoHTTPD.IHTTPSession) : WebSocket(handshake) {

        private var clientId: String = UUID.randomUUID().toString()
        private var clientName: String = "مستخدم"

        override fun onOpen() {
            // ننتظر رسالة "hello" من العميل لتحديد اسمه قبل تسجيله رسمياً
        }

        override fun onClose(code: fi.iki.elonen.NanoWSD.WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            clients.remove(clientId)
            listener.onClientDisconnected(clientId, clientName)
            broadcastUserList()
            broadcastSystem("$clientName غادر المحادثة")
        }

        override fun onMessage(message: fi.iki.elonen.NanoWSD.WebSocketFrame) {
            try {
                val json = JSONObject(message.textPayload)
                when (json.optString("type")) {
                    "hello" -> {
                        val password = json.optString("password")
                        if (serverPassword != null && serverPassword != password) {
                            val error = JSONObject().apply {
                                put("type", "error")
                                put("message", "كلمة السر خاطئة")
                            }
                            send(error.toString())
                            close(fi.iki.elonen.NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "Wrong password", true)
                            return
                        }
                        clientId = json.optString("senderId", clientId)
                        clientName = json.optString("sender", "مستخدم")
                        clients[clientId] = ClientInfo(clientName, this)
                        listener.onClientConnected(clientId, clientName)
                        broadcastUserList()
                        broadcastSystem("$clientName انضم إلى المحادثة")
                    }
                    "kick" -> {
                        val targetId = json.optString("targetId")
                        val requesterId = json.optString("senderId")
                        if (requesterId == hostId) {
                            clients[targetId]?.socket?.close(fi.iki.elonen.NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "Kicked by host", true)
                        }
                    }
                    "message", "image", "status_update" -> {
                        listener.onMessageReceived(json)
                        broadcast(json)
                    }
                    else -> {
                        // أنواع أخرى تُعاد بثها كما هي
                        broadcast(json)
                    }
                }
            } catch (e: Exception) {
                // رسالة غير صالحة، تجاهلها
            }
        }

        override fun onPong(pong: fi.iki.elonen.NanoWSD.WebSocketFrame?) {}

        override fun onException(exception: IOException?) {
            clients.remove(clientId)
            listener.onClientDisconnected(clientId, clientName)
            broadcastUserList()
        }
    }

    private fun broadcastUserList() {
        val arr = org.json.JSONArray()
        for ((id, name) in getConnectedUsers()) {
            val o = JSONObject()
            o.put("id", id)
            o.put("name", name)
            arr.put(o)
        }
        val json = JSONObject()
        json.put("type", "userlist")
        json.put("users", arr)
        broadcast(json)
        listener.onUserListChanged(getConnectedUsers())
    }

    private fun broadcastSystem(text: String) {
        val json = JSONObject()
        json.put("type", "system")
        json.put("id", UUID.randomUUID().toString())
        json.put("sender", "النظام")
        json.put("senderId", "system")
        json.put("text", text)
        json.put("timestamp", System.currentTimeMillis())
        broadcast(json)
    }
}
