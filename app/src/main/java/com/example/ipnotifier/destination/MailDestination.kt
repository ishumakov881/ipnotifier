package com.example.ipnotifier.destination

import android.util.Log
import com.example.ipnotifier.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class MailDestination(
    override val id: String,
    private val config: MailDestinationConfig,
) : Destination {

    override val displayName: String = config.displayName.ifBlank { "Mail" }
    override val type: DestinationType = DestinationType.Mail
    override val enabled: Boolean = config.enabled && config.isConfiguredForSending()

    override fun composeMessage(context: DeliveryContext): String = buildString {
        appendLine("Subject: ${buildSubject(context)}")
        appendLine("From: ${config.fromAddress}")
        appendLine("To: ${config.recipientList().joinToString()}")
        appendLine()
        append(buildBody(context))
    }.trim()

    override suspend fun send(context: DeliveryContext): Result<Unit> = withContext(Dispatchers.IO) {
        if (!enabled) {
            return@withContext Result.failure(
                IllegalStateException("Mail destination disabled or not configured"),
            )
        }

        val protocol = transportProtocol()
        Log.i(TAG, "send start: ${config.smtpHost}:${config.smtpPort} mode=${config.securityMode} protocol=$protocol")

        try {
            withTimeout(SEND_TIMEOUT_MS) {
                sendInternal(context, protocol)
            }
            Log.i(TAG, "send success")
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            val message = "Таймаут ${SEND_TIMEOUT_MS / 1000} сек: ${config.smtpHost}:${config.smtpPort}. " +
                "Проверьте Wi‑Fi/моб. сеть (оператор может блокировать SMTP) и режим SSL/STARTTLS."
            Log.e(TAG, message, e)
            Result.failure(IllegalStateException(message))
        } catch (e: Exception) {
            Log.e(TAG, "send failed: ${MailErrors.format(e)}", e)
            Result.failure(IllegalStateException(MailErrors.format(e), e))
        }
    }

    private fun sendInternal(context: DeliveryContext, protocol: String) {
        val session = Session.getInstance(buildMailProperties(protocol), object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(config.username, config.password)
        }).apply {
            debug = BuildConfig.DEBUG
        }

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(config.fromAddress))
            config.recipientList().forEach { recipient ->
                addRecipient(Message.RecipientType.TO, InternetAddress(recipient))
            }
            subject = buildSubject(context)
            setText(buildBody(context), Charsets.UTF_8.name())
        }

        val transport = session.getTransport(protocol)
        transport.use {
            Log.d(TAG, "connecting to ${config.smtpHost}:${config.smtpPort} as ${config.username}")
            it.connect(config.smtpHost, config.smtpPort, config.username, config.password)
            Log.d(TAG, "connected, sending message")
            it.sendMessage(message, message.allRecipients)
        }
    }

    private fun transportProtocol(): String =
        if (config.securityMode == MailSecurityMode.SSL) SMTPS_PROTOCOL else SMTP_PROTOCOL

    private fun Transport.use(block: (Transport) -> Unit) {
        try {
            block(this)
        } finally {
            runCatching { close() }
        }
    }

    private fun buildMailProperties(protocol: String): Properties = Properties().apply {
        val prefix = if (protocol == SMTPS_PROTOCOL) "mail.smtps." else "mail.smtp."

        put("mail.transport.protocol", protocol)
        put("${prefix}host", config.smtpHost)
        put("${prefix}port", config.smtpPort.toString())
        put("${prefix}auth", "true")
        applyMailTimeouts(prefix)
        put("${prefix}ssl.trust", config.smtpHost)

        when (config.securityMode) {
            MailSecurityMode.NONE -> Unit
            MailSecurityMode.STARTTLS -> {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
            MailSecurityMode.SSL -> {
                put("mail.smtps.ssl.enable", "true")
                put("mail.smtps.ssl.protocols", "TLSv1.2")
            }
        }

        if (config.pop3Host.isNotBlank()) {
            put("mail.pop3.host", config.pop3Host)
            put("mail.pop3.port", config.pop3Port.toString())
        }
    }

    private fun Properties.applyMailTimeouts(prefix: String) {
        put("${prefix}connectiontimeout", SEND_TIMEOUT_MS.toString())
        put("${prefix}timeout", SEND_TIMEOUT_MS.toString())
        put("${prefix}writetimeout", SEND_TIMEOUT_MS.toString())
    }

    private fun buildSubject(context: DeliveryContext): String {
        val previous = context.previousDeliveredIp
        return if (previous != null && previous != context.ip) {
            "IP Notifier: $previous → ${context.ip}"
        } else {
            "IP Notifier: ${context.ip}"
        }
    }

    private fun buildBody(context: DeliveryContext): String = buildString {
        appendLine("External IP update")
        appendLine()
        appendLine("Current IP: ${context.ip}")
        context.previousDeliveredIp?.let { previous ->
            if (previous != context.ip) {
                appendLine("Previous delivered IP: $previous")
            }
        }
        appendLine()
        appendLine("Sent by IP Notifier")
    }

    private companion object {
        const val TAG = "MailDestination"
        const val SMTP_PROTOCOL = "smtp"
        const val SMTPS_PROTOCOL = "smtps"
        const val SEND_TIMEOUT_MS = 20_000L
    }
}

object MailErrors {

    fun format(error: Throwable?): String {
        if (error == null) return "Неизвестная ошибка"

        val parts = linkedSetOf<String>()
        var current: Throwable? = error
        while (current != null) {
            current.message?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
            current = when (current) {
                is MessagingException -> current.nextException ?: current.cause
                else -> current.cause
            }
        }
        val raw = parts.joinToString("\n").ifBlank { error.javaClass.simpleName }
        return enrichYandexHint(raw)
    }

    private fun enrichYandexHint(message: String): String {
        val lower = message.lowercase()
        val isAuthDenied = lower.contains("authentication failed") ||
            lower.contains("535") ||
            lower.contains("access rights")
        if (!isAuthDenied) return message

        return buildString {
            appendLine(message.trim())
            appendLine()
            appendLine("Yandex не дал доступ к SMTP. Нужно:")
            appendLine("1. Почта → Настройки → Почтовые программы")
            appendLine("   включить доступ с паролем приложения")
            appendLine("2. На id.yandex.ru → Безопасность")
            appendLine("   создать «Пароль приложения» (Почта)")
            appendLine("3. В приложении вставить именно этот пароль")
            appendLine("   (не обычный пароль от аккаунта)")
        }.trim()
    }
}
