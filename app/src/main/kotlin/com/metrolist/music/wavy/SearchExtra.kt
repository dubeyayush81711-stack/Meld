package com.metrolist.music.wavy

import android.content.Context
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.YouTubeQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Live YouTube Music song search: finds any song in any language and spelling. */
suspend fun liveSongs(q: String): JSONArray = withContext(Dispatchers.IO) {
    val out = JSONArray()
    try {
        val r = YouTube.search(q, YouTube.SearchFilter.FILTER_SONG).getOrNull() ?: return@withContext out
        r.items.filterIsInstance<SongItem>().take(25).forEach { s ->
            out.put(JSONObject().put("yt_video_id", s.id).put("title", s.title).put("artist", s.artists.joinToString { it.name })
                .put("cover_url", s.thumbnail).put("duration_s", s.duration ?: 0).put("artist_id", s.artists.firstOrNull()?.id ?: JSONObject.NULL).put("album", s.album?.name ?: JSONObject.NULL))
        }
    } catch (e: Exception) { }
    out
}

/** Plays the song, then YouTube Music's radio for it (same language/style, same artist, popular songs). */
fun playRadio(pc: PlayerConnection?, t: Track) {
    if (pc == null) return
    pc.playQueue(YouTubeQueue(WatchEndpoint(videoId = t.id, playlistId = "RDAMVM" + t.id), t.toMeta()))
}

private fun prefs(ctx: Context) = ctx.getSharedPreferences("wavy_search", Context.MODE_PRIVATE)
private fun key() = "hist_" + Api.uid

fun loadHistory(ctx: Context): List<String> = try {
    val a = JSONArray(prefs(ctx).getString(key(), "[]")); (0 until a.length()).map { a.getString(it) }
} catch (e: Exception) { emptyList() }

private fun store(ctx: Context, l: List<String>) { prefs(ctx).edit().putString(key(), JSONArray(l).toString()).apply() }

fun saveHistory(ctx: Context, q: String) { try { TasteProfile.searched(q) } catch (e: Exception) { }; store(ctx, (listOf(q) + loadHistory(ctx).filter { !it.equals(q, true) }).take(30)) }
fun removeHistory(ctx: Context, q: String): List<String> { val l = loadHistory(ctx).filter { it != q }; store(ctx, l); return l }
fun clearHistory(ctx: Context) { store(ctx, emptyList()) }

val searchCache = object : LinkedHashMap<String, JSONObject>(32, 0.75f, true) { override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JSONObject>?) = size > 40 }

/** Live YouTube Music results first (its own relevance), catalog songs merged in, then exact title/artist matches float to the top. */
fun rankSearch(q: String, cat: JSONObject?, live: JSONArray): JSONObject {
    val o = JSONObject()
    cat?.keys()?.forEach { k -> o.put(k, cat.get(k)) }
    val toks = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val have = HashSet<String>(); val all = ArrayList<JSONObject>()
    for (i in 0 until live.length()) live.getJSONObject(i).let { if (have.add(it.optString("yt_video_id"))) all.add(it) }
    cat?.optJSONArray("songs")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { if (have.add(it.optString("yt_video_id"))) all.add(it) } }
    fun score(x: JSONObject): Int {
        val t = x.optString("title").lowercase(); val h = t + " " + x.optString("artist").lowercase()
        var sc = 0
        if (t == q.lowercase()) sc += 4
        if (toks.isNotEmpty() && toks.all { h.contains(it) }) sc += 3
        if (t.startsWith(q.lowercase())) sc += 2
        if (t.contains("cover") || t.contains("karaoke") || t.contains("slowed") || t.contains("reverb")) sc -= 2
        return sc
    }
    val sorted = all.withIndex().sortedWith(compareByDescending<IndexedValue<JSONObject>> { score(it.value) }.thenBy { it.index }).map { it.value }
    o.put("songs", JSONArray(sorted))
    return o
}
