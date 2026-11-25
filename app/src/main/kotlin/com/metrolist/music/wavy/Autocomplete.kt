package com.metrolist.music.wavy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private val acHttp = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()

/** Typo-tolerant suggestions from YouTube's public autocomplete (same engine as Google). Free, no key. */
suspend fun autocomplete(q: String): List<String> = withContext(Dispatchers.IO) {
    try {
        val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=en&q=" + URLEncoder.encode(q, "UTF-8")
        val body = acHttp.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()).execute().use { it.body?.string() ?: "" }
        val a = JSONArray(body).optJSONArray(1) ?: return@withContext emptyList()
        (0 until a.length()).map { a.optString(it) }
    } catch (e: Exception) { emptyList() }
}
