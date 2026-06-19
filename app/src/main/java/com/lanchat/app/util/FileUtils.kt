package com.lanchat.app.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

object FileUtils {
    
    fun getBase64FromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            if (bytes != null) {
                Base64.encodeToString(bytes, Base64.DEFAULT)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun saveFileFromBase64(context: Context, base64: String, fileName: String): File? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val dir = File(context.getExternalFilesDir(null), "LanChat")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            file
        } catch (e: Exception) {
            null
        }
    }
}
