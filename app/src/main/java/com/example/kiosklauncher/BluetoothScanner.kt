package com.example.kiosklauncher

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class BluetoothScanner(private val context: Context) {

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var receiver: BroadcastReceiver? = null

    fun isSupported(): Boolean = adapter != null

    fun pairedDevices(): List<BluetoothDevice> {
        return try {
            adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun startScan(onDeviceFound: (BluetoothDevice) -> Unit, onFinished: () -> Unit) {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        @Suppress("DEPRECATION")
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null) onDeviceFound(device)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> onFinished()
                }
            }
        }
        context.registerReceiver(receiver, filter)
        try {
            adapter?.startDiscovery()
        } catch (e: SecurityException) {
            onFinished()
        }
    }

    fun stopScan() {
        try {
            adapter?.cancelDiscovery()
        } catch (e: SecurityException) {
            // ignore
        }
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // already unregistered
            }
        }
        receiver = null
    }

    fun pair(device: BluetoothDevice): Boolean {
        return try {
            device.createBond()
        } catch (e: SecurityException) {
            false
        }
    }

    /** Best-effort readable name; falls back to the MAC address if the name isn't accessible. */
    fun displayName(device: BluetoothDevice): String {
        return try {
            device.name ?: device.address
        } catch (e: SecurityException) {
            device.address
        }
    }
}
