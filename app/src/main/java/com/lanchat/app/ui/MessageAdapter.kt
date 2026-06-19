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
import com.lanchat.app.data.MessageEntity
import com.lanchat.app.data.UiMessage
import com.lanchat.app.util.AudioUtils
import com.lanchat.app.util.ImageUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val messages: MutableList<UiMessage>,
    private val onMessageLongClick: (UiMessage) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ME = 0
        private const val TYPE_OTHER = 1
        private const val TYPE_SYSTEM = 2
        private const val TYPE_IMAGE_ME = 3
        private const val TYPE_IMAGE_OTHER = 4
        private const val TYPE_FILE_ME = 5
        private const val TYPE_FILE_OTHER = 6
        private const val TYPE_VOICE_ME = 7
        private const val TYPE_VOICE_OTHER = 8
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when {
            msg.isSystem -> TYPE_SYSTEM
            msg.isImage && msg.isMine -> TYPE_IMAGE_ME
            msg.isImage -> TYPE_IMAGE_OTHER
            msg.isVoice && msg.isMine -> TYPE_VOICE_ME
            msg.isVoice -> TYPE_VOICE_OTHER
            msg.isFile && msg.isMine -> TYPE_FILE_ME
            msg.isFile -> TYPE_FILE_OTHER
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
            TYPE_FILE_ME, TYPE_FILE_OTHER, TYPE_VOICE_ME, TYPE_VOICE_OTHER -> 
                FileViewHolder(inflater.inflate(R.layout.item_message_other, parent, false)) // Reuse or create specific
            else -> SystemViewHolder(inflater.inflate(R.layout.item_message_system, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        val timeStr = timeFormat.format(Date(msg.timestamp))
        
        holder.itemView.setOnLongClickListener {
            onMessageLongClick(msg)
            true
        }

        when (holder) {
            is MeViewHolder -> {
                holder.text.text = msg.text
                holder.time.text = timeStr
                setStatus(holder.status, msg.status)
                bindReply(holder.replyLayout, holder.replyText, msg)
            }
            is OtherViewHolder -> {
                holder.name.text = msg.sender
                holder.text.text = msg.text
                holder.time.text = timeStr
                bindReply(holder.replyLayout, holder.replyText, msg)
            }
            is SystemViewHolder -> {
                holder.text.text = msg.text
            }
            is ImageMeViewHolder -> {
                bindImage(holder.image, holder.text, holder.time, msg, timeStr)
                setStatus(holder.status, msg.status)
            }
            is ImageOtherViewHolder -> {
                holder.name.text = msg.sender
                bindImage(holder.image, holder.text, holder.time, msg, timeStr)
            }
            is FileViewHolder -> {
                // Simplified for now, just show as text with an icon
                val prefix = if (msg.isVoice) "🎤 رسالة صوتية" else "📁 ملف: ${msg.fileName}"
                holder.text.text = prefix
                holder.itemView.setOnClickListener {
                    if (msg.isVoice && msg.imageData != null) {
                        AudioUtils.playAudio(msg.imageData, holder.itemView.context)
                    }
                }
            }
        }
    }

    private fun bindReply(layout: View, textView: TextView, msg: UiMessage) {
        if (msg.replyToId != null) {
            layout.visibility = View.VISIBLE
            textView.text = msg.replyToText ?: "رسالة"
        } else {
            layout.visibility = View.GONE
        }
    }

    private fun setStatus(textView: TextView, status: Int) {
        when (status) {
            MessageEntity.STATUS_SENT -> {
                textView.text = "✓"
                textView.alpha = 0.6f
            }
            MessageEntity.STATUS_DELIVERED -> {
                textView.text = "✓✓"
                textView.alpha = 0.6f
            }
            MessageEntity.STATUS_SEEN -> {
                textView.text = "✓✓"
                textView.setTextColor(textView.context.getColor(R.color.accent))
                textView.alpha = 1.0f
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
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    fun setMessages(newMessages: List<UiMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    class MeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvMessageText)
        val time: TextView = view.findViewById(R.id.tvTime)
        val status: TextView = view.findViewById(R.id.tvStatus)
        val replyLayout: View = view.findViewById(R.id.layoutReplyPreview)
        val replyText: TextView = view.findViewById(R.id.tvReplyPreviewText)
    }

    class OtherViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvSenderName)
        val text: TextView = view.findViewById(R.id.tvMessageText)
        val time: TextView = view.findViewById(R.id.tvTime)
        val replyLayout: View = view.findViewById(R.id.layoutReplyPreview)
        val replyText: TextView = view.findViewById(R.id.tvReplyPreviewText)
    }

    class SystemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvSystemText)
    }

    class ImageMeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.ivMessageImage)
        val text: TextView = view.findViewById(R.id.tvMessageText)
        val time: TextView = view.findViewById(R.id.tvTime)
        val status: TextView = view.findViewById(R.id.tvStatus)
    }

    class ImageOtherViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvSenderName)
        val image: ImageView = view.findViewById(R.id.ivMessageImage)
        val text: TextView = view.findViewById(R.id.tvMessageText)
        val time: TextView = view.findViewById(R.id.tvTime)
    }

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvMessageText)
    }
}
