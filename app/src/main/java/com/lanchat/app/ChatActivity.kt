package com.lanchat.app

import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lanchat.app.data.UiMessage
import com.lanchat.app.network.ChatClient
import com.lanchat.app.network.ChatServer
import com.lanchat.app.network.ChatServerService
import com.lanchat.app.ui.MessageAdapter
import org.json.JSONObject
import java.util.UUID

class ChatActivity : AppCompatActivity(), ChatClient.ClientListener {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: MessageAdapter
    private lateinit var etMessage: android.widget.EditText
    private lateinit var btnSend: ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var tvChatTitle: TextView
    private lateinit var statusDot: View
    private lateinit var usersScroll: HorizontalScrollView
    private lateinit var usersContainer: LinearLayout
    private lateinit var btnUsers: ImageButton
    private lateinit var btnBack: ImageButton

    private var client: ChatClient? = null
    private val myUserId = UUID.randomUUID().toString()
    private var userName: String = "أنا"
    private var mode: String = "client"
    private var serverIp: String = "127.0.0.1"
    private var port: Int = ChatServer.DEFAULT_PORT
    private var usersVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        userName = intent.getStringExtra("userName") ?: "أنا"
        mode = intent.getStringExtra("mode") ?: "client"
        serverIp = intent.getStringExtra("serverIp") ?: "127.0.0.1"
        port = intent.getIntExtra("port", ChatServer.DEFAULT_PORT)

        bindViews()
        setupRecycler()
        setupListeners()

        tvChatTitle.text = if (mode == "host") "محادثة (أنت المستضيف)" else "محادثة الشبكة المحلية"

        connectToServer()
    }

    private fun bindViews() {
        recycler = findViewById(R.id.recyclerMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        tvStatus = findViewById(R.id.tvStatus)
        tvChatTitle = findViewById(R.id.tvChatTitle)
        statusDot = findViewById(R.id.statusDot)
        usersScroll = findViewById(R.id.usersScroll)
        usersContainer = findViewById(R.id.usersContainer)
        btnUsers = findViewById(R.id.btnUsers)
        btnBack = findViewById(R.id.btnBack)
    }

    private fun setupRecycler() {
        adapter = MessageAdapter(mutableListOf())
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter
    }

    private fun setupListeners() {
        btnSend.setOnClickListener { sendCurrentMessage() }

        etMessage.setOnEditorActionListener { _, _, _ ->
            sendCurrentMessage()
            true
        }

        btnUsers.setOnClickListener {
            usersVisible = !usersVisible
            usersScroll.visibility = if (usersVisible) View.VISIBLE else View.GONE
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun connectToServer() {
        tvStatus.text = getString(R.string.connecting)
        setStatusColor(false)

        client = ChatClient(serverIp, port, userName, myUserId, this)
        client?.connect()
    }

    private fun sendCurrentMessage() {
        val text = etMessage.text?.toString()?.trim()
        if (text.isNullOrEmpty()) return

        if (client?.isOpen() != true) {
            Toast.makeText(this, "غير متصل بالسيرفر", Toast.LENGTH_SHORT).show()
            return
        }

        client?.sendMessage(text)
        etMessage.setText("")
    }

    // ===== ChatClient.ClientListener =====

    override fun onConnected() {
        runOnUiThread {
            tvStatus.text = getString(R.string.connected)
            setStatusColor(true)
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            tvStatus.text = getString(R.string.not_connected)
            setStatusColor(false)
        }
    }

    override fun onMessage(json: JSONObject) {
        runOnUiThread {
            when (json.optString("type")) {
                ChatMessageType.MESSAGE -> {
                    val senderId = json.optString("senderId")
                    val msg = UiMessage(
                        id = json.optString("id"),
                        sender = json.optString("sender"),
                        text = json.optString("text"),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                        isMine = senderId == myUserId,
                        isSystem = false
                    )
                    adapter.addMessage(msg)
                    recycler.scrollToPosition(adapter.itemCount - 1)
                }
                ChatMessageType.SYSTEM -> {
                    val msg = UiMessage(
                        id = json.optString("id"),
                        sender = "النظام",
                        text = json.optString("text"),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                        isMine = false,
                        isSystem = true
                    )
                    adapter.addMessage(msg)
                    recycler.scrollToPosition(adapter.itemCount - 1)
                }
                ChatMessageType.USERLIST -> {
                    val users = json.optJSONArray("users")
                    renderUserChips(users)
                }
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            tvStatus.text = getString(R.string.not_connected)
            setStatusColor(false)
        }
    }

    private fun renderUserChips(users: org.json.JSONArray?) {
        usersContainer.removeAllViews()
        if (users == null) return
        for (i in 0 until users.length()) {
            val u = users.getJSONObject(i)
            val name = u.optString("name")
            val chip = layoutInflater.inflate(R.layout.item_user_chip, usersContainer, false)
            chip.findViewById<TextView>(R.id.tvUserChipName).text = name
            usersContainer.addView(chip)
        }
        btnUsers.alpha = if (users.length() > 0) 1f else 0.6f
    }

    private fun setStatusColor(connected: Boolean) {
        val color = if (connected) {
            getColor(R.color.online_green)
        } else {
            getColor(R.color.text_secondary)
        }
        statusDot.background.setTint(color)
    }

    override fun onDestroy() {
        super.onDestroy()
        client?.close()
        // إذا كنا المستضيف، السيرفر يستمر بالعمل عبر الخدمة (Foreground Service)
        // حتى يبقى متاحاً لباقي الأجهزة. يمكن إيقافه يدوياً من الإشعار إن لزم.
    }

    private object ChatMessageType {
        const val MESSAGE = "message"
        const val SYSTEM = "system"
        const val USERLIST = "userlist"
    }
}
