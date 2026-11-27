package com.metrolist.music.wavy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object HomeRefresh { var n by mutableIntStateOf(0) }
private val moodCache = HashMap<String, List<Track>>()
private var myLangs: List<String>? = null

/** The languages the user picked in onboarding (saved in user_prefs). */
suspend fun userLanguages(): List<String> {
    myLangs?.let { return it }
    val saved = S.s("my_langs_" + (Api.uid ?: "")).split(",").filter { it.isNotBlank() }
    if (saved.isNotEmpty()) { myLangs = saved; return saved }
    val l = try {
        val a = (Api.get("user_prefs?user_id=eq.${Api.uid}&select=languages") as? JSONArray)?.optJSONObject(0)?.optJSONArray("languages")
        if (a == null) emptyList() else (0 until a.length()).map { a.getString(it).lowercase() }
    } catch (e: Exception) { emptyList() }
    if (l.isNotEmpty()) { myLangs = l; S.put("my_langs_" + (Api.uid ?: ""), l.joinToString(",")) }
    return l
}

private val skip = Regex("(?i)cover|karaoke|slowed|reverb|8d|sped up|instrumental|remix")

private fun query(mood: String, lang: String): String {
    val l = if (lang == "south") "tamil telugu" else lang
    return when (mood) {
        "chill" -> "chill $l songs"
        "party" -> if (lang == "hindi") "bollywood party dance songs" else "$l party dance songs"
        else -> "$l top hits songs"
    }
}

/** A Home row filled by live YouTube Music searches for a mood in the user's languages (cached; drops covers, karaoke, slowed and reverb). */
@Composable
fun clearMoodCache() { moodCache.clear() }

@Composable
fun MoodRow(title: String, mood: String, pc: PlayerConnection?) {
    var tracks by remember { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(mood, HomeRefresh.n) {
        val langs = userLanguages().ifEmpty { listOf("hindi") }.take(2)
        val key = mood + "|" + langs.joinToString(",")
        if (HomeRefresh.n == 0) moodCache[key]?.let { tracks = LangGuard.filter(it); return@LaunchedEffect }
        try {
            val a = JSONArray(S.s("mood_$key", "[]"))
            if (a.length() > 0) if (HomeRefresh.n == 0) tracks = LangGuard.filter((0 until a.length()).map { val o = a.getJSONObject(it); Track(o.getString("i"), o.getString("t"), o.getString("a"), o.optString("c").ifBlank { null }, o.optInt("d"), o.optString("x").ifBlank { null }, o.optString("l").ifBlank { null }) })
        } catch (_: Exception) { }
        val res = try {
            coroutineScope { langs.map { lg -> async { liveSongs(query(mood, lg)).tracks() } }.map { it.await() } }
        } catch (e: Exception) { emptyList() }
        val merged = (0 until (res.maxOfOrNull { it.size } ?: 0)).flatMap { i -> res.mapNotNull { it.getOrNull(i) } }
            .filter { !skip.containsMatchIn(it.title) }.distinctBy { it.id }.let { LangGuard.filter(it) }.let { m -> val f = Played.filter(m); (if (f.size >= 8) f else m).take(40).chunked(10).flatMap { it.shuffled() }.take(20) }
        if (merged.isNotEmpty()) {
            moodCache[key] = merged
            try { val a = JSONArray(); merged.forEach { a.put(JSONObject().put("i", it.id).put("t", it.title).put("a", it.artist).put("c", it.cover ?: "").put("d", it.dur).put("x", it.artistId ?: "").put("l", it.album ?: "")) }; S.put("mood_$key", a.toString()) } catch (_: Exception) { }
            tracks = merged
        } else if (tracks == null) tracks = merged
    }
    val l = tracks
    if (l.isNullOrEmpty()) return
    Column {
        SectionHeader(title)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(l) { i, t -> MiniCard(t.cover, t.title, t.artist) { playTracks(pc, l, i, title) } }
        }
        Spacer(Modifier.height(28.dp))
    }
}
