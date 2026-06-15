package com.lanchat.app

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.lanchat.app.util.ImageUtils

/**
 * شاشة لعرض الصور المُستقبلة أو المُرسلة في وضع ملء الشاشة.
 */
class ImageViewerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        val ivFullImage = findViewById<ImageView>(R.id.ivFullImage)
        val btnClose = findViewById<ImageButton>(R.id.btnClose)

        val base64Image = intent.getStringExtra(EXTRA_IMAGE_BASE64)
        if (base64Image != null) {
            val bitmap = ImageUtils.decodeBase64ToBitmap(base64Image)
            if (bitmap != null) {
                ivFullImage.setImageBitmap(bitmap)
            }
        }

        btnClose.setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_IMAGE_BASE64 = "image_base64"
    }
}
