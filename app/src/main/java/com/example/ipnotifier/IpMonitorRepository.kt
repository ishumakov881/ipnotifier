package com.example.ipnotifier

import com.example.ipnotifier.geo.IpGeoInfo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object IpMonitorRepository {

    private val _state = MutableStateFlow(IpMonitorState())
    val state: StateFlow<IpMonitorState> = _state.asStateFlow()

    fun setStatus(status: IpStatus, errorMessage: String? = null) {
        _state.update { it.copy(status = status, errorMessage = errorMessage) }
    }

    fun setServiceRunning(running: Boolean) {
        _state.update { current ->
            if (running) {
                current.copy(isServiceRunning = true, status = IpStatus.Monitoring, errorMessage = null)
            } else {
                current.copy(isServiceRunning = false, status = IpStatus.Idle)
            }
        }
    }

    fun setNoNetwork() {
        _state.update { current ->
            current.copy(
                previousIp = current.currentIp ?: current.previousIp,
                currentIp = null,
                status = IpStatus.NoNetwork,
                ipChanged = false,
                changeReason = null,
                errorMessage = null,
            )
        }
    }

    fun applyIpResult(ip: String, reason: String) {
        _state.update { current ->
            val changed = current.currentIp != null && current.currentIp != ip
            val delivery = IpDeliveryCoordinator.onIpObserved(current.delivery, ip).let { observed ->
                if (IpDeliveryCoordinator.needsDelivery(observed)) {
                    observed.copy(deliveryStatus = DeliveryStatus.Pending)
                } else {
                    observed
                }
            }
            current.copy(
                previousIp = if (changed) current.currentIp else current.previousIp,
                currentIp = ip,
                status = IpStatus.Monitoring,
                lastUpdatedMillis = System.currentTimeMillis(),
                ipChanged = changed,
                changeReason = if (changed) reason else current.changeReason,
                errorMessage = null,
                delivery = delivery,
            )
        }
    }

    fun setGeo(ip: String, geo: IpGeoInfo) {
        _state.update { current ->
            val relevantIps = setOfNotNull(current.currentIp, current.previousIp, ip)
            current.copy(geoByIp = (current.geoByIp + (ip to geo)).filterKeys { it in relevantIps })
        }
    }

    fun updateDelivery(delivery: IpDeliveryState) {
        _state.update { it.copy(delivery = delivery) }
    }

    fun deliveryState(): IpDeliveryState = _state.value.delivery

    fun clearChangeHighlight() {
        _state.update { it.copy(ipChanged = false, changeReason = null) }
    }
}
