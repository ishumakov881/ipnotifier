package com.example.ipnotifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Запуск мониторинга IP после перезагрузки устройства.
 * Требует, чтобы приложение хотя бы раз было открыто пользователем
 * (ограничение Android на автозапуск «мёртвых» приложений).
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in ACCEPTED_ACTIONS) return

        Log.i(TAG, "boot event: $action → start IpMonitorService")
        IpMonitorService.start(context.applicationContext)
    }

    private companion object {
        const val TAG = "BootCompletedReceiver"

        val ACCEPTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
