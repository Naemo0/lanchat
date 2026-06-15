package com.lanchat.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lanchat.app.R
import com.lanchat.app.data.UiMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(private val messages: MutableList<UiMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ME = 0
        private const val TYPE_OTHER = 1
        private const val TYPE_SYSTEM = 2
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when {
            msg.isSystem -> TYPE_SYSTEM
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
}
