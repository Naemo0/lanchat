package com.lanchat.app.data

/**
 * نموذج موحّد للرسالة المتبادلة بين الأجهزة عبر JSON.
 */
data class ChatMessage(
    val type: String,          // "message" | "system" | "userlist" | "hello"
    val id: String,            // معرف فريد للرسالة
    val sender: String,        // اسم المرسل
    val senderId: String,      // معرف فريد للجهاز المرسل
    val text: String,          // نص الرسالة
    val timestamp: Long        // وقت الإرسال
) {
    companion object {
        const val TYPE_MESSAGE = "message"
        const val TYPE_SYSTEM = "system"
        const val TYPE_USERLIST = "userlist"
        const val TYPE_HELLO = "hello"
    }
}

/**
 * عنصر يمثل رسالة في الواجهة (إضافة حالة "أنا" لتحديد جهة العرض)
 */
data class UiMessage(
    val id: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val isMine: Boolean,
    val isSystem: Boolean = false
)

/**
 * عضو متصل في الشبكة
 */
data class ConnectedUser(
    val id: String,
    val name: String
)
