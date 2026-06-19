package com.lanchat.app.data

/**
 * نموذج موحّد للرسالة المتبادلة بين الأجهزة عبر JSON.
 */
data class ChatMessage(
    val type: String,          // "message" | "system" | "userlist" | "hello" | "image"
    val id: String,            // معرف فريد للرسالة
    val sender: String,        // اسم المرسل
    val senderId: String,      // معرف فريد للجهاز المرسل
    val text: String,          // نص الرسالة
    val timestamp: Long,       // وقت الإرسال
    val imageData: String? = null // بيانات الصورة بترميز Base64 (لرسائل الصور فقط)
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
    val imageData: String? = null, // Base64 لبيانات الصورة
    var status: Int = 0 // 0: Sent, 1: Delivered, 2: Seen
)

/**
 * عضو متصل في الشبكة
 */
data class ConnectedUser(
    val id: String,
    val name: String
)
