package com.example.ipnotifier

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import com.example.ipnotifier.destination.AppDestinations
import com.example.ipnotifier.destination.Destination
import com.example.ipnotifier.geo.IpGeoInfo
import com.example.ipnotifier.ui.StatusStripeCard
import com.example.ipnotifier.ui.statusColor
import com.example.ipnotifier.log.DeliveryLogsScreen
import com.example.ipnotifier.settings.SettingsScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ipnotifier.ui.theme.IpnotifierTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        IpMonitorService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startMonitoringService()
        setContent {
            IpnotifierTheme {
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Main) }
                BackHandler(enabled = currentScreen != AppScreen.Main) {
                    currentScreen = AppScreen.Main
                }
                when (currentScreen) {
                    AppScreen.Main -> IpMonitorScreen(
                        onOpenSettings = { currentScreen = AppScreen.Settings },
                        onOpenLogs = { currentScreen = AppScreen.Logs },
                        onStopMonitoring = { IpMonitorService.stop(this) },
                        onStartMonitoring = { startMonitoringService() },
                    )
                    AppScreen.Settings -> SettingsScreen(
                        onBack = { currentScreen = AppScreen.Main },
                    )
                    AppScreen.Logs -> DeliveryLogsScreen(
                        onBack = { currentScreen = AppScreen.Main },
                    )
                }
            }
        }
    }

    private fun startMonitoringService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED -> {
                    IpMonitorService.start(this)
                }
                else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            IpMonitorService.start(this)
        }
    }
}

private enum class AppScreen {
    Main,
    Settings,
    Logs,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpMonitorScreen(
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onStopMonitoring: () -> Unit,
    onStartMonitoring: () -> Unit,
) {
    val state by IpMonitorRepository.state.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext
    val destinations by remember(appContext) { AppDestinations.flow(appContext) }
        .collectAsStateWithLifecycle(initialValue = null)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.ipChanged, state.currentIp) {
        if (state.ipChanged && state.currentIp != null && state.previousIp != null) {
            snackbarHostState.showSnackbar(
                message = "IP изменился: ${state.previousIp} → ${state.currentIp}",
            )
            IpMonitorRepository.clearChangeHighlight()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("IP Notifier") },
                actions = {
                    IconButton(onClick = onOpenLogs) {
                        Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Логи")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Сервис работает в фоне и проверяет IP при смене сети.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CurrentIpCard(state = state)
            StatusCard(state = state)
            DeliveryCard(
                delivery = state.delivery,
                destinations = destinations,
                onClick = onOpenSettings,
            )

            state.previousIp?.let { previousIp ->
                PreviousIpCard(previousIp = previousIp, geo = state.geoByIp[previousIp])
            }

            MonitoringToggleButton(
                isRunning = state.isServiceRunning,
                onStart = onStartMonitoring,
                onStop = onStopMonitoring,
            )
        }
    }
}

@Composable
private fun MonitoringToggleButton(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isRunning) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "toggleContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isRunning) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        label = "toggleContent",
    )

    Button(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = if (isRunning) onStop else onStart,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        AnimatedContent(
            targetState = isRunning,
            label = "toggleLabel",
        ) { running ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    text = if (running) "Остановить мониторинг" else "Запустить мониторинг",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun CurrentIpCard(state: IpMonitorState) {
    val containerColor by animateColorAsState(
        targetValue = if (state.ipChanged) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "ipCardColor",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Текущий внешний IP",
                style = MaterialTheme.typography.titleMedium,
            )
            val geo = state.currentIp?.let { state.geoByIp[it] }
            val ipStyle = MaterialTheme.typography.displaySmall
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (geo != null) {
                    Text(text = geo.flagEmoji, style = ipStyle)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = state.currentIp ?: "—",
                        style = ipStyle,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 18.sp,
                            maxFontSize = ipStyle.fontSize,
                        ),
                    )
                    if (geo != null) {
                        GeoDetails(geo = geo)
                    }
                }
            }
            if (state.changeReason != null) {
                Text(
                    text = "Причина проверки: ${state.changeReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeliveryCard(
    delivery: IpDeliveryState,
    destinations: List<Destination>?,
    onClick: () -> Unit,
) {
    StatusStripeCard(
        active = destinations?.any { it.enabled },
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Отправка (Destinations)",
                style = MaterialTheme.typography.titleMedium,
            )
            destinations?.let { list ->
                if (list.none { it.enabled }) {
                    Text(
                        text = "IP никуда не отправляется: нет активных получателей",
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor(active = false),
                    )
                }
                list.forEach { destination ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(statusColor(destination.enabled), CircleShape),
                        )
                        Text(
                            text = "${destination.displayName}: ${destination.inactiveReason ?: "активно"}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (destinations?.any { it.enabled } != false) {
                Text(text = deliveryStatusLabel(delivery.deliveryStatus))
            }
            delivery.lastDeliveredIp?.let { ip ->
                Text(
                    text = "Последняя отправка: $ip",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                delivery.lastDeliveredMillis?.let { millis ->
                    Text(
                        text = formatRelativeTimestamp(millis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (IpDeliveryCoordinator.needsDelivery(delivery)) {
                Text(
                    text = "Есть неотправленные наблюдения (#${delivery.observationGeneration - delivery.lastDeliveredGeneration})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            delivery.lastDeliveryError?.let { error ->
                Text(
                    text = "Ошибка: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: IpMonitorState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Статус",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = statusLabel(state.status))
            state.lastUpdatedMillis?.let { millis ->
                Text(
                    text = "Обновлено: ${formatTimestamp(millis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.errorMessage?.let { error ->
                Text(
                    text = "Ошибка: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun PreviousIpCard(previousIp: String, geo: IpGeoInfo?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Предыдущий IP",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (geo != null) {
                    Text(text = geo.flagEmoji, style = MaterialTheme.typography.titleLarge)
                }
                Column {
                    Text(
                        text = previousIp,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (geo != null) {
                        GeoDetails(geo = geo)
                    }
                }
            }
        }
    }
}

@Composable
private fun GeoDetails(geo: IpGeoInfo) {
    geo.isp?.let { isp ->
        Text(
            text = isp,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    Text(
        text = listOfNotNull(geo.countryName, geo.asn).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun deliveryStatusLabel(status: DeliveryStatus): String = when (status) {
    DeliveryStatus.Idle -> "Нечего отправлять"
    DeliveryStatus.Pending -> "Ожидает отправки"
    DeliveryStatus.Sending -> "Отправка…"
    DeliveryStatus.Delivered -> "Отправлено"
    DeliveryStatus.Failed -> "Ошибка отправки"
}

private fun statusLabel(status: IpStatus): String = when (status) {
    IpStatus.Idle -> "Мониторинг остановлен"
    IpStatus.Fetching -> "Получение IP..."
    IpStatus.Monitoring -> "Мониторинг активен"
    IpStatus.NoNetwork -> "Нет сети"
    IpStatus.Error -> "Ошибка"
}

private fun formatRelativeTimestamp(millis: Long): String {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    val day = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun Calendar.sameDayAs(other: Calendar) =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
            get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
    return when {
        day.sameDayAs(today) -> "сегодня в $time"
        day.sameDayAs(yesterday) -> "вчера в $time"
        else -> SimpleDateFormat("dd.MM.yyyy в HH:mm", Locale.getDefault()).format(Date(millis))
    }
}

private fun formatTimestamp(millis: Long): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(millis))
}
