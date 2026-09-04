package com.example.ipnotifier

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object IpFetcher {

    private val endpoints = listOf(
        "https://api.ipify.org",
        "https://icanhazip.com",
    )

    suspend fun fetchPublicIp(): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (endpoint in endpoints) {
            try {
                return@withContext fetchFrom(endpoint)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No IP endpoints available")
    }

    private fun fetchFrom(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code from $urlString")
            }
            return connection.inputStream.bufferedReader().readText().trim()
        } finally {
            connection.disconnect()
        }
    }
}
