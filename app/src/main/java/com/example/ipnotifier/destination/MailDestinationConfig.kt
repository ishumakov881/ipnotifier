package com.example.ipnotifier.destination

enum class MailSecurityMode {
    NONE,
    STARTTLS,
    SSL,
}

data class MailDestinationConfig(
    val enabled: Boolean = false,
    val displayName: String = "Main",
    val smtpHost: String = "",
    val smtpPort: Int = 587,
    val pop3Host: String = "",
    val pop3Port: Int = 995,
    val username: String = "",
    val password: String = "",
    val fromAddress: String = "",
    val toAddresses: String = "",
    val securityMode: MailSecurityMode = MailSecurityMode.STARTTLS,
) {
    fun isConfiguredForSending(): Boolean =
        smtpHost.isNotBlank() &&
            username.isNotBlank() &&
            password.isNotBlank() &&
            fromAddress.isNotBlank() &&
            toAddresses.isNotBlank()

    fun recipientList(): List<String> =
        toAddresses.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
