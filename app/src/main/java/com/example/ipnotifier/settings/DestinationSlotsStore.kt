package com.example.ipnotifier.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ipnotifier.destination.DestinationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.destinationSlotsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "destination_slots",
)

/** Extra destination types the user added via «+» (Mail is always present separately). */
class DestinationSlotsStore(
    private val context: Context,
) {

    val extraTypesFlow: Flow<Set<DestinationType>> =
        context.destinationSlotsDataStore.data.map { prefs ->
            prefs[Keys.EXTRA_TYPES]
                .orEmpty()
                .mapNotNull { name ->
                    runCatching { DestinationType.valueOf(name) }.getOrNull()
                }
                .filter { it != DestinationType.Mail }
                .toSet()
        }

    suspend fun add(type: DestinationType) {
        if (type == DestinationType.Mail) return
        context.destinationSlotsDataStore.edit { prefs ->
            val current = prefs[Keys.EXTRA_TYPES].orEmpty().toMutableSet()
            current.add(type.name)
            prefs[Keys.EXTRA_TYPES] = current
        }
    }

    private object Keys {
        val EXTRA_TYPES = stringSetPreferencesKey("extra_types")
    }
}
