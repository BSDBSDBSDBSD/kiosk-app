package com.example.kiosklauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BluetoothDevicesAdapter(
    private val devices: MutableList<BtDevice>,
    private val onPairClick: (BtDevice) -> Unit
) : RecyclerView.Adapter<BluetoothDevicesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.deviceName)
        val status: TextView = view.findViewById(R.id.deviceStatus)
        val pairButton: Button = view.findViewById(R.id.pairButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val d = devices[position]
        holder.name.text = d.name
        holder.status.text = if (d.isPaired) "מחובר בעבר" else d.address
        holder.pairButton.text = if (d.isPaired) "חבר מחדש" else "חבר"
        holder.pairButton.setOnClickListener { onPairClick(d) }
    }

    override fun getItemCount() = devices.size

    fun setDevices(newDevices: List<BtDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    fun addIfNew(device: BtDevice) {
        if (devices.none { it.address == device.address }) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }
}
