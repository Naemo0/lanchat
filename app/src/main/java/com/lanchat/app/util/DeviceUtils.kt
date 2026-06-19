package com.lanchat.app.util

import android.content.Context
import android.provider.Settings
import java.util.*

object DeviceUtils {
    /**
     * يحصل على معرف فريد للجهاز لمنع انتحال الشخصية.
     * يستخدم ANDROID_ID كمعرف ثابت للجهاز.
     */
    fun getUniqueId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return androidId ?: UUID.randomUUID().toString()
    }
}
