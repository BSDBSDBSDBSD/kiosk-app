package com.example.kiosklauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager

object WifiHelper {

    private var receiver: BroadcastReceiver? = null

    fun isEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return try { wifiManager.isWifiEnabled } catch (e: Exception) { false }
    }

    /** Starts a scan; [onResults] is called once with whatever networks were found. */
    fun startScan(context: Context, onResults: (List<ScanResult>) -> Unit) {
        val appContext = context.applicationContext
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        stopScan(appContext)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val results = try { wifiManager.scanResults } catch (e: SecurityException) { emptyList() }
                // de-duplicate by SSID, keep the strongest signal
                val deduped = results
                    .filter { it.SSID.isNotBlank() }
                    .groupBy { it.SSID }
                    .map { (_, group) -> group.maxByOrNull { it.level }!! }
                    .sortedByDescending { it.level }
                onResults(deduped)
                stopScan(appContext)
            }
        }
        appContext.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        try {
            wifiManager.startScan()
        } catch (e: Exception) {
            onResults(emptyList())
        }
    }

    fun stopScan(context: Context) {
        receiver?.let {
            try { context.applicationContext.unregisterReceiver(it) } catch (e: IllegalArgumentException) { }
        }
        receiver = null
    }

    fun isSecured(result: ScanResult): Boolean {
        val caps = result.capabilities ?: ""
        return caps.contains("WPA") || caps.contains("WEP") || caps.contains("PSK") || caps.contains("EAP")
    }

    /**
     * Connects to a network via root shell (`cmd wifi connect-network`).
     * Works on Android 10+ where regular apps can no longer manage Wi-Fi
     * configurations directly. Best-effort — command availability can vary
     * slightly by OEM/Android version.
     */
    fun connect(ssid: String, password: String?, secured: Boolean): Boolean {
        val escapedSsid = ssid.replace("\"", "\\\"")
        val command = if (!secured || password.isNullOrEmpty()) {
            "cmd wifi connect-network \"$escapedSsid\" open"
        } else {
            val escapedPass = password.replace("\"", "\\\"")
            "cmd wifi connect-network \"$escapedSsid\" wpa2 \"$escapedPass\""
        }
        return RootUtils.runRootCommand(command)
    }
}
