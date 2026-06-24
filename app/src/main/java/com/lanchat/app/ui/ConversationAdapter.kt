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
        
        // Update Avatar
        val avatarColor = if (conv.id.isNotEmpty()) {
            val colors = intArrayOf(0xFF6366F1.toInt(), 0xFF10B981.toInt(), 0xFFF59E0B.toInt(), 0xFFEF4444.toInt(), 0xFF8B5CF6.toInt())
            colors[Math.abs(conv.id.hashCode()) % colors.size]
        } else 0xFF6366F1.toInt()
        
        holder.avatar.setBackgroundColor(avatarColor)
        holder.avatar.setImageResource(android.R.drawable.ic_menu_myplaces)
        holder.avatar.imageTintList = android.content.res.ColorStateList.valueOf(holder.itemView.context.getColor(R.color.white))
        
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
