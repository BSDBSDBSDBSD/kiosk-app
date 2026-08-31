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
        binding.checkOwnerStatusButton.setOnClickListener { checkOwnerStatus() }
        binding.enableLockButton.setOnClickListener { enableFullLock() }
        binding.disableLockButton.setOnClickListener { disableFullLock() }
        binding.clearOwnerButton.setOnClickListener { confirmClearDeviceOwner() }

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
        if (!BuildConfig.WIFI_ENABLED) {
            binding.settingsWifiRow.visibility = android.view.View.GONE
            binding.settingsWifiNetworksButton.visibility = android.view.View.GONE
        } else {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            binding.wifiSwitch.isChecked = try { wifiManager.isWifiEnabled } catch (e: Exception) { false }
            binding.wifiSwitch.setOnCheckedChangeListener { _, checked ->
                CoroutineScope(Dispatchers.IO).launch { RootUtils.setWifiEnabled(checked) }
            }
            binding.settingsWifiNetworksButton.setOnClickListener {
                Toast.makeText(this, "פתח את הוילון במסך הבית כדי לחפש רשתות", Toast.LENGTH_SHORT).show()
            }
        }

        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        binding.bluetoothSwitch.isChecked = try { bluetoothAdapter?.isEnabled == true } catch (e: Exception) { false }
        binding.bluetoothSwitch.setOnCheckedChangeListener { _, checked ->
            CoroutineScope(Dispatchers.IO).launch { RootUtils.setBluetoothEnabled(checked) }
        }
    }

    private fun checkOwnerStatus() {
        CoroutineScope(Dispatchers.Main).launch {
            val isOwnerHere = KioskManager.isDeviceOwner(this@SettingsActivity)
            val dump = withContext(Dispatchers.IO) { RootUtils.dumpDeviceOwnerStatus() }
            androidx.appcompat.app.AlertDialog.Builder(this@SettingsActivity)
                .setTitle("מצב Device Owner")
                .setMessage(
                    "האפליקציה הזו היא Device Owner: ${if (isOwnerHere) "כן" else "לא"}\n\n" +
                        "פלט מלא מהמערכת:\n$dump"
                )
                .setPositiveButton("סגור", null)
                .show()
        }
    }

    private fun enableFullLock() {
        saveSelectedApps()
        CoroutineScope(Dispatchers.Main).launch {
            if (KioskManager.isDeviceOwner(this@SettingsActivity)) {
                finishEnablingLock()
                return@launch
            }

            val rootOk = withContext(Dispatchers.IO) { RootUtils.isRootAvailable() }
            if (!rootOk) {
                Toast.makeText(this@SettingsActivity, "לא זוהתה הרשאת root", Toast.LENGTH_LONG).show()
                return@launch
            }

            val result = withContext(Dispatchers.IO) { RootUtils.setDeviceOwnerViaRoot(this@SettingsActivity) }

            if (!result.success || !KioskManager.isDeviceOwner(this@SettingsActivity)) {
                androidx.appcompat.app.AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("ההפעלה נכשלה")
                    .setMessage(
                        "לא ניתן היה להפוך את האפליקציה ל-Device Owner.\n\n" +
                            "פלט הפקודה בפועל:\n${result.log}\n\n" +
                            "סיבות נפוצות: יש כבר Device Owner אחר במכשיר (מבדיקה קודמת עם " +
                            "פלייבור/אפליקציה אחרת), יש חשבון Google מוגדר, או שאין הרשאת root מספקת."
                    )
                    .setPositiveButton("הבנתי", null)
                    .show()
                return@launch
            }

            finishEnablingLock()
        }
    }

    private fun finishEnablingLock() {
        val allowedPackages = KioskPrefs.getAllowedPackages(this)
        KioskManager.configureLockTask(this, allowedPackages)
        KioskPrefs.setLockEnabled(this, true)
        Toast.makeText(this, "נעילת קיוסק הופעלה", Toast.LENGTH_LONG).show()
        finish()
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
                "לא ניתן היה להסיר את ההרשאות. ודא שיש הרשאת root ונסה שוב.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
