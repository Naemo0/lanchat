package com.lanchat.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lanchat.app.R
import com.lanchat.app.data.ConversationEntity
import java.text.SimpleDateFormat
import java.util.*

class ConversationAdapter(private val onConvClick: (ConversationEntity) -> Unit) :
    RecyclerView.Adapter<ConversationAdapter.ConvViewHolder>() {

    private var conversations = listOf<ConversationEntity>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submitList(list: List<ConversationEntity>) {
        conversations = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConvViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConvViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConvViewHolder, position: Int) {
        val conv = conversations[position]
        holder.name.text = conv.name
        holder.lastMsg.text = conv.lastMessage
        holder.time.text = timeFormat.format(Date(conv.lastTimestamp))
        
        if (conv.unreadCount > 0) {
            holder.badge.text = conv.unreadCount.toString()
            holder.badge.visibility = View.VISIBLE
        } else {
            holder.badge.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onConvClick(conv) }
    }

    override fun getItemCount(): Int = conversations.size

    class ConvViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvConvName)
        val lastMsg: TextView = view.findViewById(R.id.tvConvLastMsg)
        val time: TextView = view.findViewById(R.id.tvConvTime)
        val badge: TextView = view.findViewById(R.id.tvUnreadBadge)
        val avatar: ImageView = view.findViewById(R.id.ivAvatar)
    }
}
