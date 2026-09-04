package com.example.ipnotifier.destination

import android.content.Context
import com.example.ipnotifier.settings.MailSettingsStore

object AppDestinations {

    suspend fun load(context: Context): List<Destination> {
        val mailSettings = MailSettingsStore(context).getSettings()
        return buildList {
            add(
                MailDestination(
                    id = "mail_main",
                    config = mailSettings,
                ),
            )
            // add(TelegramDestination(...))
            // add(FcmDestination(...))
        }
    }
}
