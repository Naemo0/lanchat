package com.lanchat.app.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object AudioUtils {
    private var recorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null

    fun startRecording(context: Context, outputFile: File) {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
    }

    fun stopRecording() {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
    }

    fun playAudio(base64: String, context: Context) {
        val tempFile = File(context.cacheDir, "temp_audio.mp4")
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        FileOutputStream(tempFile).use { it.write(bytes) }

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(tempFile.absolutePath)
            prepare()
            start()
        }
    }

    fun encodeAudioFile(file: File): String {
        val bytes = FileInputStream(file).use { it.readBytes() }
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }
}
