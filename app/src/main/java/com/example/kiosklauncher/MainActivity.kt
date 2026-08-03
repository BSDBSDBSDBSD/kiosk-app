package com.example.kiosklauncher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.net.wifi.WifiManager
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.kiosklauncher.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.adminCornerTrigger.setOnLongClickListener {
            showPinDialog()
            true
        }

        // This is the home screen — there's nowhere to "go back" to, so
        // pressing back here should simply do nothing instead of falling
        // through to the system Recents/Overview screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // no-op: swallow the back press
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

    private fun updateStatusText() {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiOn = try { wifiManager.isWifiEnabled } catch (e: Exception) { false }

        val parts = mutableListOf<String>()
        if (batteryPct >= 0) parts.add("סוללה $batteryPct%")
        parts.add(if (wifiOn) "Wi-Fi מופעל" else "Wi-Fi כבוי")
        binding.statusText.text = parts.joinToString("   •   ")
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
