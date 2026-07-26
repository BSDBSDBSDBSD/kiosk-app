package com.example.kiosklauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!KioskPrefs.isLockEnabled(context)) return

        if (KioskManager.isDeviceOwner(context)) {
            KioskManager.configureLockTask(context, KioskPrefs.getAllowedPackages(context))
        }

        val launchIntent = Intent(context, MainActivity::class.java)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }
}
