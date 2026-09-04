package com.example.ipnotifier.log

import android.content.Context
import com.example.ipnotifier.destination.Destination
import com.example.ipnotifier.destination.DeliveryContext

object DeliveryLogRepository {

    fun dao(context: Context): DeliveryLogDao =
        AppDatabase.get(context).deliveryLogDao()

    suspend fun logSend(
        context: Context,
        destination: Destination,
        deliveryContext: DeliveryContext,
        result: Result<Unit>,
    ) {
        val response = if (result.isSuccess) {
            "OK"
        } else {
            result.exceptionOrNull()?.message ?: "Unknown error"
        }
        dao(context).insert(
            DeliveryLogEntity(
                timestampMillis = System.currentTimeMillis(),
                destinationId = destination.id,
                destinationName = destination.displayName,
                messageText = destination.composeMessage(deliveryContext),
                responseText = response,
                success = result.isSuccess,
            ),
        )
    }
}
