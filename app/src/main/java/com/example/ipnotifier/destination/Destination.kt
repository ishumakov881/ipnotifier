package com.example.ipnotifier.destination

enum class DestinationType {
    Mail,
    // Telegram,
    // FirebaseMessaging,
}

data class DeliveryContext(
    val ip: String,
    val previousDeliveredIp: String?,
)

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
            appendLine("IP: ${context.ip}")
            context.previousDeliveredIp?.let { appendLine("Previous: $it") }
        }.trim()
}
