package com.example.ipnotifier.destination

import com.example.ipnotifier.geo.IpGeoInfo

enum class DestinationType {
    Mail,
    // Telegram,
    // FirebaseMessaging,
}

data class DeliveryContext(
    val ip: String,
    val previousDeliveredIp: String?,
    val deviceName: String,
    val currentGeo: IpGeoInfo? = null,
    val previousGeo: IpGeoInfo? = null,
) {
    fun formatIp(ip: String = this.ip, geo: IpGeoInfo? = currentGeo): String =
        geo?.let { "${it.flagEmoji} $ip" } ?: ip

    fun formatPreviousIp(): String? {
        val previous = previousDeliveredIp ?: return null
        return formatIp(previous, previousGeo)
    }
}

data class DestinationSendResult(
    val destinationId: String,
    val destinationName: String,
    val success: Boolean,
    val errorMessage: String? = null,
)

interface Destination {
    val id: String
    val displayName: String
    val type: DestinationType
    val enabled: Boolean

    /** Why nothing will be sent to this destination; null when it is active. */
    val inactiveReason: String?

    suspend fun send(context: DeliveryContext): Result<Unit>

    fun composeMessage(context: DeliveryContext): String =
        buildString {
            appendLine("Device: ${context.deviceName}")
            appendLine("IP: ${context.formatIp()}")
            context.formatPreviousIp()?.let { appendLine("Previous: $it") }
        }.trim()
}
