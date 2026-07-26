package com.example.kiosklauncher

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kiosklauncher.databinding.ActivitySettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val appList = mutableListOf<AppInfo>()
    private lateinit var adapter: SettingsAppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appList.addAll(AppRepository.getLaunchableApps(this))
        adapter = SettingsAppsAdapter(appList)
        binding.settingsAppsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.settingsAppsRecyclerView.adapter = adapter

        binding.saveAppsButton.setOnClickListener { saveSelectedApps() }
        binding.changePinButton.setOnClickListener { changePin() }
        binding.enableLockButton.setOnClickListener { enableFullLock() }
        binding.disableLockButton.setOnClickListener { disableFullLock() }
    }

    private fun saveSelectedApps() {
        val selected = appList.filter { it.isAllowedInKiosk }.map { it.packageName }.toSet()
        KioskPrefs.saveAllowedPackages(this, selected)
        Toast.makeText(this, "רשימת האפליקציות נשמרה", Toast.LENGTH_SHORT).show()
    }

    private fun changePin() {
        val newPin = binding.newPinField.text.toString()
        if (newPin.length != 4) {
            Toast.makeText(this, "הקוד חייב להיות 4 ספרות", Toast.LENGTH_SHORT).show()
            return
        }
        KioskPrefs.setPin(this, newPin)
        binding.newPinField.text.clear()
        Toast.makeText(this, "הסיסמה עודכנה", Toast.LENGTH_SHORT).show()
    }

    private fun enableFullLock() {
        saveSelectedApps()
        CoroutineScope(Dispatchers.Main).launch {
            val success = withContext(Dispatchers.IO) {
                if (!KioskManager.isDeviceOwner(this@SettingsActivity)) {
                    if (!RootUtils.isRootAvailable()) return@withContext false
                    RootUtils.setDeviceOwnerViaRoot(this@SettingsActivity)
                } else {
                    true
                }
            }

            if (!success || !KioskManager.isDeviceOwner(this@SettingsActivity)) {
                Toast.makeText(
                    this@SettingsActivity,
                    "לא ניתן היה להפוך את האפליקציה ל-Device Owner. ודא שאין חשבון Google מוגדר במכשיר ושיש הרשאת root.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val allowedPackages = KioskPrefs.getAllowedPackages(this@SettingsActivity)
            KioskManager.configureLockTask(this@SettingsActivity, allowedPackages)
            KioskPrefs.setLockEnabled(this@SettingsActivity, true)

            Toast.makeText(this@SettingsActivity, "נעילת קיוסק הופעלה", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun disableFullLock() {
        KioskPrefs.setLockEnabled(this, false)
        KioskManager.releaseRestrictions(this)
        if (KioskManager.isDeviceOwner(this)) {
            KioskManager.configureLockTask(this, KioskPrefs.getAllowedPackages(this))
        }
        Toast.makeText(this, "נעילת קיוסק בוטלה", Toast.LENGTH_SHORT).show()
        finish()
    }
}
