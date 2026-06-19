package com.lanchat.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lanchat.app.data.*
import com.lanchat.app.databinding.ActivityChatBinding
import com.lanchat.app.network.ChatClient
import com.lanchat.app.network.ChatServer
import com.lanchat.app.network.MessageNotifier
import com.lanchat.app.ui.MessageAdapter
import com.lanchat.app.util.ImageUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

class ChatActivity : AppCompatActivity(), ChatClient.ClientListener {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessageAdapter
    private var client: ChatClient? = null
    private val myUserId = UUID.randomUUID().toString()
    private var userName: String = "أنا"
    private var serverIp: String = "127.0.0.1"
    private var serverName: String = "محادثة"
    private var port: Int = ChatServer.DEFAULT_PORT
    private var isInForeground = false

    private val db by lazy { ChatDatabase.getDatabase(this) }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) sendImageFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userName = intent.getStringExtra("userName") ?: "أنا"
        serverIp = intent.getStringExtra("serverIp") ?: "127.0.0.1"
        serverName = intent.getStringExtra("serverName") ?: "محادثة"
        port = intent.getIntExtra("port", ChatServer.DEFAULT_PORT)

        binding.tvChatTitle.text = serverName
        
        setupRecycler()
        setupListeners()
        observeMessages()
        connectToServer()
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
        // Mark all messages as seen when entering the chat
        lifecycleScope.launch {
            // Logic to send "seen" status for all other's messages can be added here
        }
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
    }

    private fun setupRecycler() {
        adapter = MessageAdapter(mutableListOf())
        binding.recyclerMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recyclerMessages.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener { sendCurrentMessage() }
        binding.btnAttach.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnUsers.setOnClickListener {
            val visible = binding.usersScroll.visibility == View.VISIBLE
            binding.usersScroll.visibility = if (visible) View.GONE else View.VISIBLE
        }
    }

    private fun observeMessages() {
        lifecycleScope.launch {
            db.chatDao().getMessagesForServer(serverIp).collectLatest { entities ->
                val uiMessages = entities.map { 
                    UiMessage(it.id, it.serverId, it.sender, it.text, it.timestamp, it.isMine, false, it.isImage, it.imageData, it.status)
                }
                adapter.setMessages(uiMessages)
                binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    private fun connectToServer() {
        binding.tvStatus.text = "جاري الاتصال..."
        client = ChatClient(serverIp, port, userName, myUserId, this)
        client?.connect()
    }

    private fun sendCurrentMessage() {
        val text = binding.etMessage.text?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // Save locally first
        saveMessageLocally(messageId, text, timestamp, false)
        
        client?.sendMessage(text, messageId)
        binding.etMessage.setText("")
    }

    private fun sendImageFromUri(uri: Uri) {
        Thread {
            val base64 = ImageUtils.encodeImageFromUri(this, uri)
            runOnUiThread {
                if (base64 != null) {
                    val messageId = UUID.randomUUID().toString()
                    val timestamp = System.currentTimeMillis()
                    saveMessageLocally(messageId, "[صورة]", timestamp, true, base64)
                    client?.sendImage(base64)
                }
            }
        }.start()
    }

    private fun saveMessageLocally(id: String, text: String, ts: Long, isImg: Boolean, imgData: String? = null, isMine: Boolean = true, sender: String = "أنا") {
        lifecycleScope.launch {
            val entity = MessageEntity(id, serverIp, sender, if(isMine) myUserId else "other", text, ts, isMine, isImg, imgData)
            db.chatDao().insertMessage(entity)
            
            val conv = ConversationEntity(serverIp, serverName, if(isImg) "[صورة]" else text, ts, 0)
            db.chatDao().insertOrUpdateConversation(conv)
        }
    }

    // ===== ChatClient.ClientListener =====

    override fun onConnected() {
        runOnUiThread {
            binding.tvStatus.text = "متصل"
            binding.statusDot.background.setTint(getColor(R.color.online_green))
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            binding.tvStatus.text = "غير متصل"
            binding.statusDot.background.setTint(getColor(R.color.text_secondary))
        }
    }

    override fun onMessage(json: JSONObject) {
        val type = json.optString("type")
        val senderId = json.optString("senderId")
        val isMine = senderId == myUserId

        runOnUiThread {
            when (type) {
                "message", "image" -> {
                    if (!isMine) {
                        val msgId = json.optString("id")
                        val sender = json.optString("sender")
                        val text = json.optString("text")
                        val ts = json.optLong("timestamp")
                        val isImg = type == "image"
                        val imgData = json.optString("image", null)
                        
                        saveMessageLocally(msgId, text, ts, isImg, imgData, false, sender)
                        
                        if (!isInForeground) {
                            MessageNotifier.show(this, sender, if(isImg) "أرسل صورة" else text)
                        }
                        
                        // Send "delivered" status back
                        client?.sendStatusUpdate(msgId, MessageEntity.STATUS_DELIVERED)
                        // If in foreground, send "seen"
                        if (isInForeground) {
                            client?.sendStatusUpdate(msgId, MessageEntity.STATUS_SEEN)
                        }
                    }
                }
                "status_update" -> {
                    val msgId = json.optString("messageId")
                    val status = json.optInt("status")
                    lifecycleScope.launch {
                        db.chatDao().updateMessageStatus(msgId, status)
                    }
                }
                "userlist" -> {
                    val users = json.optJSONArray("users")
                    renderUserChips(users)
                }
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    private fun renderUserChips(users: org.json.JSONArray?) {
        binding.usersContainer.removeAllViews()
        if (users == null) return
        for (i in 0 until users.length()) {
            val u = users.getJSONObject(i)
            val name = u.optString("name")
            val chip = layoutInflater.inflate(R.layout.item_user_chip, binding.usersContainer, false)
            chip.findViewById<android.widget.TextView>(R.id.tvUserChipName).text = name
            binding.usersContainer.addView(chip)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client?.close()
    }
}
