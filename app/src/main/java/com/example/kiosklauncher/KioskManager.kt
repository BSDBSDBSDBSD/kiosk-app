package com.example.kiosklauncher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

object KioskManager {

    private fun adminComponent(context: Context) =
        ComponentName(context, KioskDeviceAdminReceiver::class.java)

    private fun dpm(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isDeviceOwner(context: Context): Boolean {
        return dpm(context).isDeviceOwnerApp(context.packageName)
    }

    /**
     * Restricts which packages may run in Lock Task Mode and enables the
     * status-bar / keyguard restrictions. Only works once the app is the
     * Device Owner.
     */
    fun configureLockTask(context: Context, allowedPackages: Set<String>) {
        if (!isDeviceOwner(context)) return
        val manager = dpm(context)
        val admin = adminComponent(context)

        val packages = (allowedPackages + context.packageName).toTypedArray()
        manager.setLockTaskPackages(admin, packages)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setStatusBarDisabled(admin, true)
        }
        manager.setKeyguardDisabled(admin, true)
    }

    fun startLockTask(activity: Activity) {
        activity.startLockTask()
    }

    fun stopLockTask(activity: Activity) {
        activity.stopLockTask()
    }

    /** Restores the status bar and keyguard so the device behaves normally again. */
    fun releaseRestrictions(context: Context) {
        if (!isDeviceOwner(context)) return
        val manager = dpm(context)
        val admin = adminComponent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.setStatusBarDisabled(admin, false)
        }
        manager.setKeyguardDisabled(admin, false)
    }
}
