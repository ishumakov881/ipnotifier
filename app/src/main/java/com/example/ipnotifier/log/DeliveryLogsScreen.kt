package com.example.ipnotifier.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var undoJob by remember { mutableStateOf<Job?>(null) }
    var query by remember { mutableStateOf("") }
    val logs by dao.observeSearch(query.trim()).collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    EmptyLogsState(
                        icon = Icons.Outlined.Inbox,
                        title = "Логов пока нет",
                        subtitle = "Они появятся после отправки на Destination",
                    )
                }
                logs.isEmpty() -> {
                    EmptyLogsState(
                        icon = Icons.Outlined.SearchOff,
                        title = "Ничего не найдено",
                        subtitle = "По запросу «$query» совпадений нет",
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(logs, key = { it.id }) { log ->
                            SwipeToDeleteContainer(
                                onDelete = {
                                    undoJob?.cancel()
                                    undoJob = scope.launch {
                                        dao.deleteById(log.id)
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Запись удалена",
                                            actionLabel = "Отменить",
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            dao.insert(log)
                                        }
                                    }
                                },
                            ) {
                                DeliveryLogCard(log)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Паттерн как в ldsonline NotificationMessagesScreen:
 * confirmValueChange вызывает onDelete и возвращает false —
 * свайп не «залипает», элемент уходит из списка через данные.
 */
@Composable
private fun EmptyLogsState(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun SwipeToDeleteContainer(
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                currentOnDelete()
                false
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val color = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, shape = CardDefaults.shape)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        },
        content = { content() },
    )
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
