package com.lanchat.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.lanchat.app.network.ChatServer
import com.lanchat.app.network.ChatServerService

class MainActivity : AppCompatActivity() {

    private lateinit var etUserName: TextInputEditText
    private lateinit var etServerIp: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUserName = findViewById(R.id.etUserName)
        etServerIp = findViewById(R.id.etServerIp)

        val tvLocalIpHint = findViewById<android.widget.TextView>(R.id.tvLocalIpHint)
        val localIps = getAllLocalIpHints()
        tvLocalIpHint.text = if (localIps.isNotEmpty()) {
            "عناوين جهازك على الشبكة:\n" + localIps.joinToString("\n")
        } else {
            "تأكد من الاتصال بشبكة Wi-Fi أو تفعيل الهوتسبوت"
        }

        findViewById<android.widget.Button>(R.id.btnStartServer).setOnClickListener {
            val name = userNameOrDefault()
            startServerAndChat(name)
        }

        findViewById<android.widget.Button>(R.id.btnJoinServer).setOnClickListener {
            val name = userNameOrDefault()
            val ipInput = etServerIp.text?.toString()?.trim()
            if (ipInput.isNullOrEmpty()) {
                Toast.makeText(this, "أدخل عنوان IP للسيرفر", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // يدعم إدخال "IP" أو "IP:PORT" (مفيد عند استخدام بورت مخصص عبر راوترات متعددة)
            val parts = ipInput.split(":")
            val ip = parts[0].trim()
            val customPort = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: ChatServer.DEFAULT_PORT
            joinChat(name, ip, customPort)
        }
    }

    private fun userNameOrDefault(): String {
        val name = etUserName.text?.toString()?.trim()
        return if (name.isNullOrEmpty()) "مستخدم${(1000..9999).random()}" else name
    }

    private fun startServerAndChat(name: String) {
        // تشغيل خدمة السيرفر في الخلفية
        val serviceIntent = Intent(this, ChatServerService::class.java)
        serviceIntent.putExtra("hostName", name)
        serviceIntent.putExtra("port", ChatServer.DEFAULT_PORT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // الانتظار قليلاً ليبدأ السيرفر، ثم الانتقال لشاشة المحادثة كعميل محلي
        android.os.Handler(mainLooper).postDelayed({
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("mode", "host")
            intent.putExtra("userName", name)
            intent.putExtra("serverIp", "127.0.0.1")
            intent.putExtra("port", ChatServer.DEFAULT_PORT)
            startActivity(intent)
        }, 400)
    }

    private fun joinChat(name: String, ip: String, port: Int = ChatServer.DEFAULT_PORT) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("mode", "client")
        intent.putExtra("userName", name)
        intent.putExtra("serverIp", ip)
        intent.putExtra("port", port)
        startActivity(intent)
    }

    /**
     * يحاول الحصول على كل عناوين IP المحلية للجهاز (على كل واجهات الشبكة)
     * لإظهارها كدليل للمستخدم. مفيد عند وجود عدة شبكات/راوترات متصلة بالجهاز.
     */
    private fun getAllLocalIpHints(): List<String> {
        val result = mutableListOf<String>()
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                        result.add("${addr.hostAddress} (${iface.displayName})")
                    }
                }
            }
        } catch (e: Exception) {
        }
        return result
    }
}
