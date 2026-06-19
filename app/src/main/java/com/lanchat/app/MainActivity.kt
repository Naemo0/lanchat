package com.lanchat.app

import android.content.Intent
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.view.View
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
        binding.tvUserDisplayName.text = DeviceUtils.getUserName(this)
    }

    private fun setupUI() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.layoutConversations.visibility = View.VISIBLE
                        binding.layoutDiscovery.visibility = View.GONE
                    }
                    1 -> {
                        binding.layoutConversations.visibility = View.GONE
                        binding.layoutDiscovery.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.fabStartServer.setOnClickListener { showHostDialog() }
        binding.btnJoinManual.setOnClickListener {
            val ip = binding.etServerIp.text.toString()
            if (ip.isNotEmpty()) {
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("serverIp", ip)
                    putExtra("serverName", "Remote Room")
                    putExtra("userName", DeviceUtils.getUserName(this@MainActivity))
                }
                startActivity(intent)
            }
        }
        binding.btnEditProfile.setOnClickListener { showProfileDialog() }
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
        binding.rvConversations.layoutManager = LinearLayoutManager(this)
        binding.rvConversations.adapter = convAdapter

        serverAdapter = ServerAdapter { serviceInfo ->
            showJoinDialog(serviceInfo)
        }
        binding.rvDiscoveredServers.layoutManager = LinearLayoutManager(this)
        binding.rvDiscoveredServers.adapter = serverAdapter
    }

    private fun observeConversations() {
        lifecycleScope.launch {
            db.chatDao().getAllConversations().collectLatest { list ->
                convAdapter.submitList(list)
                binding.layoutEmptyChats.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun startDiscovery() {
        nsdHelper = NsdHelper(this)
        nsdHelper?.discoverServices(object : NsdHelper.DiscoveryListener {
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                runOnUiThread { 
                    serverAdapter.addServer(serviceInfo)
                    binding.layoutSearching.visibility = View.GONE
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                runOnUiThread { 
                    serverAdapter.removeServer(serviceInfo)
                    if (serverAdapter.itemCount == 0) {
                        binding.layoutSearching.visibility = View.VISIBLE
                    }
                }
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
                    binding.tvUserDisplayName.text = newName
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
