package com.example.ipnotifier

interface IpDeliverySender {
    suspend fun send(ip: String, previousDeliveredIp: String?): Result<Unit>
}
