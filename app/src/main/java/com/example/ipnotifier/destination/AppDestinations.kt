package com.example.ipnotifier.destination

import android.content.Context
import com.example.ipnotifier.settings.DestinationSlotsStore
import com.example.ipnotifier.settings.MailSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

object AppDestinations {

    fun flow(context: Context): Flow<List<Destination>> {
        val appContext = context.applicationContext
        return combine(
            MailSettingsStore(appContext).settingsFlow,
            DestinationSlotsStore(appContext).extraTypesFlow,
        ) { mailSettings, extraTypes ->
            buildList {
                add(
                    MailDestination(
                        id = MAIL_ID,
                        config = mailSettings,
                    ),
                )
                extraTypes.sortedBy { it.ordinal }.forEach { type ->
                    add(placeholderFor(type))
                }
            }
        }
    }

    suspend fun load(context: Context): List<Destination> = flow(context).first()

    fun availableToAdd(existing: List<Destination>): List<DestinationType> =
        DestinationType.entries.filter { type ->
            existing.none { it.type == type }
        }

    private fun placeholderFor(type: DestinationType): Destination = when (type) {
        DestinationType.Mail -> error("Mail is not a placeholder slot")
        DestinationType.Telegram -> PlaceholderDestination(
            id = "telegram_main",
            displayName = "Telegram",
            type = DestinationType.Telegram,
        )
        DestinationType.FirebaseMessaging -> PlaceholderDestination(
            id = "firebase_main",
            displayName = "Firebase",
            type = DestinationType.FirebaseMessaging,
        )
    }

    const val MAIL_ID = "mail_main"
}
