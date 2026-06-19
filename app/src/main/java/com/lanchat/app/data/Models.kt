package com.lanchat.app.data

/**
 * نموذج موحّد للرسالة المتبادلة بين الأجهزة عبر JSON.
 */
data class ChatMessage(
    val type: String,          // "message" | "system" | "userlist" | "hello" | "image" | "file" | "voice" | "typing"
    val id: String,
    val sender: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val imageData: String? = null,
    val fileData: String? = null,
    val fileName: String? = null,
    val replyToId: String? = null,
    val replyToText: String? = null
) {
    companion object {
        const val TYPE_MESSAGE = "message"
        const val TYPE_SYSTEM = "system"
        const val TYPE_USERLIST = "userlist"
        const val TYPE_HELLO = "hello"
        const val TYPE_IMAGE = "image"
    }
}

/**
 * عنصر يمثل رسالة في الواجهة (إضافة حالة "أنا" لتحديد جهة العرض)
 */
data class UiMessage(
    val id: String,
    val serverId: String = "",
    val sender: String,
    val text: String,
    val timestamp: Long,
    val isMine: Boolean,
    val isSystem: Boolean = false,
    val isImage: Boolean = false,
    val imageData: String? = null,
    val isFile: Boolean = false,
    val fileName: String? = null,
    val isVoice: Boolean = false,
    val replyToId: String? = null,
    val replyToText: String? = null,
    var status: Int = 0
)

/**
 * عضو متصل في الشبكة
 */
data class ConnectedUser(
    val id: String,
    val name: String
)
