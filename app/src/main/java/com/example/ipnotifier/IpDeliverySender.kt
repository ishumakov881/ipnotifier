package com.example.ipnotifier

interface IpDeliverySender {
    suspend fun send(
        ip: String,
        previousDeliveredIp: String?,
        observedAtMillis: Long = System.currentTimeMillis(),
    ): Result<Unit>
}
