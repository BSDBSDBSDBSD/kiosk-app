package com.example.kiosklauncher

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
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

        binding.adminCornerTrigger.setOnClickListener {
            showPinDialog()
        }

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
