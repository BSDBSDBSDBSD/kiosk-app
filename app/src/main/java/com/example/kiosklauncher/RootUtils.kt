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
