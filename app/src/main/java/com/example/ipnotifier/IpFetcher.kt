package com.example.ipnotifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object IpFetcher {

    private val endpoints = listOf(
        "https://api.ipify.org",
        "https://icanhazip.com",
    )

    suspend fun fetchPublicIp(): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (endpoint in endpoints) {
            try {
                return@withContext SimpleHttp.get(endpoint).trim()
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No IP endpoints available")
    }
}
