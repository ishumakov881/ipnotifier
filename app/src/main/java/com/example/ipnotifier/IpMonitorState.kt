package com.example.ipnotifier

enum class IpStatus {
    Idle,
    Fetching,
    Monitoring,
    NoNetwork,
    Error,
}

enum class DeliveryStatus {
    Idle,
    Pending,
    Sending,
    Delivered,
    Failed,
}

data class IpDeliveryState(
    val lastSeenIp: String? = null,
    val lastDeliveredIp: String? = null,
    val observationGeneration: Long = 0,
    val lastDeliveredGeneration: Long = 0,
    val isDelivering: Boolean = false,
    val lastDeliveryAttemptMillis: Long? = null,
    val consecutiveFailures: Int = 0,
    val lastDeliveryError: String? = null,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.Idle,
)

data class IpMonitorState(
    val currentIp: String? = null,
    val previousIp: String? = null,
    val status: IpStatus = IpStatus.Idle,
    val lastUpdatedMillis: Long? = null,
    val ipChanged: Boolean = false,
    val changeReason: String? = null,
    val errorMessage: String? = null,
    val delivery: IpDeliveryState = IpDeliveryState(),
)
