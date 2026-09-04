package com.example.ipnotifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class IpMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fetchMutex = Mutex()
    private val deliveryMutex = Mutex()
    private var periodicFetchJob: Job? = null
    private var deliveryRetryJob: Job? = null
    private var lastFetchAt = 0L

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var deliverySender: IpDeliverySender

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            fetchIp(reason = getString(R.string.change_reason_network_available))
            attemptDelivery()
        }

        override fun onLost(network: Network) {
            IpMonitorRepository.setNoNetwork()
            updateNotification(
                title = getString(R.string.notification_title_no_network),
                text = getString(R.string.notification_text_no_network),
            )
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                fetchIp(reason = getString(R.string.change_reason_network_changed))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        deliverySender = ToastIpDeliverySender(this)
        createNotificationChannel()
        registerNetworkCallback()
        IpMonitorRepository.setStatus(IpStatus.Monitoring)
        startPeriodicFetch()
        startDeliveryRetry()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_title_monitoring), getString(R.string.notification_starting)))
        fetchIp(reason = getString(R.string.change_reason_initial))
        return START_STICKY
    }

    override fun onDestroy() {
        periodicFetchJob?.cancel()
        deliveryRetryJob?.cancel()
        serviceScope.cancel()
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun startPeriodicFetch() {
        periodicFetchJob = serviceScope.launch {
            while (true) {
                delay(PERIODIC_FETCH_MS)
                if (hasInternet()) {
                    fetchIp(reason = getString(R.string.change_reason_periodic))
                }
            }
        }
    }

    private fun startDeliveryRetry() {
        deliveryRetryJob = serviceScope.launch {
            while (true) {
                delay(DELIVERY_RETRY_TICK_MS)
                if (hasInternet()) {
                    attemptDelivery()
                }
            }
        }
    }

    private fun hasInternet(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun fetchIp(reason: String) {
        serviceScope.launch {
            fetchMutex.withLock {
                val now = System.currentTimeMillis()
                if (now - lastFetchAt < MIN_FETCH_INTERVAL_MS) return@withLock
                lastFetchAt = now

                if (!hasInternet()) {
                    IpMonitorRepository.setNoNetwork()
                    updateNotification(
                        title = getString(R.string.notification_title_no_network),
                        text = getString(R.string.notification_text_no_network),
                    )
                    return@withLock
                }

                IpMonitorRepository.setStatus(IpStatus.Fetching)
                updateNotification(
                    title = getString(R.string.notification_title_fetching),
                    text = getString(R.string.notification_text_fetching),
                )

                try {
                    val ip = IpFetcher.fetchPublicIp()
                    IpMonitorRepository.applyIpResult(ip, reason)
                    val state = IpMonitorRepository.state.value
                    val notificationText = if (state.ipChanged && state.previousIp != null) {
                        getString(R.string.notification_text_ip_changed, state.previousIp, ip)
                    } else {
                        getString(R.string.notification_text_current_ip, ip)
                    }
                    updateNotification(
                        title = getString(R.string.notification_title_monitoring),
                        text = notificationText,
                    )
                    attemptDelivery()
                } catch (e: Exception) {
                    IpMonitorRepository.setStatus(IpStatus.Error, e.message)
                    updateNotification(
                        title = getString(R.string.notification_title_error),
                        text = e.message ?: getString(R.string.notification_text_unknown_error),
                    )
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun attemptDelivery() {
        serviceScope.launch {
            deliveryMutex.withLock {
                if (!hasInternet()) return@withLock

                val now = System.currentTimeMillis()
                var delivery = IpMonitorRepository.deliveryState()
                if (!IpDeliveryCoordinator.canAttemptNow(delivery, now)) return@withLock

                val ip = delivery.lastSeenIp ?: return@withLock
                delivery = IpDeliveryCoordinator.markDelivering(delivery, now)
                IpMonitorRepository.updateDelivery(delivery)

                val result = deliverySender.send(ip, delivery.lastDeliveredIp)
                delivery = if (result.isSuccess) {
                    IpDeliveryCoordinator.markDelivered(delivery, ip)
                } else {
                    IpDeliveryCoordinator.markFailed(
                        delivery,
                        result.exceptionOrNull()?.message ?: getString(R.string.delivery_error_unknown),
                    )
                }
                IpMonitorRepository.updateDelivery(delivery)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "ip_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MIN_FETCH_INTERVAL_MS = 5_000L
        private const val PERIODIC_FETCH_MS = 30 * 60 * 1_000L
        private const val DELIVERY_RETRY_TICK_MS = 30_000L

        @RequiresApi(Build.VERSION_CODES.O)
        fun start(context: Context) {
            val intent = Intent(context, IpMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IpMonitorService::class.java))
        }
    }
}
