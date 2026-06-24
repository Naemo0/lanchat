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
    private var userName: String = "Me"
    private var serverIp: String = "127.0.0.1"
    private var serverName: String = "Chat Room"
    private var port: Int = ChatServer.DEFAULT_PORT
    private var isInForeground = false
    private var replyMessage: UiMessage? = null

    private val db by lazy { ChatDatabase.getDatabase(this) }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            handleSelectedUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        myUserId = com.lanchat.app.util.DeviceUtils.getUniqueId(this)
        userName = intent.getStringExtra("userName") ?: "Me"
        serverIp = intent.getStringExtra("serverIp") ?: "127.0.0.1"
        serverName = intent.getStringExtra("serverName") ?: "Chat Room"
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
        lifecycleScope.launch {
            db.chatDao().markAsRead(serverIp)
        }
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
    }

    private fun setupRecycler() {
        adapter = MessageAdapter(mutableListOf()) { msg ->
            if (msg.type == ChatMessage.TYPE_FILE) {
                handleFileDownload(msg)
            } else {
                showReplyLayout(msg)
            }
        }
        binding.recyclerMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recyclerMessages.adapter = adapter
    }

    private fun handleFileDownload(msg: UiMessage) {
        val data = msg.fileData ?: return
        val name = msg.fileName ?: "downloaded_file"
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Download File")
            .setMessage("Do you want to download $name?")
            .setPositiveButton("Download") { _, _ ->
                try {
                    val path = com.lanchat.app.util.FileUtils.saveBase64ToFile(this, data, name)
                    if (path != null) {
                        Toast.makeText(this, "File saved to: $path", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        binding.btnAttach.setOnClickListener { pickFileLauncher.launch("*/*") }
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCancelReply.setOnClickListener { hideReplyLayout() }
        binding.btnInfo.setOnClickListener { showRoomInfo() }
        
        var isRecording = false
        val audioFile = java.io.File(cacheDir, "voice_msg.mp4")
        
        val recordPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // Start recording logic moved here if needed or just handled in click
            } else {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnVoice.setOnClickListener {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                recordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                return@setOnClickListener
            }

            try {
                if (!isRecording) {
                    com.lanchat.app.util.AudioUtils.startRecording(this, audioFile)
                    binding.btnVoice.setColorFilter(getColor(R.color.secondary))
                    Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show()
                    isRecording = true
                } else {
                    com.lanchat.app.util.AudioUtils.stopRecording()
                    binding.btnVoice.clearColorFilter()
                    val base64 = com.lanchat.app.util.AudioUtils.encodeAudioFile(audioFile)
                    if (base64 != null) {
                        val messageId = UUID.randomUUID().toString()
                        saveMessageLocally(messageId, "Voice message", System.currentTimeMillis(), ChatMessage.TYPE_VOICE, null, true, "Me", myUserId, base64)
                        client?.sendVoice(base64, messageId)
                    }
                    isRecording = false
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Audio Error: ${e.message}", Toast.LENGTH_SHORT).show()
                isRecording = false
            }
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
                    UiMessage(
                        id = it.id,
                        sender = it.sender,
                        senderId = it.senderId,
                        text = it.text,
                        timestamp = it.timestamp,
                        isMine = it.isMine,
                        type = it.type,
                        imageData = it.imageData,
                        fileData = it.fileData,
                        fileName = it.fileName,
                        voiceData = it.voiceData,
                        replyToId = it.replyToId,
                        replyToText = it.replyToText,
                        replyToSender = it.replyToSender,
                        status = it.status,
                        avatar = it.avatar
                    )
                }
                adapter.setMessages(uiMessages)
                binding.recyclerMessages.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    private fun showRoomInfo() {
        val info = """
            Room Name: $serverName
            IP Address: $serverIp
            Port: $port
        """.trimIndent()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Room Info")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun connectToServer() {
        binding.tvStatus.text = "Connecting..."
        val pass = intent.getStringExtra("password")
        client = ChatClient(serverIp, port, userName, myUserId, this, pass)
        client?.connect()
    }

    private fun sendCurrentMessage() {
        val text = binding.etMessage.text?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        saveMessageLocally(
            id = messageId,
            text = text,
            ts = timestamp,
            type = ChatMessage.TYPE_MESSAGE,
            replyToId = replyMessage?.id,
            replyToText = replyMessage?.text ?: "",
            replyToSender = replyMessage?.sender ?: ""
        )
        
        client?.sendMessage(
            text = text,
            id = messageId,
            replyToId = replyMessage?.id,
            replyToText = replyMessage?.text,
            replyToSender = replyMessage?.sender
        )
        
        binding.etMessage.setText("")
        hideReplyLayout()
    }

    private fun handleSelectedUri(uri: Uri) {
        val type = contentResolver.getType(uri)
        if (type?.startsWith("image/") == true) {
            sendImageFromUri(uri)
        } else {
            sendFileFromUri(uri)
        }
    }

    private fun sendImageFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val base64 = ImageUtils.encodeImageFromUri(this@ChatActivity, uri)
                if (base64 != null) {
                    val messageId = UUID.randomUUID().toString()
                    saveMessageLocally(messageId, "Sent a photo", System.currentTimeMillis(), ChatMessage.TYPE_IMAGE, base64)
                    client?.sendImage(base64, messageId)
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Image Error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendFileFromUri(uri: Uri) {
        try {
            val name = com.lanchat.app.util.ImageUtils.getFileName(this, uri) ?: "file"
            val base64 = com.lanchat.app.util.FileUtils.getBase64FromUri(this, uri)
            if (base64 != null) {
                val messageId = UUID.randomUUID().toString()
                saveMessageLocally(
                    id = messageId, 
                    text = "Sent a file: $name", 
                    ts = System.currentTimeMillis(), 
                    type = ChatMessage.TYPE_FILE, 
                    imgData = null, 
                    isMine = true, 
                    sender = "Me", 
                    senderId = myUserId, 
                    voiceData = null, 
                    replyToId = null, 
                    replyToText = null, 
                    replyToSender = null, 
                    fileName = name, 
                    fileData = base64
                )
                client?.sendFile(name, base64, messageId)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "File Error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveMessageLocally(
        id: String,
        text: String,
        ts: Long,
        type: String,
        imgData: String? = null,
        isMine: Boolean = true,
        sender: String = "Me",
        senderId: String = if(isMine) myUserId else "other",
        voiceData: String? = null,
        replyToId: String? = null,
        replyToText: String? = null,
        replyToSender: String? = null,
        fileName: String? = null,
        fileData: String? = null
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
                type = type,
                imageData = imgData,
                voiceData = voiceData,
                replyToId = replyToId,
                replyToText = replyToText,
                replyToSender = replyToSender,
                fileName = fileName,
                fileData = fileData,
                status = if(isMine) MessageStatus.SENDING else MessageStatus.SENT
            )
            db.chatDao().insertMessage(entity)
            
            val pass = intent.getStringExtra("password")
            val conv = ConversationEntity(
                id = serverIp,
                name = serverName,
                lastMessage = text,
                lastTimestamp = ts,
                unreadCount = 0,
                port = port,
                password = pass
            )
            db.chatDao().insertOrUpdateConversation(conv)
        }
    }

    // ===== ChatClient.ClientListener =====

    override fun onConnected() {
        runOnUiThread {
            binding.tvStatus.text = "Connected"
            binding.statusDot.background.setTint(getColor(R.color.online_green))
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            binding.tvStatus.text = "Disconnected"
            binding.statusDot.background.setTint(getColor(R.color.slate_500))
        }
    }

    override fun onMessage(json: JSONObject) {
        val type = json.optString("type")
        val senderId = json.optString("senderId")
        val isMine = senderId == myUserId

        runOnUiThread {
            when (type) {
                ChatMessage.TYPE_MESSAGE, ChatMessage.TYPE_IMAGE, ChatMessage.TYPE_FILE, ChatMessage.TYPE_VOICE -> {
                    if (!isMine) {
                        val msgId = json.optString("id")
                        val sender = json.optString("sender")
                        val text = json.optString("text")
                        val ts = json.optLong("timestamp")
                        
                        saveMessageLocally(
                            id = msgId,
                            text = text,
                            ts = ts,
                            type = type,
                            imgData = json.optString("imageData", null),
                            isMine = false,
                            sender = sender,
                            senderId = senderId,
                            voiceData = json.optString("voiceData", null),
                            replyToId = json.optString("replyToId", null),
                            replyToText = json.optString("replyToText", null),
                            replyToSender = json.optString("replyToSender", null),
                            fileName = json.optString("fileName", null),
                            fileData = json.optString("fileData", null)
                        )
                        
                        if (!isInForeground) {
                            MessageNotifier.show(this, sender, text, serverIp, serverName)
                            lifecycleScope.launch {
                                db.chatDao().incrementUnreadCount(serverIp)
                            }
                        }
                        
                        client?.sendStatusUpdate(msgId, MessageStatus.DELIVERED)
                        if (isInForeground) {
                            client?.sendStatusUpdate(msgId, MessageStatus.SEEN)
                        }
                    } else {
                        // Update status to SENT for my own message
                        val msgId = json.optString("id")
                        lifecycleScope.launch {
                            db.chatDao().updateMessageStatus(msgId, MessageStatus.SENT)
                        }
                    }
                }
                ChatMessage.TYPE_TYPING -> {
                    if (!isMine) {
                        val isTyping = json.optBoolean("isTyping")
                        binding.tvTyping.visibility = if (isTyping) View.VISIBLE else View.GONE
                    }
                }
                ChatMessage.TYPE_STATUS_UPDATE -> {
                    val msgId = json.optString("messageId")
                    val status = json.optInt("status")
                    lifecycleScope.launch {
                        db.chatDao().updateMessageStatus(msgId, status)
                    }
                }
                "error" -> {
                    val msg = json.optString("message")
                    Toast.makeText(this@ChatActivity, msg, Toast.LENGTH_LONG).show()
                    if (msg.contains("password")) finish()
                }
            }
        }
    }

    override fun onError(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        super.onDestroy()
        client?.close()
    }
}
