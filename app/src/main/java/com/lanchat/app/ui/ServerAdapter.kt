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
        if (servers.none { it.host.hostAddress == serviceInfo.host.hostAddress }) {
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
        holder.ip.text = server.host.hostAddress
        holder.itemView.setOnClickListener { onServerClick(server) }
    }

    override fun getItemCount(): Int = servers.size

    class ServerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvServerName)
        val ip: TextView = view.findViewById(R.id.tvServerIp)
    }
}
