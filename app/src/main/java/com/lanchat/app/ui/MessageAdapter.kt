package com.lanchat.app.ui

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lanchat.app.ImageViewerActivity
import com.lanchat.app.R
import com.lanchat.app.data.UiMessage
import com.lanchat.app.util.ImageUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(private val messages: MutableList<UiMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ME = 0
        private const val TYPE_OTHER = 1
        private const val TYPE_SYSTEM = 2
        private const val TYPE_IMAGE_ME = 3
        private const val TYPE_IMAGE_OTHER = 4
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when {
            msg.isSystem -> TYPE_SYSTEM
            msg.isImage && msg.isMine -> TYPE_IMAGE_ME
            msg.isImage -> TYPE_IMAGE_OTHER
            msg.isMine -> TYPE_ME
            else -> TYPE_OTHER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ME -> MeViewHolder(inflater.inflate(R.layout.item_message_me, parent, false))
            TYPE_OTHER -> OtherViewHolder(inflater.inflate(R.layout.item_message_other, parent, false))
            TYPE_IMAGE_ME -> ImageMeViewHolder(inflater.inflate(R.layout.item_image_me, parent, false))
            TYPE_IMAGE_OTHER -> ImageOtherViewHolder(inflater.inflate(R.layout.item_image_other, parent, false))
            else -> SystemViewHolder(inflater.inflate(R.layout.item_message_system, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        val timeStr = timeFormat.format(Date(msg.timestamp))
        when (holder) {
            is MeViewHolder -> {
                holder.text.text = msg.text
                holder.time.text = timeStr
            }
            is OtherViewHolder -> {
                holder.name.text = msg.sender
                holder.text.text = msg.text
                holder.time.text = timeStr
            }
            is SystemViewHolder -> {
                holder.text.text = msg.text
            }
            is ImageMeViewHolder -> {
                bindImage(holder.image, holder.text, holder.time, msg, timeStr)
            }
            is ImageOtherViewHolder -> {
                holder.name.text = msg.sender
                bindImage(holder.image, holder.text, holder.time, msg, timeStr)
            }
        }
    }

    private fun bindImage(image: ImageView, text: TextView, time: TextView, msg: UiMessage, timeStr: String) {
        time.text = timeStr
        if (msg.text.isNotEmpty()) {
            text.text = msg.text
            text.visibility = View.VISIBLE
        } else {
            text.visibility = View.GONE
        }

        val data = msg.imageData
        if (data != null) {
            val bitmap = ImageUtils.decodeBase64ToBitmap(data)
            if (bitmap != null) {
                image.setImageBitmap(bitmap)
                image.setOnClickListener {
                    val context = image.context
                    val intent = Intent(context, ImageViewerActivity::class.java)
                    intent.putExtra(ImageViewerActivity.EXTRA_IMAGE_BASE64, data)
                    context.startActivity(intent)
                }
            } else {
                image.setImageDrawable(null)
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(msg: UiMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    class MeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvMessageText)
        val time: TextView = view.findViewById(R.id.tvTime)
    }

    class OtherViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvSenderName)
        val text: TextView = view.findViewById(R.id.tvMessageText)
        val time: TextView = view.findViewById(R.id.tvTime)
    }

    class SystemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvSystemText)
    }

    class ImageMeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.ivMessageImage)
        val text: TextView = view.findViewById(R.id.tvMessageText)
        val time: TextView = view.findViewById(R.id.tvTime)
    }

    class ImageOtherViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvSenderName)
        val image: ImageView = view.findViewById(R.id.ivMessageImage)
        val text: TextView = view.findViewById(R.id.tvMessageText)
        val time: TextView = view.findViewById(R.id.tvTime)
    }
}
