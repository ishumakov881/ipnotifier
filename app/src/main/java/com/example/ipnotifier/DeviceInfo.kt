package com.example.ipnotifier

import android.content.Context
import android.os.Build
import android.provider.Settings

object DeviceInfo {

    fun name(context: Context): String {
        val fromSettings = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        return fromSettings
            ?: Build.MODEL.takeIf { it.isNotBlank() }
            ?: Build.DEVICE.takeIf { it.isNotBlank() }
            ?: "Android"
    }
}
