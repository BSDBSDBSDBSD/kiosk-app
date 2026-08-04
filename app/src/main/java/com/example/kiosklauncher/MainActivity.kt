package com.example.kiosklauncher

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kiosklauncher.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var curtainOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestConnectivityPermissions()

        binding.adminCornerTrigger.setOnLongClickListener {
            showPinDialog()
            true
        }

        // Start the curtain fully hidden above the screen. We measure the
        // real rendered height instead of using a fixed guess, so it works
        // correctly no matter the screen size/density.
        binding.curtainPanel.visibility = View.INVISIBLE
        binding.curtainPanel.post {
            binding.curtainPanel.translationY = -binding.curtainPanel.height.toFloat()
            binding.curtainPanel.visibility = View.VISIBLE
        }

        binding.curtainHandle.setOnClickListener { toggleCurtain() }
        binding.curtainCloseButton.setOnClickListener { toggleCurtain() }
        setupCurtainSwitches()
        binding.curtainBluetoothDevicesButton.setOnClickListener { showBluetoothDevicesDialog() }

        if (BuildConfig.WIFI_ENABLED) {
            binding.curtainWifiNetworksButton.setOnClickListener { showWifiNetworksDialog() }
        } else {
            binding.curtainWifiRow.visibility = View.GONE
            binding.curtainWifiNetworksButton.visibility = View.GONE
        }

        // This is the home screen — there's nowhere to "go back" to, so
        // pressing back here should simply do nothing instead of falling
        // through to the system Recents/Overview screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (curtainOpen) toggleCurtain()
            }
        })

        // If the device is already the owner and kiosk lock is enabled,
        // re-enter lock task mode automatically (e.g. after a relaunch).
        if (KioskPrefs.isLockEnabled(this) && KioskManager.isDeviceOwner(this)) {
            KioskManager.startLockTask(this)
        }

        loadGrid()
    }

    override fun onResume() {
        super.onResume()
        loadGrid()
        updateStatusText()
    }

    private fun requestConnectivityPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val toRequest = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), 2001)
        }
    }

    // --- Curtain (quick settings panel) ---

    private fun toggleCurtain() {
        curtainOpen = !curtainOpen
        val target = if (curtainOpen) 0f else -binding.curtainPanel.height.toFloat()
        binding.curtainPanel.animate()
            .translationY(target)
            .setDuration(260)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
        if (curtainOpen) updateStatusText()
    }

    private fun setupCurtainSwitches() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        binding.curtainWifiSwitch.isChecked = try { wifiManager.isWifiEnabled } catch (e: Exception) { false }
        binding.curtainWifiSwitch.setOnCheckedChangeListener { _, checked ->
            CoroutineScope(Dispatchers.IO).launch { RootUtils.setWifiEnabled(checked) }
        }

        binding.curtainBluetoothSwitch.isChecked = try { BluetoothHelper.isEnabled() } catch (e: Exception) { false }
        binding.curtainBluetoothSwitch.setOnCheckedChangeListener { _, checked ->
            CoroutineScope(Dispatchers.IO).launch { RootUtils.setBluetoothEnabled(checked) }
        }
    }

    private fun updateStatusText() {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiOn = try { wifiManager.isWifiEnabled } catch (e: Exception) { false }

        val parts = mutableListOf<String>()
        if (batteryPct >= 0) parts.add("סוללה $batteryPct%")
        if (BuildConfig.WIFI_ENABLED) {
            parts.add(if (wifiOn) "Wi-Fi מופעל" else "Wi-Fi כבוי")
        }
        binding.statusText.text = parts.joinToString("   •   ")
    }

    // --- Bluetooth devices dialog ---

    private fun showBluetoothDevicesDialog() {
        if (!BluetoothHelper.isSupported()) {
            Toast.makeText(this, "אין Bluetooth במכשיר זה", Toast.LENGTH_SHORT).show()
            return
        }
        if (!BluetoothHelper.isEnabled()) {
            Toast.makeText(this, "הפעל קודם את ה-Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_bluetooth_devices)

        val recyclerView = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.devicesRecyclerView)
        val scanButton = dialog.findViewById<Button>(R.id.scanButton)
        val closeButton = dialog.findViewById<Button>(R.id.closeBluetoothDialogButton)
        val progress = dialog.findViewById<ProgressBar>(R.id.scanProgress)

        val devices = BluetoothHelper.getPairedDevices().toMutableList()
        val adapter = BluetoothDevicesAdapter(devices) { device ->
            val ok = BluetoothHelper.pair(device.device)
            Toast.makeText(
                this,
                if (ok) "מנסה להתחבר ל-${device.name}..." else "נכשל בניסיון חיבור",
                Toast.LENGTH_SHORT
            ).show()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        scanButton.setOnClickListener {
            progress.visibility = View.VISIBLE
            BluetoothHelper.startDiscovery(this) { found ->
                runOnUiThread { adapter.addIfNew(found) }
            }
        }

        closeButton.setOnClickListener {
            BluetoothHelper.stopDiscovery(this)
            dialog.dismiss()
        }

        dialog.setOnDismissListener { BluetoothHelper.stopDiscovery(this) }
        dialog.show()
    }

    // --- Wi-Fi networks dialog (full flavor only) ---

    private fun showWifiNetworksDialog() {
        if (!WifiHelper.isEnabled(this)) {
            Toast.makeText(this, "הפעל קודם את ה-Wi-Fi", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_wifi_networks)

        val recyclerView = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.wifiNetworksRecyclerView)
        val scanButton = dialog.findViewById<Button>(R.id.wifiScanButton)
        val closeButton = dialog.findViewById<Button>(R.id.closeWifiDialogButton)
        val progress = dialog.findViewById<ProgressBar>(R.id.wifiScanProgress)

        val networks = mutableListOf<ScanResult>()
        val adapter = WifiNetworksAdapter(networks) { network -> promptConnect(network) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fun runScan() {
            progress.visibility = View.VISIBLE
            WifiHelper.startScan(this) { results ->
                runOnUiThread {
                    progress.visibility = View.GONE
                    adapter.setNetworks(results)
                }
            }
        }

        scanButton.setOnClickListener { runScan() }
        closeButton.setOnClickListener {
            WifiHelper.stopScan(this)
            dialog.dismiss()
        }
        dialog.setOnDismissListener { WifiHelper.stopScan(this) }

        runScan()
        dialog.show()
    }

    private fun promptConnect(network: ScanResult) {
        val secured = WifiHelper.isSecured(network)
        if (!secured) {
            connectWifi(network.SSID, null, false)
            return
        }

        val input = EditText(this)
        input.hint = "סיסמת הרשת"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        AlertDialog.Builder(this)
            .setTitle(network.SSID)
            .setView(input)
            .setPositiveButton("התחבר") { _, _ ->
                connectWifi(network.SSID, input.text.toString(), true)
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun connectWifi(ssid: String, password: String?, secured: Boolean) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(this@MainActivity, "מתחבר ל-$ssid...", Toast.LENGTH_SHORT).show()
            val ok = kotlinx.coroutines.withContext(Dispatchers.IO) {
                WifiHelper.connect(ssid, password, secured)
            }
            if (!ok) {
                Toast.makeText(this@MainActivity, "החיבור נכשל", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadGrid() {
        val allowedPackages = KioskPrefs.getAllowedPackages(this)
        val apps = AppRepository.getLaunchableApps(this)
            .filter { allowedPackages.contains(it.packageName) }

        binding.emptyStateText.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        binding.kioskGrid.layoutManager = GridLayoutManager(this, 4)
        binding.kioskGrid.adapter = KioskAppsAdapter(apps) { app ->
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) startActivity(intent)
        }
    }

    private fun showPinDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.hint = "קוד PIN"

        AlertDialog.Builder(this)
            .setTitle("גישת מנהל")
            .setView(input)
            .setPositiveButton("אישור") { _, _ ->
                val pin = input.text.toString()
                if (KioskPrefs.checkPin(this, pin)) {
                    if (KioskManager.isDeviceOwner(this)) {
                        KioskManager.stopLockTask(this)
                    }
                    startActivity(Intent(this, SettingsActivity::class.java))
                } else {
                    Toast.makeText(this, "קוד שגוי", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("ביטול", null)
            .show()
    }
}
