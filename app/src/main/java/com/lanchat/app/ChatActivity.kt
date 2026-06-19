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
    private lateinit var myUserId: String
    private var userName: String = "أنا"
    private var serverIp: String = "127.0.0.1"
    private var serverName: String = "محادثة"
    private var port: Int = ChatServer.DEFAULT_PORT
    private var isInForeground = false
    private var replyMessage: UiMessage? = null

    private val db by lazy { ChatDatabase.getDatabase(this) }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val type = contentResolver.getType(uri)
            if (type?.startsWith("image/") == true) {
                sendImageFromUri(uri)
            } else {
                sendFileFromUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        myUserId = com.lanchat.app.util.DeviceUtils.getUniqueId(this)
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
        adapter = MessageAdapter(mutableListOf()) { msg ->
            showReplyLayout(msg)
        }
        binding.recyclerMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recyclerMessages.adapter = adapter
    }

    private fun showReplyLayout(msg: UiMessage) {
        replyMessage = msg
        binding.layoutReply.visibility = View.VISIBLE
        binding.tvReplyName.text = msg.sender
        binding.tvReplyText.text = msg.text
    }

    private fun hideReplyLayout() {
        replyMessage = null
        binding.layoutReply.visibility = View.GONE
    }

    private fun setupListeners() {
        binding.btnSend.setOnClickListener { sendCurrentMessage() }
        binding.btnAttach.setOnClickListener { pickImageLauncher.launch("*/*") }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCancelReply.setOnClickListener { hideReplyLayout() }
        
        var isRecording = false
        val audioFile = java.io.File(cacheDir, "voice_msg.mp4")
        binding.btnVoice.setOnClickListener {
            if (!isRecording) {
                com.lanchat.app.util.AudioUtils.startRecording(this, audioFile)
                binding.btnVoice.setColorFilter(getColor(R.color.bubble_me))
                Toast.makeText(this, "جاري التسجيل...", Toast.LENGTH_SHORT).show()
                isRecording = true
            } else {
                com.lanchat.app.util.AudioUtils.stopRecording()
                binding.btnVoice.clearColorFilter()
                val base64 = com.lanchat.app.util.AudioUtils.encodeAudioFile(audioFile)
                client?.sendVoice(base64)
                saveMessageLocally(UUID.randomUUID().toString(), "[رسالة صوتية]", System.currentTimeMillis(), false, base64, true, "أنا", myUserId, null, null)
                isRecording = false
            }
        }

        binding.btnUsers.setOnClickListener {
            val visible = binding.usersScroll.visibility == View.VISIBLE
            binding.usersScroll.visibility = if (visible) View.GONE else View.VISIBLE
        }

        binding.etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                client?.sendTypingStatus(!s.isNullOrEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
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
        saveMessageLocally(
            id = messageId,
            text = text,
            ts = timestamp,
            isImg = false,
            replyToId = replyMessage?.id,
            replyToText = replyMessage?.text
        )
        
        client?.sendMessage(
            text = text,
            id = messageId,
            replyToId = replyMessage?.id,
            replyToText = replyMessage?.text
        )
        
        binding.etMessage.setText("")
        hideReplyLayout()
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

    private fun sendFileFromUri(uri: Uri) {
        val name = com.lanchat.app.util.ImageUtils.getFileName(this, uri) ?: "file"
        val base64 = com.lanchat.app.util.FileUtils.getBase64FromUri(this, uri)
        if (base64 != null) {
            client?.sendFile(name, base64)
            saveMessageLocally(UUID.randomUUID().toString(), "[ملف: $name]", System.currentTimeMillis(), false, null, true, "أنا", myUserId, null, null)
        }
    }

    private fun saveMessageLocally(
        id: String,
        text: String,
        ts: Long,
        isImg: Boolean,
        imgData: String? = null,
        isMine: Boolean = true,
        sender: String = "أنا",
        senderId: String = if(isMine) myUserId else "other",
        replyToId: String? = null,
        replyToText: String? = null
    ) {
        lifecycleScope.launch {
            val entity = MessageEntity(
                id = id,
                serverId = serverIp,
                sender = sender,
                senderId = senderId,
                text = text,
                timestamp = ts,
                isMine = isMine,
                isImage = isImg,
                imageData = imgData,
                replyToId = replyToId,
                replyToText = replyToText
            )
            db.chatDao().insertMessage(entity)
            
            val lastText = when {
                isImg -> "[صورة]"
                else -> text
            }
            val conv = ConversationEntity(serverIp, serverName, lastText, ts, 0)
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
                        val rId = json.optString("replyToId", null)
                        val rText = json.optString("replyToText", null)
                        
                        saveMessageLocally(
                            id = msgId,
                            text = text,
                            ts = ts,
                            isImg = isImg,
                            imgData = imgData,
                            isMine = false,
                            sender = sender,
                            senderId = senderId,
                            replyToId = rId,
                            replyToText = rText
                        )
                        
                        if (!isInForeground) {
                            MessageNotifier.show(this, sender, if(isImg) "أرسل صورة" else text)
                        }
                        
                        client?.sendStatusUpdate(msgId, MessageEntity.STATUS_DELIVERED)
                        if (isInForeground) {
                            client?.sendStatusUpdate(msgId, MessageEntity.STATUS_SEEN)
                        }
                    }
                }
                "typing" -> {
                    if (!isMine) {
                        val isTyping = json.optBoolean("isTyping")
                        binding.tvTyping.visibility = if (isTyping) View.VISIBLE else View.GONE
                    }
                }
                "file", "voice" -> {
                    if (!isMine) {
                        val msgId = json.optString("id")
                        val sender = json.optString("sender")
                        val text = json.optString("text")
                        val ts = json.optLong("timestamp")
                        val fName = json.optString("fileName", null)
                        val fData = json.optString("fileData", null)
                        
                        saveMessageLocally(
                            id = msgId,
                            text = text,
                            ts = ts,
                            isImg = false,
                            imgData = fData,
                            isMine = false,
                            sender = sender,
                            senderId = senderId,
                            replyToId = json.optString("replyToId", null),
                            replyToText = json.optString("replyToText", null)
                        )
                    }
                }
                "error" -> {
                    val msg = json.optString("message")
                    Toast.makeText(this@ChatActivity, msg, Toast.LENGTH_LONG).show()
                    if (msg.contains("كلمة السر")) finish()
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
