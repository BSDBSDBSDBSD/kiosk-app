package com.example.kiosklauncher

import android.content.Context
import java.io.DataOutputStream

object RootUtils {

    data class Result(val success: Boolean, val log: String)

    /**
     * Runs `dpm set-device-owner` via a root shell. This only needs to run
     * once; after that the app is the Device Owner and all further kiosk
     * enforcement goes through the official DevicePolicyManager APIs
     * (no more dependency on root).
     *
     * Important: we must use the receiver's fully-qualified class name here,
     * NOT the relative ".ClassName" shorthand. The shorthand is resolved
     * against the app's packageName (applicationId), which differs from the
     * class's real package whenever a flavor uses applicationIdSuffix (like
     * the btOnly flavor) — that mismatch made this silently fail for that
     * flavor only.
     */
    fun setDeviceOwnerViaRoot(context: Context): Result {
        val component = "${context.packageName}/${KioskDeviceAdminReceiver::class.java.name}"
        val commands = mutableListOf("dpm set-device-owner $component")
        val output = runAsRootWithOutput(commands)
        val success = output.contains("Success")
        // If it failed, also dump who currently holds device owner (if
        // anyone) - a very common cause is a DIFFERENT app/flavor already
        // holding it from earlier testing, which only that app (or a
        // factory reset) can release.
        val diagnostics = if (!success) {
            runAsRootWithOutput(listOf("dumpsys device_policy | grep -i 'device owner' -A 3"))
        } else ""
        return Result(success, if (diagnostics.isNotBlank()) "$output\n\nמצב Device Owner נוכחי:\n$diagnostics" else output)
    }

    fun isRootAvailable(): Boolean = runAsRoot(listOf("id"))

    fun setWifiEnabled(enabled: Boolean): Boolean {
        val state = if (enabled) "enable" else "disable"
        return runAsRoot(listOf("svc wifi $state"))
    }

    fun setBluetoothEnabled(enabled: Boolean): Boolean {
        val state = if (enabled) "enable" else "disable"
        return runAsRoot(listOf("svc bluetooth $state"))
    }

    /** Generic single-command root runner, for features like Wi-Fi network connect. */
    fun runRootCommand(command: String): Boolean = runAsRoot(listOf(command))

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

    private fun runAsRootWithOutput(commands: List<String>): String {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            val os = DataOutputStream(process.outputStream)
            for (cmd in commands) {
                os.writeBytes("$cmd\n")
            }
            os.writeBytes("exit\n")
            os.flush()
            os.close()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            "שגיאה: ${e.message}"
        }
    }
}
