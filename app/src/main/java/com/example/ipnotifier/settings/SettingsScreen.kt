package com.example.ipnotifier.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ipnotifier.destination.DeliveryContext
import com.example.ipnotifier.destination.MailDestination
import com.example.ipnotifier.destination.MailErrors
import com.example.ipnotifier.log.DeliveryLogRepository
import com.example.ipnotifier.destination.MailDestinationConfig
import com.example.ipnotifier.destination.MailSecurityMode
import androidx.compose.material3.CardDefaults
import kotlinx.coroutines.launch

private sealed interface TestSendUiState {
    data class Running(val step: String) : TestSendUiState
    data class Success(val recipients: String) : TestSendUiState
    data class Error(val message: String) : TestSendUiState
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { MailSettingsStore(context) }
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(MailDestinationConfig()) }
    var securityExpanded by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var testState by remember { mutableStateOf<TestSendUiState?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        settings = store.getSettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Destinations") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Mail (Main)",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Yandex: smtp.yandex.ru, 465 + SSL. Логин = полный email. Пароль — только «пароль приложения» (id.yandex.ru → Безопасность). Обычный пароль Yandex отклонит с 535.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Включить Mail destination")
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = { settings = settings.copy(enabled = it) },
                        )
                    }

                    SettingsTextField(
                        label = "Имя destination",
                        value = settings.displayName,
                        onValueChange = { settings = settings.copy(displayName = it) },
                    )
                }
            }

            SectionTitle("SMTP (отправка)")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsTextField(
                        label = "SMTP сервер",
                        value = settings.smtpHost,
                        onValueChange = { settings = settings.copy(smtpHost = it) },
                        placeholder = "smtp.yandex.ru",
                    )
                    SettingsTextField(
                        label = "SMTP порт",
                        value = settings.smtpPort.toString(),
                        onValueChange = { value ->
                            settings = settings.copy(smtpPort = value.toIntOrNull() ?: settings.smtpPort)
                        },
                        keyboardType = KeyboardType.Number,
                    )

                    ExposedDropdownMenuBox(
                        expanded = securityExpanded,
                        onExpandedChange = { securityExpanded = it },
                    ) {
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            readOnly = true,
                            value = securityModeLabel(settings.securityMode),
                            onValueChange = {},
                            label = { Text("Безопасность SMTP") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = securityExpanded) },
                        )
                        ExposedDropdownMenu(
                            expanded = securityExpanded,
                            onDismissRequest = { securityExpanded = false },
                        ) {
                            MailSecurityMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(securityModeLabel(mode)) },
                                    onClick = {
                                        settings = settings.copy(securityMode = mode)
                                        securityExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            SectionTitle("POP3 (опционально)")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsTextField(
                        label = "POP3 сервер",
                        value = settings.pop3Host,
                        onValueChange = { settings = settings.copy(pop3Host = it) },
                        placeholder = "pop.yandex.ru",
                    )
                    SettingsTextField(
                        label = "POP3 порт",
                        value = settings.pop3Port.toString(),
                        onValueChange = { value ->
                            settings = settings.copy(pop3Port = value.toIntOrNull() ?: settings.pop3Port)
                        },
                        keyboardType = KeyboardType.Number,
                    )
                }
            }

            SectionTitle("Аутентификация и адреса")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsTextField(
                        label = "Логин",
                        value = settings.username,
                        onValueChange = { settings = settings.copy(username = it) },
                        placeholder = "jakez007@yandex.ru",
                        keyboardType = KeyboardType.Email,
                    )
                    PasswordSettingsTextField(
                        label = "Пароль",
                        value = settings.password,
                        onValueChange = { settings = settings.copy(password = it) },
                    )
                    SettingsTextField(
                        label = "От (From)",
                        value = settings.fromAddress,
                        onValueChange = { settings = settings.copy(fromAddress = it) },
                        placeholder = "jakez007@yandex.ru",
                        keyboardType = KeyboardType.Email,
                    )
                    SettingsTextField(
                        label = "Кому (To)",
                        value = settings.toAddresses,
                        onValueChange = { settings = settings.copy(toAddresses = it) },
                        placeholder = "admin@example.com, ops@example.com",
                    )
                }
            }

            TestSendStatusCard(state = testState)

            saveMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving && !isTesting,
                    onClick = {
                        scope.launch {
                            isSaving = true
                            store.save(settings)
                            saveMessage = "Настройки сохранены"
                            isSaving = false
                        }
                    },
                ) {
                    Text(if (isSaving) "Сохранение…" else "Сохранить")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !isTesting && !isSaving && settings.isConfiguredForSending(),
                    onClick = {
                        scope.launch {
                            isTesting = true
                            try {
                                testState = TestSendUiState.Running("Сохранение настроек…")
                                store.save(settings)

                                val smtpInfo =
                                    "${settings.smtpHost}:${settings.smtpPort} (${securityModeLabel(settings.securityMode)})"
                                testState = TestSendUiState.Running("Подключение к $smtpInfo (до 20 сек)…")

                                val destination = MailDestination("mail_main", settings.copy(enabled = true))
                                val deliveryContext = DeliveryContext(
                                    ip = "203.0.113.42",
                                    previousDeliveredIp = "198.51.100.10",
                                )
                                val result = destination.send(deliveryContext)
                                DeliveryLogRepository.logSend(
                                    context = context.applicationContext,
                                    destination = destination,
                                    deliveryContext = deliveryContext,
                                    result = result,
                                )

                                testState = if (result.isSuccess) {
                                    TestSendUiState.Success(
                                        recipients = settings.recipientList().joinToString(),
                                    )
                                } else {
                                    TestSendUiState.Error(
                                        message = MailErrors.format(result.exceptionOrNull()),
                                    )
                                }
                            } catch (e: Exception) {
                                testState = TestSendUiState.Error(
                                    message = MailErrors.format(e),
                                )
                            } finally {
                                isTesting = false
                            }
                        }
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        Text(if (isTesting) "Отправка…" else "Тест")
                    }
                }
            }
        }
    }
}

@Composable
private fun TestSendStatusCard(state: TestSendUiState?) {
    if (state == null) return

    val containerColor = when (state) {
        is TestSendUiState.Running -> MaterialTheme.colorScheme.surfaceVariant
        is TestSendUiState.Success -> MaterialTheme.colorScheme.primaryContainer
        is TestSendUiState.Error -> MaterialTheme.colorScheme.errorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Тестовая отправка",
                style = MaterialTheme.typography.titleSmall,
            )
            when (state) {
                is TestSendUiState.Running -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = state.step,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is TestSendUiState.Success -> {
                    Text(
                        text = "✓ Письмо успешно отправлено",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Получатели: ${state.recipients}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Тема: IP Notifier: 198.51.100.10 → 203.0.113.42",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                is TestSendUiState.Error -> {
                    Text(
                        text = "✗ Не удалось отправить",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordSettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (passwordVisible) KeyboardType.Text else KeyboardType.Password,
        ),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (passwordVisible) "Скрыть пароль" else "Показать пароль",
                )
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

private fun securityModeLabel(mode: MailSecurityMode): String = when (mode) {
    MailSecurityMode.NONE -> "Без шифрования"
    MailSecurityMode.STARTTLS -> "STARTTLS (587)"
    MailSecurityMode.SSL -> "SSL/TLS (465)"
}
