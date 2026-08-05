package com.example.kiosklauncher

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
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
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var curtainOpen = false

    // drag-tracking state for the curtain handle
    private var dragStartRawY = 0f
    private var dragStartTranslation = 0f
    private var isDragging = false

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

        setupCurtainDragHandle()
        binding.curtainCloseButton.setOnClickListener { animateCurtain(false) }
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
                if (curtainOpen) animateCurtain(false)
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
        // Wi-Fi scan results require location permission on every Android
        // version (unlike Bluetooth, this did not change with neverForLocation).
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        val toRequest = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), 2001)
        }
    }

    /** True if the required scan permission(s) are actually granted. */
    private fun hasScanPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val btOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        return fineLocation && btOk
    }

    /** Both classic Bluetooth discovery and Wi-Fi scanning silently return zero
     *  results if system Location is turned off, even with permission granted. */
    private fun isLocationServicesEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try { lm.isLocationEnabled } catch (e: Exception) { true }
    }

    private fun warnIfScanBlocked(): Boolean {
        if (!hasScanPermissions()) {
            Toast.makeText(this, "חסרה הרשאת מיקום/Bluetooth לסריקה - אשר אותה ונסה שוב", Toast.LENGTH_LONG).show()
            requestConnectivityPermissions()
            return true
        }
        if (!isLocationServicesEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("מיקום כבוי")
                .setMessage("חיפוש מכשירי Bluetooth ורשתות Wi-Fi דורש שהמיקום (Location) יהיה פעיל במערכת, גם אם לא נעשה בו שימוש בפועל. להפעיל עכשיו?")
                .setPositiveButton("פתח הגדרות מיקום") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("ביטול", null)
                .show()
            return true
        }
        return false
    }

    // --- Curtain (quick settings panel) ---

    private fun setupCurtainDragHandle() {
        binding.curtainHandle.setOnTouchListener { _, event ->
            val panelHeight = binding.curtainPanel.height.toFloat()
                .let { if (it <= 0f) 700f else it }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartRawY = event.rawY
                    dragStartTranslation = binding.curtainPanel.translationY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = event.rawY - dragStartRawY
                    if (abs(delta) > 6) isDragging = true
                    if (isDragging) {
                        val newTranslation = (dragStartTranslation + delta).coerceIn(-panelHeight, 0f)
                        binding.curtainPanel.translationY = newTranslation
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        val shouldOpen = binding.curtainPanel.translationY > -panelHeight / 2
                        animateCurtain(shouldOpen)
                    } else {
                        // treated as a simple tap
                        animateCurtain(!curtainOpen)
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun animateCurtain(open: Boolean) {
        curtainOpen = open
        val panelHeight = binding.curtainPanel.height.toFloat()
            .let { if (it <= 0f) 700f else it }
        val target = if (open) 0f else -panelHeight
        binding.curtainPanel.animate()
            .translationY(target)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
        if (open) updateStatusText()
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
        if (warnIfScanBlocked()) return

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
        if (warnIfScanBlocked()) return

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
                    if (results.isEmpty()) {
                        Toast.makeText(this, "לא נמצאו רשתות - נסה שוב בעוד רגע", Toast.LENGTH_SHORT).show()
                    }
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
