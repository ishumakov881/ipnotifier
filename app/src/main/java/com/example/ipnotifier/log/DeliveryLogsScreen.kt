package com.example.ipnotifier.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ipnotifier.ui.formatDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DeliveryLogFilter(
    val label: String,
    val icon: ImageVector,
) {
    All("Все", Icons.Default.FilterList),
    Success("Отправлено", Icons.Default.CheckCircle),
    Failed("Ошибки", Icons.Default.ErrorOutline),
}

private sealed interface DeliveryLogListItem {
    val key: String

    data class Success(val log: DeliveryLogEntity) : DeliveryLogListItem {
        override val key: String = "ok-${log.id}"
    }

    data class FailureGroup(val logs: List<DeliveryLogEntity>) : DeliveryLogListItem {
        override val key: String = "fail-${logs.first().id}-${logs.last().id}-${logs.size}"
        val newest: DeliveryLogEntity get() = logs.first()
        val oldest: DeliveryLogEntity get() = logs.last()
    }
}

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
    var filter by rememberSaveable { mutableStateOf(DeliveryLogFilter.All) }
    val allLogs by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val searchedLogs by dao.observeSearch(query.trim()).collectAsStateWithLifecycle(initialValue = emptyList())
    val filteredLogs = remember(searchedLogs, filter) {
        when (filter) {
            DeliveryLogFilter.All -> searchedLogs
            DeliveryLogFilter.Success -> searchedLogs.filter { it.success }
            DeliveryLogFilter.Failed -> searchedLogs.filter { !it.success }
        }
    }
    val listItems = remember(filteredLogs) { groupDeliveryLogs(filteredLogs) }
    val totalCount = allLogs.size
    val successCount = remember(allLogs) { allLogs.count { it.success } }
    val failedCount = totalCount - successCount
    val subtitle = when {
        totalCount == 0 -> "Пока пусто"
        filter != DeliveryLogFilter.All || query.isNotBlank() ->
            "Показано ${filteredLogs.size} из $totalCount"
        else -> "Всего $totalCount · ✓ $successCount · ✗ $failedCount"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Логи доставки")
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
                    .padding(top = 8.dp),
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Поиск") },
                placeholder = { Text("destination, текст письма, ответ…") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                        }
                    }
                },
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                DeliveryLogFilter.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = filter == option,
                        onClick = { filter = option },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = DeliveryLogFilter.entries.size,
                        ),
                        icon = {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = { Text(option.label) },
                    )
                }
            }

            when {
                totalCount == 0 -> {
                    EmptyLogsState(
                        icon = Icons.Outlined.Inbox,
                        title = "Логов пока нет",
                        subtitle = "Они появятся после отправки на Destination",
                    )
                }
                filteredLogs.isEmpty() && query.isNotBlank() -> {
                    EmptyLogsState(
                        icon = Icons.Outlined.SearchOff,
                        title = "Ничего не найдено",
                        subtitle = "По запросу «$query» совпадений нет",
                    )
                }
                filteredLogs.isEmpty() -> {
                    EmptyLogsState(
                        icon = filter.icon,
                        title = when (filter) {
                            DeliveryLogFilter.Success -> "Нет успешных отправок"
                            DeliveryLogFilter.Failed -> "Нет ошибок"
                            DeliveryLogFilter.All -> "Ничего не найдено"
                        },
                        subtitle = when (filter) {
                            DeliveryLogFilter.Success -> "Успешные доставки появятся здесь"
                            DeliveryLogFilter.Failed -> "Неудачные попытки появятся здесь"
                            DeliveryLogFilter.All -> "Измените фильтр или поиск"
                        },
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(listItems, key = { it.key }) { item ->
                            when (item) {
                                is DeliveryLogListItem.Success -> {
                                    SwipeToDeleteContainer(
                                        onDelete = {
                                            undoJob?.cancel()
                                            undoJob = scope.launch {
                                                dao.deleteById(item.log.id)
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Запись удалена",
                                                    actionLabel = "Отменить",
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    dao.insert(item.log)
                                                }
                                            }
                                        },
                                    ) {
                                        DeliverySuccessCard(item.log)
                                    }
                                }
                                is DeliveryLogListItem.FailureGroup -> {
                                    SwipeToDeleteContainer(
                                        onDelete = {
                                            undoJob?.cancel()
                                            undoJob = scope.launch {
                                                item.logs.forEach { dao.deleteById(it.id) }
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Удалено ${item.logs.size}: ${item.newest.destinationName}",
                                                    actionLabel = "Отменить",
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    item.logs.forEach { dao.insert(it) }
                                                }
                                            }
                                        },
                                    ) {
                                        DeliveryFailureGroupCard(item)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Logs arrive DESC by time. Collapse adjacent failures for the same destination
 * into one group; successes stay as single items.
 */
private fun groupDeliveryLogs(logs: List<DeliveryLogEntity>): List<DeliveryLogListItem> {
    if (logs.isEmpty()) return emptyList()
    val result = mutableListOf<DeliveryLogListItem>()
    var index = 0
    while (index < logs.size) {
        val current = logs[index]
        if (current.success) {
            result += DeliveryLogListItem.Success(current)
            index++
            continue
        }
        val group = mutableListOf(current)
        var next = index + 1
        while (next < logs.size) {
            val candidate = logs[next]
            if (candidate.success || candidate.destinationId != current.destinationId) break
            group += candidate
            next++
        }
        result += DeliveryLogListItem.FailureGroup(group)
        index = next
    }
    return result
}

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
private fun DeliverySuccessCard(log: DeliveryLogEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
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
                text = "Ответ: ${log.responseText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun DeliveryFailureGroupCard(group: DeliveryLogListItem.FailureGroup) {
    var expanded by remember(group.key) { mutableStateOf(false) }
    val newest = group.newest
    val oldest = group.oldest
    val spanMs = newest.timestampMillis - oldest.timestampMillis
    val title = "${failuresLabel(group.logs.size)} · ${newest.destinationName}"
    val timeRange = if (group.logs.size == 1) {
        formatLogTime(newest.timestampMillis)
    } else {
        "${formatLogShortTime(oldest.timestampMillis)} → ${formatLogShortTime(newest.timestampMillis)}" +
            if (spanMs > 0L) " · ${formatDuration(spanMs)}" else ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = group.logs.size > 1) { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = timeRange,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (group.logs.size > 1) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Text(
                text = "Ошибка: ${newest.responseText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            AnimatedVisibility(visible = expanded || group.logs.size == 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (group.logs.size > 1) {
                        group.logs.forEach { attempt ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = formatLogTime(attempt.timestampMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(
                                    text = attempt.responseText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                    Text(
                        text = "Письмо:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = newest.messageText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

private fun failuresLabel(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val word = when {
        mod100 in 11..14 -> "неудач"
        mod10 == 1 -> "неудача"
        mod10 in 2..4 -> "неудачи"
        else -> "неудач"
    }
    return "$count $word"
}

private fun formatLogTime(millis: Long): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(millis))
}

private fun formatLogShortTime(millis: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(millis))
}
