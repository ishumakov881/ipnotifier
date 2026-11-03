package com.example.ipnotifier

/**
 * Логика «отправить, но не спамить»:
 *
 * - Каждое **новое наблюдение** IP (отличается от lastSeenIp) увеличивает observationGeneration
 *   и сбрасывает таймер стабильности.
 * - Отправка только если generation впереди **и** IP стабилен [stabilityMs],
 *   **и** прошёл backoff после ошибок.
 * - Перед попыткой всегда сверяемся со свежим fetch: если IP снова равен
 *   lastDeliveredIp (флап 176→194→176) — тихо помечаем delivered без письма.
 */
object IpDeliveryCoordinator {

    const val DEFAULT_STABILITY_MS = 2 * 60_000L

    fun onIpObserved(
        current: IpDeliveryState,
        ip: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): IpDeliveryState {
        if (ip == current.lastSeenIp) {
            return current
        }
        return current.copy(
            lastSeenIp = ip,
            observationGeneration = current.observationGeneration + 1,
            lastSeenChangedAtMillis = nowMillis,
        )
    }

    fun needsDelivery(state: IpDeliveryState): Boolean =
        state.lastSeenIp != null &&
            state.observationGeneration > state.lastDeliveredGeneration &&
            !state.isDelivering

    fun isStable(
        state: IpDeliveryState,
        nowMillis: Long,
        stabilityMs: Long = DEFAULT_STABILITY_MS,
    ): Boolean {
        val changedAt = state.lastSeenChangedAtMillis ?: return false
        return nowMillis - changedAt >= stabilityMs
    }

    fun stabilityRemainingMs(
        state: IpDeliveryState,
        nowMillis: Long,
        stabilityMs: Long = DEFAULT_STABILITY_MS,
    ): Long {
        val changedAt = state.lastSeenChangedAtMillis ?: return stabilityMs
        return (changedAt + stabilityMs - nowMillis).coerceAtLeast(0L)
    }

    fun retryDelayMs(consecutiveFailures: Int): Long = when {
        consecutiveFailures <= 0 -> 0L
        consecutiveFailures == 1 -> 30_000L
        consecutiveFailures == 2 -> 60_000L
        consecutiveFailures == 3 -> 5 * 60_000L
        else -> 15 * 60_000L
    }

    fun canRetryNow(state: IpDeliveryState, nowMillis: Long): Boolean {
        val lastAttempt = state.lastDeliveryAttemptMillis ?: return true
        return nowMillis - lastAttempt >= retryDelayMs(state.consecutiveFailures)
    }

    /**
     * Sync state to [freshIp], optionally settle a healed flap, then decide whether to send.
     * Caller must persist [AttemptPlan.state] even when action is [AttemptAction.None].
     */
    fun planAttempt(
        state: IpDeliveryState,
        freshIp: String,
        nowMillis: Long,
        stabilityMs: Long = DEFAULT_STABILITY_MS,
    ): AttemptPlan {
        var next = onIpObserved(state, freshIp, nowMillis)

        // Flap healed: public IP matches what we already notified — no mail.
        if (
            freshIp == next.lastDeliveredIp &&
            next.observationGeneration > next.lastDeliveredGeneration
        ) {
            return AttemptPlan(
                state = markDelivered(next, freshIp, nowMillis),
                action = AttemptAction.None,
            )
        }

        if (!needsDelivery(next)) {
            return AttemptPlan(state = next, action = AttemptAction.None)
        }

        next = next.copy(deliveryStatus = DeliveryStatus.Pending)

        if (!isStable(next, nowMillis, stabilityMs)) {
            return AttemptPlan(state = next, action = AttemptAction.None)
        }
        if (!canRetryNow(next, nowMillis)) {
            return AttemptPlan(state = next, action = AttemptAction.None)
        }

        return AttemptPlan(state = next, action = AttemptAction.Send)
    }

    /** @deprecated Prefer [planAttempt]; kept for call sites that only check readiness. */
    fun canAttemptNow(state: IpDeliveryState, nowMillis: Long): Boolean {
        if (!needsDelivery(state)) return false
        if (!isStable(state, nowMillis)) return false
        return canRetryNow(state, nowMillis)
    }

    fun markDelivering(state: IpDeliveryState, nowMillis: Long): IpDeliveryState =
        state.copy(
            isDelivering = true,
            lastDeliveryAttemptMillis = nowMillis,
            deliveryStatus = DeliveryStatus.Sending,
        )

    fun markDelivered(state: IpDeliveryState, ip: String, nowMillis: Long): IpDeliveryState =
        state.copy(
            lastDeliveredIp = ip,
            lastDeliveredMillis = nowMillis,
            lastDeliveredGeneration = state.observationGeneration,
            isDelivering = false,
            consecutiveFailures = 0,
            failureSinceMillis = null,
            lastDeliveryError = null,
            deliveryStatus = DeliveryStatus.Delivered,
        )

    fun markFailed(state: IpDeliveryState, error: String, nowMillis: Long): IpDeliveryState =
        state.copy(
            isDelivering = false,
            consecutiveFailures = state.consecutiveFailures + 1,
            failureSinceMillis = state.failureSinceMillis ?: nowMillis,
            lastDeliveryError = error,
            deliveryStatus = DeliveryStatus.Failed,
        )

    data class AttemptPlan(
        val state: IpDeliveryState,
        val action: AttemptAction,
    )

    enum class AttemptAction {
        None,
        Send,
    }
}
