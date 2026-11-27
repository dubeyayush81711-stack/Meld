package com.metrolist.music.wavy

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class Pod(val name: String, val author: String, val art: String?, val feed: String)
data class Ep(val title: String, val url: String, val dur: Int, val date: String)

private val podHttp = OkHttpClient.Builder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).readTimeout(20, java.util.concurrent.TimeUnit.SECONDS).followRedirects(true).build()
private fun podGet(url: String): String? = try { podHttp.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()).execute().use { if (it.isSuccessful) it.body?.string() else null } } catch (e: Exception) { null }

/** Public podcast directory search (no key). Returns shows that have a real RSS feed. */
suspend fun searchPods(term: String): List<Pod> = withContext(Dispatchers.IO) {
    val body = podGet("https://itunes.apple.com/search?media=podcast&entity=podcast&limit=20&term=" + java.net.URLEncoder.encode(term, "UTF-8")) ?: return@withContext emptyList()
    try {
        val a = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
        (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("feedUrl").startsWith("http") }
            .map { Pod(it.optString("collectionName"), it.optString("artistName"), it.optString("artworkUrl600").ifBlank { null }, it.optString("feedUrl").replaceFirst("http://", "https://")) }
    } catch (e: Exception) { emptyList() }
}

private fun durSec(s: String?): Int {
    if (s.isNullOrBlank()) return 0
    val p = s.trim().split(":").mapNotNull { it.toIntOrNull() }
    return when (p.size) { 1 -> p[0]; 2 -> p[0] * 60 + p[1]; 3 -> p[0] * 3600 + p[1] * 60 + p[2]; else -> 0 }
}

suspend fun loadEpisodes(feed: String): List<Ep> = withContext(Dispatchers.IO) {
    val xml = podGet(feed) ?: return@withContext emptyList()
    val out = ArrayList<Ep>()
    try {
        val p = android.util.Xml.newPullParser()
        p.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(java.io.StringReader(xml))
        var inItem = false; var title = ""; var url = ""; var dur = ""; var date = ""
        var ev = p.eventType
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && out.size < 60) {
            if (ev == org.xmlpull.v1.XmlPullParser.START_TAG) {
                when (p.name) {
                    "item" -> { inItem = true; title = ""; url = ""; dur = ""; date = "" }
                    "title" -> if (inItem) title = p.nextText().trim()
                    "enclosure" -> if (inItem) url = p.getAttributeValue(null, "url") ?: ""
                    "itunes:duration" -> if (inItem) dur = p.nextText()
                    "pubDate" -> if (inItem) date = p.nextText().trim()
                }
            } else if (ev == org.xmlpull.v1.XmlPullParser.END_TAG && p.name == "item") {
                inItem = false
                if (url.startsWith("http") && title.isNotBlank()) out.add(Ep(title, url.replaceFirst("http://", "https://"), durSec(dur), date.take(16)))
            }
            ev = p.next()
        }
    } catch (e: Exception) { }
    out
}

/** Follows redirects (tracking prefixes, http to https) so the player gets the final audio address. Returns the original on any failure. */
suspend fun finalUrl(u: String): String = withContext(Dispatchers.IO) {
    try { podHttp.newCall(Request.Builder().url(u).head().header("User-Agent", "Mozilla/5.0").build()).execute().use { r -> if (r.isSuccessful) r.request.url.toString() else u } } catch (e: Exception) { u }
}

fun playEpisodes(pc: PlayerConnection?, pod: Pod, eps0: List<Ep>, index: Int) {
    if (pc == null || eps0.isEmpty()) return
    val eps = eps0
    val items = eps.map { e ->
        val id = "local:pod" + Integer.toHexString(e.url.hashCode())
        Track(id, e.title, pod.name, pod.art, e.dur, null, pod.name).toMeta().toMediaItem().buildUpon().setUri(e.url).build()
    }
    pc.playQueue(com.metrolist.music.playback.queues.ListQueue(title = pod.name, items = items, startIndex = index.coerceIn(0, items.size - 1)))
}

@Composable
fun PodcastsTab(pc: PlayerConnection?) {
    var term by remember { mutableStateOf("") }
    var shows by remember { mutableStateOf<List<Pod>?>(null) }
    var label by remember { mutableStateOf("Popular searches") }
    var open by remember { mutableStateOf<Pod?>(null) }
    var eps by remember { mutableStateOf<List<Ep>?>(null) }
    val defaults = remember { listOf("Hindi", "Comedy", "News India", "Motivation", "Technology") }
    var sections by remember { mutableStateOf<List<Pair<String, List<Pod>>>>(emptyList()) }
    LaunchedEffect(Unit) { sections = defaults.map { it to searchPods(it).take(8) }.filter { it.second.isNotEmpty() } }
    LaunchedEffect(open) { eps = null; open?.let { eps = loadEpisodes(it.feed) } }
    androidx.activity.compose.BackHandler(enabled = open != null) { open = null }
    val sc = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val o = open
        if (o != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(o.art, 96.dp, 12)
                Column(Modifier.padding(start = 14.dp)) { Text(o.name, style = ts(20, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis); Text(o.author, style = ts(14, color = W.Dim), maxLines = 1) }
            }
            Text("Back to podcasts", Modifier.padding(vertical = 12.dp).clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF)).clickable { open = null }.padding(16.dp, 8.dp), style = ts(14, FontWeight.Medium))
            val e = eps
            if (e == null) Text("Loading episodes...", Modifier.padding(8.dp), style = ts(14, color = W.Dim))
            else if (e.isEmpty()) Text("This feed has no playable episodes.", Modifier.padding(8.dp), style = ts(14, color = W.Dim))
            else e.forEachIndexed { i, ep ->
                Row(Modifier.fillMaxWidth().clickable { sc.launch { val fixed = e.toMutableList(); fixed[i] = e[i].copy(url = finalUrl(e[i].url)); playEpisodes(pc, o, fixed, i) } }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ep.title, style = ts(16, FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOf(ep.date, if (ep.dur > 0) "${ep.dur / 60} min" else "").filter { it.isNotBlank() }.joinToString(" \u00B7 "), style = ts(13, color = W.Dim))
                    }
                    Text("Play", Modifier.padding(start = 10.dp).clip(RoundedCornerShape(50)).background(W.Pink).padding(14.dp, 7.dp), style = ts(13, FontWeight.SemiBold))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF)).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (term.isEmpty()) Text("Search podcasts", style = ts(16, color = Color(0xAAFFFFFF)))
                    BasicTextField(term, { term = it }, singleLine = true, textStyle = ts(16), cursorBrush = SolidColor(W.Pink), modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { val t = term.trim(); if (t.isNotEmpty()) { label = "Results for \"$t\""; shows = null; sc.launch { shows = searchPods(t) } } }))
                }
            }
            Spacer(Modifier.height(14.dp))
            val sr = shows
            if (term.isNotBlank() && label.startsWith("Results")) {
                Text(label, style = ts(20, FontWeight.SemiBold)); Spacer(Modifier.height(8.dp))
                if (sr == null) Text("Searching...", style = ts(14, color = W.Dim))
                else if (sr.isEmpty()) Text("No podcasts found.", style = ts(14, color = W.Dim))
                else sr.forEach { PodRow(it) { open = it } }
            } else {
                if (sections.isEmpty()) Text("Loading podcasts...", Modifier.padding(8.dp), style = ts(14, color = W.Dim))
                sections.forEach { (n, l) ->
                    Text(n, Modifier.padding(top = 10.dp, bottom = 8.dp), style = ts(20, FontWeight.SemiBold))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        l.forEach { p -> Column(Modifier.width(120.dp).clickable { open = p }) { Cover(p.art, 120.dp, 12); Text(p.name, Modifier.padding(top = 6.dp), style = ts(13, FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodRow(p: Pod, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(p.art, 64.dp, 10)
        Column(Modifier.padding(start = 14.dp)) { Text(p.name, style = ts(16, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis); Text(p.author, style = ts(13, color = W.Dim), maxLines = 1) }
    }
}
