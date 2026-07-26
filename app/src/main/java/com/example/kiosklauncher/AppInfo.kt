package com.example.kiosklauncher

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    var isAllowedInKiosk: Boolean = false
)
