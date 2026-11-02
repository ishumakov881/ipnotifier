package com.example.ipnotifier.destination

/** Stub channel added via «+» until a real implementation exists. */
class PlaceholderDestination(
    override val id: String,
    override val displayName: String,
    override val type: DestinationType,
) : Destination {

    override val enabled: Boolean = false
    override val inactiveReason: String = "в разработке"

    override suspend fun send(context: DeliveryContext): Result<Unit> =
        Result.failure(IllegalStateException("$displayName пока не реализован"))
}
