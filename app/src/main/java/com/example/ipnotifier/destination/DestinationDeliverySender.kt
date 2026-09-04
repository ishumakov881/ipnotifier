package com.example.ipnotifier.destination

import android.content.Context
import com.example.ipnotifier.IpDeliverySender
import com.example.ipnotifier.log.DeliveryLogRepository

class DestinationDeliverySender(
    private val context: Context,
) : IpDeliverySender {

    override suspend fun send(ip: String, previousDeliveredIp: String?): Result<Unit> {
        val deliveryContext = DeliveryContext(
            ip = ip,
            previousDeliveredIp = previousDeliveredIp,
        )
        val destinations = AppDestinations.load(context.applicationContext)
            .filter { it.enabled }

        if (destinations.isEmpty()) {
            return Result.failure(IllegalStateException("No enabled destinations configured"))
        }

        val results = destinations.map { destination ->
            val result = destination.send(deliveryContext)
            DeliveryLogRepository.logSend(
                context = context.applicationContext,
                destination = destination,
                deliveryContext = deliveryContext,
                result = result,
            )
            DestinationSendResult(
                destinationId = destination.id,
                destinationName = destination.displayName,
                success = result.isSuccess,
                errorMessage = result.exceptionOrNull()?.message,
            )
        }

        val failures = results.filterNot { it.success }
        return if (failures.isEmpty()) {
            Result.success(Unit)
        } else {
            val message = failures.joinToString("; ") { "${it.destinationName}: ${it.errorMessage}" }
            Result.failure(IllegalStateException(message))
        }
    }
}
