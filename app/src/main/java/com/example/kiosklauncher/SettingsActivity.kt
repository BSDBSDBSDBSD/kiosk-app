package com.example.kiosklauncher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1001)
        }

        appList.addAll(AppRepository.getLaunchableApps(this))
        adapter = SettingsAppsAdapter(appList)
        binding.settingsAppsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.settingsAppsRecyclerView.adapter = adapter

        binding.saveAppsButton.setOnClickListener { saveSelectedApps() }
        binding.changePinButton.setOnClickListener { changePin() }
        binding.setHomeButton.setOnClickListener { openHomeSettings() }
        binding.enableLockButton.setOnClickListener { enableFullLock() }
        binding.disableLockButton.setOnClickListener { disableFullLock() }
        binding.clearOwnerButton.setOnClickListener { confirmClearDeviceOwner() }
        binding.removeAdminButton.setOnClickListener { confirmRemoveAdmin() }

        setupConnectivitySwitches()
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

    /** Opens the system's default-launcher picker so the user can select this app as Home. */
    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "לא ניתן לפתוח את הגדרות מסך הבית במכשיר זה", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupConnectivitySwitches() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        binding.wifiSwitch.isChecked = try { wifiManager.isWifiEnabled } catch (e: Exception) { false }
        binding.wifiSwitch.setOnCheckedChangeListener { _, checked ->
            CoroutineScope(Dispatchers.IO).launch { RootUtils.setWifiEnabled(checked) }
        }

        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        binding.bluetoothSwitch.isChecked = try { bluetoothAdapter?.isEnabled == true } catch (e: Exception) { false }
        binding.bluetoothSwitch.setOnCheckedChangeListener { _, checked ->
            CoroutineScope(Dispatchers.IO).launch { RootUtils.setBluetoothEnabled(checked) }
        }
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

    private fun confirmClearDeviceOwner() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("הסרת הרשאות Device Owner")
            .setMessage(
                "פעולה זו תסיר את סטטוס ה-Device Owner ואת הגבלות הקיוסק (Status Bar / Keyguard), " +
                    "אבל האפליקציה תישאר מותקנת ואפשר להמשיך להשתמש בה כלאנצ'ר רגיל. " +
                    "בלי מחיקה ובלי אתחול. להמשיך?"
            )
            .setPositiveButton("הסר") { _, _ -> clearDeviceOwner() }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun clearDeviceOwner() {
        KioskPrefs.setLockEnabled(this, false)
        val success = KioskManager.clearDeviceOwner(this)
        if (success) {
            Toast.makeText(this, "הרשאות Device Owner הוסרו. האפליקציה נשארה מותקנת.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                this,
                "לא ניתן היה להסיר את ההרשאות בדרך זו - נסה את הכפתור האדום (דורש root ואתחול).",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun confirmRemoveAdmin() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("הסרת ניהול המכשיר")
            .setMessage(
                "פעולה זו תסיר את הרשאות ניהול המכשיר מהאפליקציה ותפעיל אתחול מיידי. " +
                    "אחרי האתחול תוכל להסיר את האפליקציה כרגיל דרך הגדרות ← אפליקציות. להמשיך?"
            )
            .setPositiveButton("הסר ואתחל") { _, _ -> removeAdminAndReboot() }
            .setNegativeButton("ביטול", null)
            .show()
    }

    private fun removeAdminAndReboot() {
        CoroutineScope(Dispatchers.Main).launch {
            val rootOk = withContext(Dispatchers.IO) { RootUtils.isRootAvailable() }
            if (!rootOk) {
                Toast.makeText(this@SettingsActivity, "לא זוהתה הרשאת root", Toast.LENGTH_LONG).show()
                return@launch
            }
            Toast.makeText(this@SettingsActivity, "מסיר ניהול מכשיר, המכשיר יתאתחל כעת...", Toast.LENGTH_LONG).show()
            withContext(Dispatchers.IO) { RootUtils.removeDeviceOwnerAndReboot(this@SettingsActivity) }
            // Device reboots at this point; nothing further to do here.
        }
    }
}
