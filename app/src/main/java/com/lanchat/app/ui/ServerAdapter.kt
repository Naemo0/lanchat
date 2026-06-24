package com.lanchat.app.ui

import android.net.nsd.NsdServiceInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lanchat.app.R

class ServerAdapter(private val onServerClick: (NsdServiceInfo) -> Unit) :
    RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    private val servers = mutableListOf<NsdServiceInfo>()

    fun addServer(serviceInfo: NsdServiceInfo) {
        if (servers.none { it.serviceName == serviceInfo.serviceName }) {
            servers.add(serviceInfo)
            notifyItemInserted(servers.size - 1)
        }
    }

    fun removeServer(serviceInfo: NsdServiceInfo) {
        val index = servers.indexOfFirst { it.serviceName == serviceInfo.serviceName }
        if (index != -1) {
            servers.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_discovered_server, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val server = servers[position]
        holder.name.text = server.serviceName
        holder.ip.text = server.host?.hostAddress ?: "Unknown IP"
        
        // Update Avatar
        val avatarColor = if (server.serviceName.isNotEmpty()) {
            val colors = intArrayOf(0xFF6366F1.toInt(), 0xFF10B981.toInt(), 0xFFF59E0B.toInt(), 0xFFEF4444.toInt(), 0xFF8B5CF6.toInt())
            colors[Math.abs(server.serviceName.hashCode()) % colors.size]
        } else 0xFF6366F1.toInt()
        
        holder.avatar.setBackgroundColor(avatarColor)
        holder.avatar.setImageResource(android.R.drawable.ic_dialog_map)
        holder.avatar.imageTintList = android.content.res.ColorStateList.valueOf(holder.itemView.context.getColor(R.color.white))

        holder.itemView.setOnClickListener { onServerClick(server) }
    }

    override fun getItemCount(): Int = servers.size

    class ServerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvServerName)
        val ip: TextView = view.findViewById(R.id.tvServerIp)
        val avatar: android.widget.ImageView = view.findViewById(R.id.ivServerAvatar)
    }
}
