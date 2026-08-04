package com.example.kiosklauncher

import android.net.wifi.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WifiNetworksAdapter(
    private val networks: MutableList<ScanResult>,
    private val onConnectClick: (ScanResult) -> Unit
) : RecyclerView.Adapter<WifiNetworksAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.networkName)
        val status: TextView = view.findViewById(R.id.networkStatus)
        val connectButton: Button = view.findViewById(R.id.connectButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wifi_network, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val network = networks[position]
        holder.name.text = network.SSID
        val secured = WifiHelper.isSecured(network)
        holder.status.text = if (secured) "מאובטחת" else "פתוחה"
        holder.connectButton.setOnClickListener { onConnectClick(network) }
    }

    override fun getItemCount() = networks.size

    fun setNetworks(newNetworks: List<ScanResult>) {
        networks.clear()
        networks.addAll(newNetworks)
        notifyDataSetChanged()
    }
}
