package com.metrolist.music.wavy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

data class LyricLine(val ms: Long, val text: String)

private val lyricCache = HashMap<String, List<LyricLine>>()

private fun parseLrc(s: String): List<LyricLine> {
    val out = ArrayList<LyricLine>()
    val re = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]")
    for (raw in s.lines()) {
        val ms = re.findAll(raw).toList()
        if (ms.isEmpty()) continue
        val text = raw.replace(re, "").trim()
        for (m in ms) {
            val frac = m.groupValues[3]
            val f = if (frac.isEmpty()) 0L else (frac.padEnd(3, '0').take(3)).toLong()
            out.add(LyricLine(m.groupValues[1].toLong() * 60000 + m.groupValues[2].toLong() * 1000 + f, text))
        }
    }
    return out.filter { it.text.isNotEmpty() }.sortedBy { it.ms }
}

/** Synced lyrics from LRCLIB (free, no key). Empty list when there is no match, so the screen stays clean. */
suspend fun fetchLyrics(id: String, title: String, artist: String, durSec: Int): List<LyricLine> = withContext(Dispatchers.IO) {
    lyricCache[id]?.let { return@withContext it }
    var res: List<LyricLine> = emptyList()
    try {
        val clean = title.replace(Regex("\\(.*?\\)|\\[.*?]"), "").trim()
        val q = URLEncoder.encode("$clean ${artist.split(",", "&").first().trim()}", "UTF-8")
        val c = URL("https://lrclib.net/api/search?q=$q").openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Wavy v1 (private app for friends)"); c.connectTimeout = 6000; c.readTimeout = 8000
        if (c.responseCode == 200) {
            val a = JSONArray(c.inputStream.bufferedReader().readText())
            var best: String? = null; var bd = Int.MAX_VALUE
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i); val s = o.optString("syncedLyrics", "")
                if (s.isBlank() || o.isNull("syncedLyrics")) continue
                val d = if (durSec > 0 && o.optDouble("duration", 0.0) > 0) abs(o.optDouble("duration") - durSec).toInt() else 0
                if (d < bd) { bd = d; best = s }
            }
            if (best != null && (durSec <= 0 || bd <= 6)) res = parseLrc(best)
        }
    } catch (e: Exception) { }
    lyricCache[id] = res
    res
}
