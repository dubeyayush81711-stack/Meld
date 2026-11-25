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

private val moodCache = HashMap<String, List<Track>>()
private var myLangs: List<String>? = null

/** The languages the user picked in onboarding (saved in user_prefs). */
suspend fun userLanguages(): List<String> {
    myLangs?.let { return it }
    val l = try {
        val a = (Api.get("user_prefs?user_id=eq.${Api.uid}&select=languages") as? JSONArray)?.optJSONObject(0)?.optJSONArray("languages")
        if (a == null) emptyList() else (0 until a.length()).map { a.getString(it).lowercase() }
    } catch (e: Exception) { emptyList() }
    if (l.isNotEmpty()) myLangs = l
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
fun MoodRow(title: String, mood: String, pc: PlayerConnection?) {
    var tracks by remember { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(mood) {
        val langs = userLanguages().ifEmpty { listOf("hindi") }.take(2)
        val key = mood + "|" + langs.joinToString(",")
        moodCache[key]?.let { tracks = it; return@LaunchedEffect }
        val res = try {
            coroutineScope { langs.map { lg -> async { liveSongs(query(mood, lg)).tracks() } }.map { it.await() } }
        } catch (e: Exception) { emptyList() }
        val merged = (0 until (res.maxOfOrNull { it.size } ?: 0)).flatMap { i -> res.mapNotNull { it.getOrNull(i) } }
            .filter { !skip.containsMatchIn(it.title) }.distinctBy { it.id }.take(20)
        if (merged.isNotEmpty()) moodCache[key] = merged
        tracks = merged
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
