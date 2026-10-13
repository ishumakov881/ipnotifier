package com.example.ipnotifier.destination

import android.content.Context
import com.example.ipnotifier.settings.MailSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object AppDestinations {

    fun flow(context: Context): Flow<List<Destination>> =
        MailSettingsStore(context).settingsFlow.map { mailSettings ->
            buildList {
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

    suspend fun load(context: Context): List<Destination> = flow(context).first()
}
