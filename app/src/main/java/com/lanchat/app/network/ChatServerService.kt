package com.lanchat.app.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lanchat.app.MainActivity
import com.lanchat.app.R
import org.json.JSONObject

/**
 * خدمة تعمل في الخلفية لإبقاء سيرفر المحادثة شغّالاً
 * حتى لو انتقل المستخدم لتطبيق آخر أو أُغلقت الشاشة.
 */
class ChatServerService : Service() {

    companion object {
        const val CHANNEL_ID = "lanchat_server_channel"
        const val NOTIFICATION_ID = 1001
        var server: ChatServer? = null
            private set
        var isRunning = false
            private set
        private var nsdHelper: NsdHelper? = null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val hostName = intent?.getStringExtra("hostName") ?: "المستضيف"
        val port = intent?.getIntExtra("port", ChatServer.DEFAULT_PORT) ?: ChatServer.DEFAULT_PORT
        val password = intent?.getStringExtra("password")

        if (!isRunning) {
            startForeground(NOTIFICATION_ID, buildNotification("جاري تشغيل السيرفر..."))

            server = ChatServer(port, hostName, password, object : ChatServer.ServerListener {
                override fun onMessageReceived(message: JSONObject) {}
                override fun onUserListChanged(users: List<Pair<String, String>>) {
                    updateNotification("متصل: ${users.size} مستخدم")
                }
                override fun onClientConnected(id: String, name: String) {}
                override fun onClientDisconnected(id: String, name: String) {}
            })
            server?.setHostId(com.lanchat.app.util.DeviceUtils.getUniqueId(this))

            try {
                server?.startWithPing()
                isRunning = true
                
                nsdHelper = NsdHelper(this)
                nsdHelper?.registerService(port, "$hostName's Chat")

                val ips = server?.getAllLocalIpAddresses() ?: emptyList()
                val ipText = if (ips.isNotEmpty()) ips.joinToString(" / ") else "127.0.0.1"
                updateNotification("السيرفر يعمل على $ipText:$port")
            } catch (e: Exception) {
                isRunning = false
                updateNotification("فشل تشغيل السيرفر: ${e.message}")
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            nsdHelper?.unregisterService()
            server?.stop()
        } catch (e: Exception) {
        }
        server = null
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "خدمة سيرفر المحادثة",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LanChat - السيرفر يعمل")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
