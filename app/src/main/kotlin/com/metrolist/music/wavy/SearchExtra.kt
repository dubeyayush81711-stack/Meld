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

fun saveHistory(ctx: Context, q: String) { store(ctx, (listOf(q) + loadHistory(ctx).filter { !it.equals(q, true) }).take(30)) }
fun removeHistory(ctx: Context, q: String): List<String> { val l = loadHistory(ctx).filter { it != q }; store(ctx, l); return l }
fun clearHistory(ctx: Context) { store(ctx, emptyList()) }
