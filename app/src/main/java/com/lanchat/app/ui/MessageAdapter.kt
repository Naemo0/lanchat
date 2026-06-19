package com.lanchat.app.ui

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.lanchat.app.ImageViewerActivity
import com.lanchat.app.R
import com.lanchat.app.data.ChatMessage
import com.lanchat.app.data.MessageStatus
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
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when {
            msg.type == ChatMessage.TYPE_SYSTEM -> TYPE_SYSTEM
            msg.isMine -> TYPE_ME
            else -> TYPE_OTHER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ME -> MeViewHolder(inflater.inflate(R.layout.item_message_me, parent, false))
            TYPE_OTHER -> OtherViewHolder(inflater.inflate(R.layout.item_message_other, parent, false))
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
                bindCommon(holder.text, holder.time, holder.replyLayout, holder.replyName, holder.replyText, msg, timeStr)
                setStatus(holder.status, msg.status)
                bindMedia(holder.image, holder.voiceLayout, msg)
            }
            is OtherViewHolder -> {
                holder.name.text = msg.sender
                bindCommon(holder.text, holder.time, holder.replyLayout, holder.replyName, holder.replyText, msg, timeStr)
                bindMedia(holder.image, holder.voiceLayout, msg)
                // Load avatar if exists
                if (msg.avatar != null) {
                    // Coil.load(msg.avatar)
                }
            }
            is SystemViewHolder -> {
                holder.text.text = msg.text
            }
        }
    }

    private fun bindCommon(text: TextView, time: TextView, replyLayout: View, replyName: TextView, replyText: TextView, msg: UiMessage, timeStr: String) {
        text.text = msg.text
        time.text = timeStr
        
        if (msg.replyToId != null) {
            replyLayout.visibility = View.VISIBLE
            replyName.text = msg.replyToSender ?: "User"
            replyText.text = msg.replyToText ?: "Message"
        } else {
            replyLayout.visibility = View.GONE
        }
    }

    private fun bindMedia(image: ImageView?, voiceLayout: View?, msg: UiMessage) {
        image?.visibility = View.GONE
        voiceLayout?.visibility = View.GONE

        when (msg.type) {
            ChatMessage.TYPE_IMAGE -> {
                image?.visibility = View.VISIBLE
                msg.imageData?.let { data ->
                    val bitmap = ImageUtils.decodeBase64ToBitmap(data)
                    image?.setImageBitmap(bitmap)
                    image?.setOnClickListener {
                        val intent = Intent(it.context, ImageViewerActivity::class.java)
                        intent.putExtra(ImageViewerActivity.EXTRA_IMAGE_BASE64, data)
                        it.context.startActivity(intent)
                    }
                }
            }
            ChatMessage.TYPE_VOICE -> {
                voiceLayout?.visibility = View.VISIBLE
                voiceLayout?.setOnClickListener {
                    msg.voiceData?.let { data ->
                        AudioUtils.playAudio(data, it.context)
                    }
                }
            }
            ChatMessage.TYPE_FILE -> {
                // Show file icon and name in text
            }
        }
    }

    private fun setStatus(textView: TextView, status: Int) {
        when (status) {
            MessageStatus.SENDING -> {
                textView.text = "..."
                textView.alpha = 0.5f
            }
            MessageStatus.SENT -> {
                textView.text = "✓"
                textView.alpha = 0.5f
            }
            MessageStatus.DELIVERED -> {
                textView.text = "✓✓"
                textView.alpha = 0.5f
            }
            MessageStatus.SEEN -> {
                textView.text = "✓✓"
                textView.setTextColor(textView.context.getColor(R.color.secondary))
                textView.alpha = 1.0f
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
        val replyName: TextView = view.findViewById(R.id.tvReplySender)
        val replyText: TextView = view.findViewById(R.id.tvReplyText)
        val image: ImageView? = view.findViewById(R.id.ivMessageImage)
        val voiceLayout: View? = view.findViewById(R.id.layoutVoice)
    }

    class OtherViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvSenderName)
        val text: TextView = view.findViewById(R.id.tvMessageText)
        val time: TextView = view.findViewById(R.id.tvTime)
        val replyLayout: View = view.findViewById(R.id.layoutReplyPreview)
        val replyName: TextView = view.findViewById(R.id.tvReplySender)
        val replyText: TextView = view.findViewById(R.id.tvReplyText)
        val image: ImageView? = view.findViewById(R.id.ivMessageImage)
        val voiceLayout: View? = view.findViewById(R.id.layoutVoice)
        val avatar: ImageView? = view.findViewById(R.id.ivAvatar)
    }

    class SystemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.tvSystemText)
    }
}
