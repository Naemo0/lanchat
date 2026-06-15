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
    private val listener: ServerListener
) : NanoWSD(port) {

    companion object {
        const val DEFAULT_PORT = 8765
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
                        clientId = json.optString("senderId", clientId)
                        clientName = json.optString("sender", "مستخدم")
                        clients[clientId] = ClientInfo(clientName, this)
                        listener.onClientConnected(clientId, clientName)
                        broadcastUserList()
                        broadcastSystem("$clientName انضم إلى المحادثة")
                    }
                    "message" -> {
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
