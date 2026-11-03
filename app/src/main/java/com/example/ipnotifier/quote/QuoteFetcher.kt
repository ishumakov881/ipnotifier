package com.example.ipnotifier.quote

import android.util.Log
import com.example.ipnotifier.SimpleHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Best-effort random quote for delivery messages.
 * Failures are swallowed — mail must still go out without a quote.
 */
object QuoteFetcher {

    private const val TAG = "QuoteFetcher"

    suspend fun fetchRandom(): Quote? = withContext(Dispatchers.IO) {
        for (provider in providers) {
            runCatching { provider() }
                .onSuccess { quote -> if (quote != null) return@withContext quote }
                .onFailure { Log.w(TAG, "quote provider failed", it) }
        }
        null
    }

    private val providers: List<() -> Quote?> = listOf(
        ::fromDummyJson,
        ::fromZenQuotes,
    )

    /** https://dummyjson.com/quotes/random → { quote, author } */
    private fun fromDummyJson(): Quote? {
        val json = JSONObject(SimpleHttp.get("https://dummyjson.com/quotes/random"))
        val text = json.optString("quote").trim()
        if (text.isEmpty()) return null
        return Quote(
            text = text,
            author = json.optString("author").trim().ifEmpty { null },
            source = "dummyjson.com",
        )
    }

    /** https://zenquotes.io/api/random → [{ "q", "a" }] */
    private fun fromZenQuotes(): Quote? {
        val array = JSONArray(SimpleHttp.get("https://zenquotes.io/api/random"))
        if (array.length() == 0) return null
        val item = array.getJSONObject(0)
        val text = item.optString("q").trim()
        if (text.isEmpty() || text.contains("Too many requests", ignoreCase = true)) return null
        return Quote(
            text = text,
            author = item.optString("a").trim().ifEmpty { null },
            source = "zenquotes.io",
        )
    }
}
