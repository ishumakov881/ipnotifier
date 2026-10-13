package com.example.ipnotifier

import java.net.HttpURLConnection
import java.net.URL

object SimpleHttp {

    private const val TIMEOUT_MS = 8_000
    private const val USER_AGENT = "IpNotifier (Android)"

    fun get(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code from $urlString")
            }
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
