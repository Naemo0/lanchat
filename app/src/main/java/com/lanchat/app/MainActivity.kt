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
        val localIp = getLocalIpHint()
        tvLocalIpHint.text = if (localIp != null) {
            "عنوان جهازك الحالي على الشبكة: $localIp"
        } else {
            "تأكد من الاتصال بشبكة Wi-Fi أو تفعيل الهوتسبوت"
        }

        findViewById<android.widget.Button>(R.id.btnStartServer).setOnClickListener {
            val name = userNameOrDefault()
            startServerAndChat(name)
        }

        findViewById<android.widget.Button>(R.id.btnJoinServer).setOnClickListener {
            val name = userNameOrDefault()
            val ip = etServerIp.text?.toString()?.trim()
            if (ip.isNullOrEmpty()) {
                Toast.makeText(this, "أدخل عنوان IP للسيرفر", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            joinChat(name, ip)
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

    private fun joinChat(name: String, ip: String) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("mode", "client")
        intent.putExtra("userName", name)
        intent.putExtra("serverIp", ip)
        intent.putExtra("port", ChatServer.DEFAULT_PORT)
        startActivity(intent)
    }

    /** يحاول الحصول على عنوان IP المحلي للجهاز لإظهاره كدليل للمستخدم */
    private fun getLocalIpHint(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
