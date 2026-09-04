package com.example.ipnotifier

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ipnotifier.ui.theme.IpnotifierTheme
import java.text.SimpleDateFormat
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
                IpMonitorScreen(
                    onStopMonitoring = { IpMonitorService.stop(this) },
                    onStartMonitoring = { startMonitoringService() },
                )
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

@Composable
fun IpMonitorScreen(
    onStopMonitoring: () -> Unit,
    onStartMonitoring: () -> Unit,
) {
    val state by IpMonitorRepository.state.collectAsStateWithLifecycle()
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "IP Notifier",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Сервис работает в фоне и проверяет IP при смене сети.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CurrentIpCard(state = state)
            StatusCard(state = state)
            DeliveryCard(delivery = state.delivery)

            if (state.previousIp != null) {
                PreviousIpCard(previousIp = state.previousIp!!)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onStartMonitoring,
                ) {
                    Text("Запустить")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onStopMonitoring,
                ) {
                    Text("Остановить")
                }
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
            Text(
                text = state.currentIp ?: "—",
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
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
private fun DeliveryCard(delivery: IpDeliveryState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Отправка на фронт",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = deliveryStatusLabel(delivery.deliveryStatus))
            delivery.lastDeliveredIp?.let { ip ->
                Text(
                    text = "Последний отправленный: $ip",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
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
private fun PreviousIpCard(previousIp: String) {
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
            Text(
                text = previousIp,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun deliveryStatusLabel(status: DeliveryStatus): String = when (status) {
    DeliveryStatus.Idle -> "Ожидание"
    DeliveryStatus.Pending -> "Ожидает отправки"
    DeliveryStatus.Sending -> "Отправка…"
    DeliveryStatus.Delivered -> "Отправлено"
    DeliveryStatus.Failed -> "Ошибка отправки"
}

private fun statusLabel(status: IpStatus): String = when (status) {
    IpStatus.Idle -> "Ожидание"
    IpStatus.Fetching -> "Получение IP..."
    IpStatus.Monitoring -> "Мониторинг активен"
    IpStatus.NoNetwork -> "Нет сети"
    IpStatus.Error -> "Ошибка"
}

private fun formatTimestamp(millis: Long): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(millis))
}
