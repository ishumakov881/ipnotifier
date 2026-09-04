package com.example.ipnotifier

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface IpDeliverySender {
    suspend fun send(ip: String, previousDeliveredIp: String?): Result<Unit>
}

class ToastIpDeliverySender(
    private val context: Context,
) : IpDeliverySender {

    override suspend fun send(ip: String, previousDeliveredIp: String?): Result<Unit> =
        withContext(Dispatchers.Main) {
            val message = if (previousDeliveredIp != null && previousDeliveredIp != ip) {
                "→ фронт: $previousDeliveredIp → $ip"
            } else {
                "→ фронт: $ip"
            }
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
            Result.success(Unit)
        }
}
