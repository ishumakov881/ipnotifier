package com.example.ipnotifier.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryLogsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val dao = remember { DeliveryLogRepository.dao(context) }
    var query by remember { mutableStateOf("") }
    val logs by dao.observeSearch(query.trim()).collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Логи доставки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Поиск") },
                placeholder = { Text("destination, текст письма, ответ…") },
            )

            when {
                logs.isEmpty() && query.isBlank() -> {
                    Text(
                        modifier = Modifier.padding(top = 24.dp),
                        text = "Логов пока нет. Они появятся после отправки на Destination.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                logs.isEmpty() -> {
                    Text(
                        modifier = Modifier.padding(top = 24.dp),
                        text = "Ничего не найдено по запросу «$query».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(logs, key = { it.id }) { log ->
                            DeliveryLogCard(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryLogCard(log: DeliveryLogEntity) {
    val container = if (log.success) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = formatLogTime(log.timestampMillis),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "${log.destinationName} (${log.destinationId})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (log.success) "Ответ: ${log.responseText}" else "Ошибка: ${log.responseText}",
                style = MaterialTheme.typography.bodySmall,
                color = if (log.success) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
            Text(
                text = "Письмо:",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = log.messageText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun formatLogTime(millis: Long): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(millis))
}
