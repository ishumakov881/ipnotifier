package com.example.ipnotifier.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ipnotifier.destination.MailDestinationConfig
import com.example.ipnotifier.destination.MailSecurityMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.mailSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mail_settings",
)

class MailSettingsStore(
    private val context: Context,
) {

    val settingsFlow: Flow<MailDestinationConfig> =
        context.mailSettingsDataStore.data.map { prefs -> prefs.toMailSettings() }

    suspend fun getSettings(): MailDestinationConfig =
        settingsFlow.first()

    suspend fun save(settings: MailDestinationConfig) {
        context.mailSettingsDataStore.edit { prefs ->
            prefs[Keys.ENABLED] = settings.enabled
            prefs[Keys.DISPLAY_NAME] = settings.displayName
            prefs[Keys.SMTP_HOST] = settings.smtpHost
            prefs[Keys.SMTP_PORT] = settings.smtpPort
            prefs[Keys.POP3_HOST] = settings.pop3Host
            prefs[Keys.POP3_PORT] = settings.pop3Port
            prefs[Keys.USERNAME] = settings.username
            prefs[Keys.PASSWORD] = settings.password
            prefs[Keys.FROM_ADDRESS] = settings.fromAddress
            prefs[Keys.TO_ADDRESSES] = settings.toAddresses
            prefs[Keys.SECURITY_MODE] = settings.securityMode.name
        }
    }

    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val SMTP_HOST = stringPreferencesKey("smtp_host")
        val SMTP_PORT = intPreferencesKey("smtp_port")
        val POP3_HOST = stringPreferencesKey("pop3_host")
        val POP3_PORT = intPreferencesKey("pop3_port")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val FROM_ADDRESS = stringPreferencesKey("from_address")
        val TO_ADDRESSES = stringPreferencesKey("to_addresses")
        val SECURITY_MODE = stringPreferencesKey("security_mode")
    }

    private fun Preferences.toMailSettings(): MailDestinationConfig =
        MailDestinationConfig(
            enabled = this[Keys.ENABLED] ?: false,
            displayName = this[Keys.DISPLAY_NAME] ?: "Main",
            smtpHost = this[Keys.SMTP_HOST] ?: "",
            smtpPort = this[Keys.SMTP_PORT] ?: 587,
            pop3Host = this[Keys.POP3_HOST] ?: "",
            pop3Port = this[Keys.POP3_PORT] ?: 995,
            username = this[Keys.USERNAME] ?: "",
            password = this[Keys.PASSWORD] ?: "",
            fromAddress = this[Keys.FROM_ADDRESS] ?: "",
            toAddresses = this[Keys.TO_ADDRESSES] ?: "",
            securityMode = runCatching {
                MailSecurityMode.valueOf(this[Keys.SECURITY_MODE] ?: MailSecurityMode.STARTTLS.name)
            }.getOrDefault(MailSecurityMode.STARTTLS),
        )
}
