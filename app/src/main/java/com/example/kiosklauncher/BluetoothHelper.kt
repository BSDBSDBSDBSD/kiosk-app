package com.example.kiosklauncher

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

data class BtDevice(
    val name: String,
    val address: String,
    val isPaired: Boolean,
    val device: BluetoothDevice
)

object BluetoothHelper {

    private var receiver: BroadcastReceiver? = null

    fun isSupported(): Boolean = BluetoothAdapter.getDefaultAdapter() != null

    fun isEnabled(): Boolean = BluetoothAdapter.getDefaultAdapter()?.isEnabled == true

    /** Already paired/bonded devices - always available instantly, no scanning needed. */
    fun getPairedDevices(): List<BtDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return try {
            adapter.bondedDevices.map {
                BtDevice(it.name ?: it.address, it.address, true, it)
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /**
     * Starts scanning for nearby discoverable devices. [onFound] is called
     * once per newly discovered device. Call [stopDiscovery] when done
     * (e.g. when the dialog closes) to unregister the receiver.
     */
    fun startDiscovery(context: Context, onFound: (BtDevice) -> Unit) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        stopDiscovery(context)

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    try {
                        onFound(BtDevice(device.name ?: device.address, device.address, false, device))
                    } catch (e: SecurityException) {
                        // missing permission on this OS version; ignore this result
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
        try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
        } catch (e: SecurityException) {
            // caller is responsible for requesting BLUETOOTH_SCAN beforehand
        }
    }

    fun stopDiscovery(context: Context) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        try {
            adapter?.cancelDiscovery()
        } catch (e: SecurityException) { }
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (e: IllegalArgumentException) { }
        }
        receiver = null
    }

    /** Attempts to pair with a device. Most audio devices auto-connect once bonded. */
    fun pair(device: BluetoothDevice): Boolean {
        return try {
            device.createBond()
        } catch (e: SecurityException) {
            false
        }
    }
}
