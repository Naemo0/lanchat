package com.lanchat.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.lanchat.app.data.ChatDatabase
import com.lanchat.app.data.ConversationEntity
import com.lanchat.app.databinding.ActivityMainBinding
import com.lanchat.app.network.ChatServer
import com.lanchat.app.network.ChatServerService
import com.lanchat.app.network.NsdHelper
import com.lanchat.app.ui.ConversationAdapter
import com.lanchat.app.ui.ServerAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var nsdHelper: NsdHelper
    private lateinit var serverAdapter: ServerAdapter
    private lateinit var convAdapter: ConversationAdapter
    private val db by lazy { ChatDatabase.getDatabase(this) }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermission()
        setupAdapters()
        setupListeners()
        observeData()

        nsdHelper = NsdHelper(this)
        startDiscovery()

        binding.tvLocalIpHint.text = "جهازك: " + getAllLocalIps().joinToString(", ")
    }

    private fun setupAdapters() {
        serverAdapter = ServerAdapter { serviceInfo ->
            val name = userNameOrDefault()
            joinChat(name, serviceInfo.host.hostAddress, serviceInfo.port, serviceInfo.serviceName)
        }
        binding.rvDiscoveredServers.layoutManager = LinearLayoutManager(this)
        binding.rvDiscoveredServers.adapter = serverAdapter

        convAdapter = ConversationAdapter { conv ->
            val name = userNameOrDefault()
            joinChat(name, conv.serverIp, ChatServer.DEFAULT_PORT, conv.serverName)
        }
        binding.rvConversations.layoutManager = LinearLayoutManager(this)
        binding.rvConversations.adapter = convAdapter
    }

    private fun setupListeners() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    binding.layoutConversations.visibility = View.VISIBLE
                    binding.layoutDiscovery.visibility = View.GONE
                } else {
                    binding.layoutConversations.visibility = View.GONE
                    binding.layoutDiscovery.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.fabStartServer.setOnClickListener {
            val name = userNameOrDefault()
            startServerAndChat(name)
        }

        binding.btnJoinManual.setOnClickListener {
            val name = userNameOrDefault()
            val ipInput = binding.etServerIp.text?.toString()?.trim()
            if (ipInput.isNullOrEmpty()) {
                Toast.makeText(this, "أدخل عنوان IP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val parts = ipInput.split(":")
            val ip = parts[0].trim()
            val port = parts.getOrNull(1)?.toIntOrNull() ?: ChatServer.DEFAULT_PORT
            joinChat(name, ip, port, "سيرفر يدوي")
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            db.chatDao().getAllConversations().collectLatest { list ->
                convAdapter.submitList(list)
                binding.tvEmptyConversations.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun startDiscovery() {
        binding.pbDiscovery.visibility = View.VISIBLE
        nsdHelper.discoverServices(object : NsdHelper.DiscoveryListener {
            override fun onServiceFound(serviceInfo: android.net.nsd.NsdServiceInfo) {
                runOnUiThread {
                    serverAdapter.addServer(serviceInfo)
                    binding.pbDiscovery.visibility = View.GONE
                    binding.tvDiscoveryStatus.text = "تم العثور على سيرفرات"
                }
            }

            override fun onServiceLost(serviceInfo: android.net.nsd.NsdServiceInfo) {
                runOnUiThread { serverAdapter.removeServer(serviceInfo) }
            }
        })
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun userNameOrDefault(): String {
        val name = binding.etUserName.text?.toString()?.trim()
        return if (name.isNullOrEmpty()) "مستخدم${(1000..9999).random()}" else name
    }

    private fun startServerAndChat(name: String) {
        val serviceIntent = Intent(this, ChatServerService::class.java)
        serviceIntent.putExtra("hostName", name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        android.os.Handler(mainLooper).postDelayed({
            joinChat(name, "127.0.0.1", ChatServer.DEFAULT_PORT, "سيرفرك المحلي")
        }, 500)
    }

    private fun joinChat(name: String, ip: String, port: Int, serverName: String) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("userName", name)
        intent.putExtra("serverIp", ip)
        intent.putExtra("port", port)
        intent.putExtra("serverName", serverName)
        startActivity(intent)
    }

    private fun getAllLocalIps(): List<String> {
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
                        result.add(addr.hostAddress)
                    }
                }
            }
        } catch (e: Exception) {}
        return result
    }

    override fun onDestroy() {
        super.onDestroy()
        nsdHelper.stopDiscovery()
    }
}
