package com.lanchat.app.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.*

/**
 * Professional Device and Identity Utilities for LanChat Pro.
 */
object DeviceUtils {

    private const val PREFS_NAME = "lanchat_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_AVATAR = "user_avatar"

    fun getUniqueId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (androidId != null) return androidId
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_USER_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, id).apply()
        }
        return id
    }

    fun getUserName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_NAME, null) ?: Build.MODEL ?: "User"
    }

    fun setUserName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }

    fun getUserAvatar(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_AVATAR, null)
    }

    fun setUserAvatar(context: Context, avatarBase64: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_AVATAR, avatarBase64).apply()
    }

    fun getDeviceModel(): String {
        return "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    }

    fun getUserColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains("user_color")) {
            val color = getRandomColor()
            prefs.edit().putInt("user_color", color).apply()
        }
        return prefs.getInt("user_color", 0xFF6366F1.toInt())
    }

    fun getRandomColor(): Int {
        val colors = listOf(
            0xFF6366F1.toInt(), 0xFF10B981.toInt(), 0xFFF59E0B.toInt(), 
            0xFFEF4444.toInt(), 0xFF8B5CF6.toInt(), 0xFFEC4899.toInt(),
            0xFF06B6D4.toInt(), 0xFF84CC16.toInt()
        )
        return colors.random()
    }
}
