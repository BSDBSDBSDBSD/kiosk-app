package com.example.kiosklauncher

import android.content.Context
import java.io.DataOutputStream

object RootUtils {

    /**
     * Runs `dpm set-device-owner` via a root shell. This only needs to run
     * once; after that the app is the Device Owner and all further kiosk
     * enforcement goes through the official DevicePolicyManager APIs
     * (no more dependency on root).
     */
    fun setDeviceOwnerViaRoot(context: Context): Boolean {
        val component = "${context.packageName}/.KioskDeviceAdminReceiver"
        val commands = listOf(
            "dpm set-device-owner $component"
        )
        return runAsRoot(commands)
    }

    fun isRootAvailable(): Boolean = runAsRoot(listOf("id"))

    /**
     * Removes this app's Device Owner status and then uninstalls it.
     * Needed because Android blocks normal uninstall of an active Device
     * Owner app by design (to stop a kiosk user from just removing the app
     * to escape). Requires root, run only from a PIN-protected screen.
     *
     * Note: `dpm remove-active-admin` is blocked by the OS for any
     * "non-test" Device Owner (SecurityException), so we fall back to
     * renaming the system's device-policy state files directly and
     * rebooting. After the reboot the app is no longer Device Owner and
     * can be uninstalled normally from Settings.
     */
    fun removeDeviceOwnerAndReboot(context: Context): Boolean {
        val component = "${context.packageName}/.KioskDeviceAdminReceiver"
        val commands = listOf(
            "dpm remove-active-admin $component 2>/dev/null",
            "mv /data/system/device_policies.xml /data/system/device_policies.xml.bak 2>/dev/null",
            "mv /data/system/device_owner.xml /data/system/device_owner.xml.bak 2>/dev/null",
            "mv /data/system/device_owner_2.xml /data/system/device_owner_2.xml.bak 2>/dev/null",
            "reboot"
        )
        return runAsRoot(commands)
    }

    fun setWifiEnabled(enabled: Boolean): Boolean {
        val state = if (enabled) "enable" else "disable"
        return runAsRoot(listOf("svc wifi $state"))
    }

    fun setBluetoothEnabled(enabled: Boolean): Boolean {
        val state = if (enabled) "enable" else "disable"
        return runAsRoot(listOf("svc bluetooth $state"))
    }

    private fun runAsRoot(commands: List<String>): Boolean {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            val os = DataOutputStream(process.outputStream)
            for (cmd in commands) {
                os.writeBytes("$cmd\n")
            }
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            process.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
