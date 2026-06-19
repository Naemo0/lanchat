package com.lanchat.app.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Message Status constants
 */
object MessageStatus {
    const val SENDING = 0
    const val SENT = 1
    const val DELIVERED = 2
    const val SEEN = 3
}

/**
 * Unified message model for JSON exchange.
 */
data class ChatMessage(
    val type: String,          // "message", "system", "userlist", "hello", "image", "file", "voice", "typing", "status_update"
    val id: String,
    val sender: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val imageData: String? = null,
    val fileData: String? = null,
    val fileName: String? = null,
    val voiceData: String? = null,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    val status: Int = MessageStatus.SENT,
    val avatar: String? = null
) {
    companion object {
        const val TYPE_MESSAGE = "message"
        const val TYPE_SYSTEM = "system"
        const val TYPE_USERLIST = "userlist"
        const val TYPE_HELLO = "hello"
        const val TYPE_IMAGE = "image"
        const val TYPE_FILE = "file"
        const val TYPE_VOICE = "voice"
        const val TYPE_TYPING = "typing"
        const val TYPE_STATUS_UPDATE = "status_update"
    }
}

/**
 * UI Message model with display-specific logic
 */
data class UiMessage(
    val id: String,
    val sender: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val isMine: Boolean,
    val type: String = ChatMessage.TYPE_MESSAGE,
    val imageData: String? = null,
    val fileData: String? = null,
    val fileName: String? = null,
    val voiceData: String? = null,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    var status: Int = MessageStatus.SENT,
    val avatar: String? = null
)

/**
 * Connected User model
 */
@Parcelize
data class ConnectedUser(
    val id: String, // Unique Hardware ID (UUID)
    val name: String,
    val avatar: String? = null,
    val isOnline: Boolean = true,
    val isTyping: Boolean = false
) : Parcelable

/**
 * Conversation / Chat Room model for persistence
 */
data class Conversation(
    val id: String, // Server IP or unique ID
    val name: String,
    val lastMessage: String,
    val timestamp: Long,
    val unreadCount: Int = 0,
    val isServer: Boolean = false
)
