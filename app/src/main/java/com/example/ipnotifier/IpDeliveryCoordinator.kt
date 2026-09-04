package com.example.ipnotifier

/**
 * Логика «отправить, но не спамить»:
 *
 * - Каждое **новое наблюдение** IP (отличается от lastSeenIp) увеличивает observationGeneration.
 * - Отправка нужна, пока observationGeneration > lastDeliveredGeneration.
 * - Поэтому цепочка 101→102→105→101 после неудачных отправок всё равно даст отправку,
 *   хотя строка IP совпала с последней успешной.
 * - Повторный fetch того же IP без промежуточных смен — generation не растёт → не спамим.
 */
object IpDeliveryCoordinator {

    fun onIpObserved(current: IpDeliveryState, ip: String): IpDeliveryState {
        if (ip == current.lastSeenIp) {
            return current
        }
        return current.copy(
            lastSeenIp = ip,
            observationGeneration = current.observationGeneration + 1,
        )
    }

    fun needsDelivery(state: IpDeliveryState): Boolean =
        state.lastSeenIp != null &&
            state.observationGeneration > state.lastDeliveredGeneration &&
            !state.isDelivering

    fun retryDelayMs(consecutiveFailures: Int): Long = when {
        consecutiveFailures <= 0 -> 0L
        consecutiveFailures == 1 -> 30_000L
        consecutiveFailures == 2 -> 60_000L
        consecutiveFailures == 3 -> 5 * 60_000L
        else -> 15 * 60_000L
    }

    fun canAttemptNow(state: IpDeliveryState, nowMillis: Long): Boolean {
        if (!needsDelivery(state)) return false
        val lastAttempt = state.lastDeliveryAttemptMillis ?: return true
        return nowMillis - lastAttempt >= retryDelayMs(state.consecutiveFailures)
    }

    fun markDelivering(state: IpDeliveryState, nowMillis: Long): IpDeliveryState =
        state.copy(
            isDelivering = true,
            lastDeliveryAttemptMillis = nowMillis,
            deliveryStatus = DeliveryStatus.Sending,
        )

    fun markDelivered(state: IpDeliveryState, ip: String): IpDeliveryState =
        state.copy(
            lastDeliveredIp = ip,
            lastDeliveredGeneration = state.observationGeneration,
            isDelivering = false,
            consecutiveFailures = 0,
            lastDeliveryError = null,
            deliveryStatus = DeliveryStatus.Delivered,
        )

    fun markFailed(state: IpDeliveryState, error: String): IpDeliveryState =
        state.copy(
            isDelivering = false,
            consecutiveFailures = state.consecutiveFailures + 1,
            lastDeliveryError = error,
            deliveryStatus = DeliveryStatus.Failed,
        )
}
