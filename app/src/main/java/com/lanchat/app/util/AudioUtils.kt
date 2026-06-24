package com.lanchat.app.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object AudioUtils {
    private var recorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private const val TAG = "AudioUtils"

    fun startRecording(context: Context, outputFile: File) {
        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Start recording failed", e)
            throw e
        }
    }

    fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stop recording failed", e)
        } finally {
            recorder = null
        }
    }

    fun playAudio(base64: String, context: Context, messageId: String, onComplete: () -> Unit = {}) {
        try {
            // Stop current playback
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null

            val audioFile = File(context.cacheDir, "voice_$messageId.mp4")
            if (!audioFile.exists()) {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                FileOutputStream(audioFile).use { it.write(bytes) }
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                start()
                setOnCompletionListener { 
                    it.release()
                    mediaPlayer = null
                    onComplete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Play audio failed", e)
        }
    }

    fun encodeAudioFile(file: File): String? {
        return try {
            if (!file.exists()) return null
            val bytes = FileInputStream(file).use { it.readBytes() }
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Encode audio failed", e)
            null
        }
    }
    
    fun release() {
        recorder?.release()
        recorder = null
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
