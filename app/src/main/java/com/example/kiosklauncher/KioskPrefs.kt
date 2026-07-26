package com.example.kiosklauncher

import android.content.Context
import java.security.MessageDigest

object KioskPrefs {
    private const val PREFS = "kiosk_prefs"
    private const val KEY_ALLOWED = "allowed_packages"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_LOCK_ENABLED = "lock_enabled"
    private const val DEFAULT_PIN = "1234"

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAllowedPackages(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_ALLOWED, emptySet()) ?: emptySet()
    }

    fun saveAllowedPackages(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_ALLOWED, packages).apply()
    }

    fun checkPin(context: Context, pin: String): Boolean {
        val storedHash = prefs(context).getString(KEY_PIN_HASH, sha256(DEFAULT_PIN))
        return sha256(pin) == storedHash
    }

    fun setPin(context: Context, newPin: String) {
        prefs(context).edit().putString(KEY_PIN_HASH, sha256(newPin)).apply()
    }

    fun isLockEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_LOCK_ENABLED, false)
    }

    fun setLockEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
    }
}
