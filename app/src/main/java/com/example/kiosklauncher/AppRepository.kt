package com.example.kiosklauncher

import android.content.Context
import android.content.pm.PackageManager

object AppRepository {

    /** Returns all launchable apps installed on the device, sorted by label. */
    fun getLaunchableApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val allowed = KioskPrefs.getAllowedPackages(context)

        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map {
                AppInfo(
                    packageName = it.packageName,
                    label = pm.getApplicationLabel(it).toString(),
                    icon = pm.getApplicationIcon(it),
                    isAllowedInKiosk = allowed.contains(it.packageName)
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
