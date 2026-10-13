package com.example.ipnotifier.geo

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

object IpGeoLocator {

    private const val TAG = "IpGeoLocator"

    // Richest data first; poorer providers are fallbacks only. Among country-only ones
    // Cloudflare goes first: country.is has been seen to report a wrong country.
    private val providers: List<IpGeoProvider> = listOf(
        IpWhoIsProvider,
        IpApiCoProvider,
        FreeIpApiProvider,
        CloudflareTraceProvider,
        CountryIsProvider,
    )

    suspend fun lookup(ip: String): IpGeoInfo? = withContext(Dispatchers.IO) {
        for (provider in providers) {
            ensureActive()
            try {
                return@withContext provider.lookup(ip).also {
                    Log.i(TAG, "${provider.name}: $ip -> ${it.countryCode} ${it.isp ?: ""}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "${provider.name} failed for $ip: ${e.message}")
            }
        }
        null
    }
}
