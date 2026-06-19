package com.lanchat.app

import android.content.Intent
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.lanchat.app.data.ChatDatabase
import com.lanchat.app.databinding.ActivityMainBinding
import com.lanchat.app.network.ChatServerService
import com.lanchat.app.network.NsdHelper
import com.lanchat.app.ui.ConversationAdapter
import com.lanchat.app.ui.ServerAdapter
import com.lanchat.app.util.DeviceUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var convAdapter: ConversationAdapter
    private lateinit var serverAdapter: ServerAdapter
    private var nsdHelper: NsdHelper? = null
    private val db by lazy { ChatDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupAdapters()
        observeConversations()
        startDiscovery()
        
        binding.tvHardwareId.text = "ID: ${DeviceUtils.getUniqueId(this)}"
    }

    private fun setupUI() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.recyclerChats.visibility = View.VISIBLE
                        binding.layoutDiscover.visibility = View.GONE
                    }
                    1 -> {
                        binding.recyclerChats.visibility = View.GONE
                        binding.layoutDiscover.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.btnHost.setOnClickListener { showHostDialog() }
        binding.btnManualConnect.setOnClickListener { showManualConnectDialog() }
        binding.btnProfile.setOnClickListener { showProfileDialog() }
    }

    private fun setupAdapters() {
        convAdapter = ConversationAdapter { conv ->
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("serverIp", conv.id)
                putExtra("serverName", conv.name)
                putExtra("userName", DeviceUtils.getUserName(this@MainActivity))
            }
            startActivity(intent)
        }
        binding.recyclerChats.layoutManager = LinearLayoutManager(this)
        binding.recyclerChats.adapter = convAdapter

        serverAdapter = ServerAdapter { serviceInfo ->
            showJoinDialog(serviceInfo)
        }
        binding.recyclerDiscovered.layoutManager = LinearLayoutManager(this)
        binding.recyclerDiscovered.adapter = serverAdapter
    }

    private fun observeConversations() {
        lifecycleScope.launch {
            db.chatDao().getAllConversations().collectLatest { list ->
                convAdapter.submitList(list)
                binding.tvEmptyChats.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun startDiscovery() {
        nsdHelper = NsdHelper(this)
        nsdHelper?.discoverServices(object : NsdHelper.DiscoveryListener {
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                runOnUiThread { serverAdapter.addServer(serviceInfo) }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                runOnUiThread { serverAdapter.removeServer(serviceInfo) }
            }
        })
    }

    private fun showHostDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_host, null)
        val etName = view.findViewById<TextInputEditText>(R.id.etRoomName)
        val etPass = view.findViewById<TextInputEditText>(R.id.etPassword)
        
        etName.setText("${DeviceUtils.getUserName(this)}'s Room")

        AlertDialog.Builder(this)
            .setTitle("Host a Room")
            .setView(view)
            .setPositiveButton("Start") { _, _ ->
                val name = etName.text.toString()
                val pass = etPass.text.toString()
                val intent = Intent(this, ChatServerService::class.java).apply {
                    putExtra("hostName", name)
                    putExtra("password", pass.ifEmpty { null })
                }
                startService(intent)
                
                // Join own room
                val chatIntent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("serverIp", "127.0.0.1")
                    putExtra("serverName", name)
                    putExtra("userName", DeviceUtils.getUserName(this@MainActivity))
                    putExtra("password", pass.ifEmpty { null })
                }
                startActivity(chatIntent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showJoinDialog(serviceInfo: NsdServiceInfo) {
        val view = layoutInflater.inflate(R.layout.dialog_join, null)
        val etPass = view.findViewById<TextInputEditText>(R.id.etPassword)

        AlertDialog.Builder(this)
            .setTitle("Join ${serviceInfo.serviceName}")
            .setView(view)
            .setPositiveButton("Join") { _, _ ->
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("serverIp", serviceInfo.host?.hostAddress)
                    putExtra("serverName", serviceInfo.serviceName)
                    putExtra("port", serviceInfo.port)
                    putExtra("userName", DeviceUtils.getUserName(this@MainActivity))
                    putExtra("password", etPass.text.toString().ifEmpty { null })
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManualConnectDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_manual_connect, null)
        val etIp = view.findViewById<TextInputEditText>(R.id.etIp)
        val etPass = view.findViewById<TextInputEditText>(R.id.etPassword)

        AlertDialog.Builder(this)
            .setTitle("Manual Connect")
            .setView(view)
            .setPositiveButton("Connect") { _, _ ->
                val ip = etIp.text.toString()
                if (ip.isNotEmpty()) {
                    val intent = Intent(this, ChatActivity::class.java).apply {
                        putExtra("serverIp", ip)
                        putExtra("serverName", "Remote Room")
                        putExtra("userName", DeviceUtils.getUserName(this@MainActivity))
                        putExtra("password", etPass.text.toString().ifEmpty { null })
                    }
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showProfileDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_profile, null)
        val etName = view.findViewById<TextInputEditText>(R.id.etUserName)
        etName.setText(DeviceUtils.getUserName(this))

        AlertDialog.Builder(this)
            .setTitle("My Profile")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString()
                if (newName.isNotEmpty()) {
                    DeviceUtils.setUserName(this, newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        nsdHelper?.stopDiscovery()
    }
}
