package com.example.ipnotifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpDeliveryCoordinatorTest {

    @Test
    fun flapBackToDeliveredIp_settlesWithoutSend() {
        val t0 = 1_000_000L
        var state = IpDeliveryState(
            lastSeenIp = "176.0.0.1",
            lastDeliveredIp = "176.0.0.1",
            observationGeneration = 1,
            lastDeliveredGeneration = 1,
            lastSeenChangedAtMillis = t0,
            deliveryStatus = DeliveryStatus.Delivered,
        )
        state = IpDeliveryCoordinator.onIpObserved(state, "194.0.0.1", t0 + 1_000)
        assertTrue(IpDeliveryCoordinator.needsDelivery(state))

        val plan = IpDeliveryCoordinator.planAttempt(
            state = state,
            freshIp = "176.0.0.1",
            nowMillis = t0 + 120_000,
            stabilityMs = 60_000,
        )
        assertEquals(IpDeliveryCoordinator.AttemptAction.None, plan.action)
        assertEquals("176.0.0.1", plan.state.lastDeliveredIp)
        assertEquals(plan.state.observationGeneration, plan.state.lastDeliveredGeneration)
        assertFalse(IpDeliveryCoordinator.needsDelivery(plan.state))
    }

    @Test
    fun unstableIp_doesNotSend() {
        val t0 = 1_000_000L
        val state = IpDeliveryCoordinator.onIpObserved(IpDeliveryState(), "194.0.0.1", t0)
        val plan = IpDeliveryCoordinator.planAttempt(
            state = state,
            freshIp = "194.0.0.1",
            nowMillis = t0 + 30_000,
            stabilityMs = 120_000,
        )
        assertEquals(IpDeliveryCoordinator.AttemptAction.None, plan.action)
        assertTrue(IpDeliveryCoordinator.needsDelivery(plan.state))
    }

    @Test
    fun stableNewIp_sends() {
        val t0 = 1_000_000L
        var state = IpDeliveryState(
            lastSeenIp = "176.0.0.1",
            lastDeliveredIp = "176.0.0.1",
            observationGeneration = 1,
            lastDeliveredGeneration = 1,
            lastSeenChangedAtMillis = t0,
        )
        state = IpDeliveryCoordinator.onIpObserved(state, "194.0.0.1", t0 + 1_000)
        val plan = IpDeliveryCoordinator.planAttempt(
            state = state,
            freshIp = "194.0.0.1",
            nowMillis = t0 + 1_000 + 120_000,
            stabilityMs = 120_000,
        )
        assertEquals(IpDeliveryCoordinator.AttemptAction.Send, plan.action)
        assertEquals("194.0.0.1", plan.state.lastSeenIp)
    }

    @Test
    fun revalidateToNewerIp_beforeSend_usesFresh() {
        val t0 = 1_000_000L
        var state = IpDeliveryCoordinator.onIpObserved(IpDeliveryState(), "194.0.0.1", t0)
        val plan = IpDeliveryCoordinator.planAttempt(
            state = state,
            freshIp = "10.0.0.5",
            nowMillis = t0 + 120_000,
            stabilityMs = 60_000,
        )
        // Fresh IP just observed at plan time → not stable yet relative to its change.
        assertEquals("10.0.0.5", plan.state.lastSeenIp)
        assertEquals(IpDeliveryCoordinator.AttemptAction.None, plan.action)
    }
}
