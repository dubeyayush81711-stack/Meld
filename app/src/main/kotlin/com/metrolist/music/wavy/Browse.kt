package com.metrolist.music.wavy

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gitems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class LangTile(val lang: String, val title: String, val count: Int, val top: String)

private val langColors = mapOf(
    "punjabi" to listOf(Color(0xFFE94E8B), Color(0xFF8A1E45)), "international" to listOf(Color(0xFF8B5CF6), Color(0xFF3A2A8C)),
    "hindi" to listOf(Color(0xFFF59E42), Color(0xFF8A4A1E)), "bhojpuri" to listOf(Color(0xFF3FBF8A), Color(0xFF1E6E48)),
    "haryanvi" to listOf(Color(0xFFE2C23A), Color(0xFF7A6A22)), "south" to listOf(Color(0xFF3F9CF0), Color(0xFF1E3E8A)),
)

@Composable
fun SearchScreen(pc: PlayerConnection?, onLang: (LangTile) -> Unit, onProfile: () -> Unit, initial: String) {
    var q by remember { mutableStateOf("") }
    var tiles by remember { mutableStateOf<List<LangTile>>(emptyList()) }
    var res by remember { mutableStateOf<JSONObject?>(null) }
    var filter by remember { mutableStateOf("All") }
    val scopeS = rememberCoroutineScope()
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val arr = Api.rpc("language_tiles") as? JSONArray ?: return@LaunchedEffect
            tiles = coroutineScope {
                (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
                    async {
                        val top = try { (Api.rpc("language_top", JSONObject().put("p_lang", o.getString("lang")).put("p_limit", 2)) as? JSONArray).tracks().joinToString(", ") { it.title } } catch (e: Exception) { "" }
                        LangTile(o.getString("lang"), o.getString("title"), o.optInt("song_count"), top)
                    }
                }.map { it.await() }
            }
        } catch (e: Exception) { }
    }
    var sugg by remember { mutableStateOf<List<String>>(emptyList()) }
    val ctxS = androidx.compose.ui.platform.LocalContext.current
    var hist by remember { mutableStateOf(loadHistory(ctxS)) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { sugg = emptyList(); return@LaunchedEffect }
        delay(150)
        val ytm = try { com.metrolist.innertube.YouTube.searchSuggestions(q.trim()).getOrNull()?.queries ?: emptyList() } catch (e: Exception) { emptyList() }
        val ac = autocomplete(q.trim())
        sugg = (ytm.take(3) + ac + ytm.drop(3)).map { it.trim() }.filter { it.isNotEmpty() && !it.equals(q.trim(), true) }.distinctBy { it.lowercase() }.take(6)
    }
    var submitted by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<List<SSection>?>(null) }
    var typed by remember { mutableStateOf<List<com.metrolist.innertube.models.YTItem>?>(null) }
    var nextTok by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showMic by remember { mutableStateOf(false) }
    val speech = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!said.isNullOrBlank()) { q = said; submitted = said.trim(); filter = "All"; saveHistory(ctxS, said.trim()) }
    }
    fun listen(prompt: String) {
        showMic = false
        try {
            speech.launch(android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, prompt))
        } catch (e: Exception) { err = "Voice input is not available on this phone." }
    }
    fun go(text: String) { val t = text.trim(); if (t.length < 1) return; q = t; filter = "All"; submitted = t; saveHistory(ctxS, t); hist = loadHistory(ctxS) }
    LaunchedEffect(submitted, filter) {
        val sq = submitted ?: return@LaunchedEffect
        err = null; busy = true
        try {
            if (filter == "All") { typed = null; summary = meldSummary(sq) }
            else { summary = null; typed = null; val p = meldTyped(sq, filter); typed = p.items; nextTok = p.next }
        } catch (e: Exception) { err = "Search failed. Check your connection." }
        busy = false
    }
    val meta by (pc?.mediaMetadata ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val playing by (pc?.isPlaying ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    if (showMic) MicScreen(onClose = { showMic = false }, onResult = { said -> showMic = false; q = said; submitted = said; filter = "All"; saveHistory(ctxS, said); hist = loadHistory(ctxS) })
    Backdrop(tint = Color(0xFF2B0B3A)) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 210.dp)) {
            col {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).height(56.dp).glass(RoundedCornerShape(50)).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.search), null, Modifier.size(24.dp), tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            if (q.isEmpty()) Text("Search", style = ts(18, color = Color(0xCCFFFFFF)))
                            BasicTextField(q, { q = it; submitted = null }, singleLine = true, textStyle = ts(18), cursorBrush = SolidColor(W.Pink), modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { go(q) }))
                        }
                        if (q.isNotEmpty()) Icon(painterResource(R.drawable.close), null, Modifier.size(22.dp).clickable { q = ""; submitted = null }, tint = Color.White)
                        Icon(painterResource(R.drawable.mic), null, Modifier.padding(start = 10.dp).size(24.dp).clickable { showMic = true }, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Avatar(40, onProfile)
                }
                Spacer(Modifier.height(16.dp))
            }
            if (q.isEmpty() && hist.isNotEmpty()) col {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent searches", Modifier.weight(1f), style = ts(20, FontWeight.SemiBold))
                    Text("Clear all", Modifier.clickable { clearHistory(ctxS); hist = emptyList() }.padding(8.dp), style = ts(14, FontWeight.Medium, W.Pink))
                }
                hist.forEach { h ->
                    Row(Modifier.fillMaxWidth().clickable { go(h) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.history), null, Modifier.size(20.dp), tint = W.Dim)
                        Text(h, Modifier.weight(1f).padding(start = 14.dp), style = ts(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(painterResource(R.drawable.close), null, Modifier.size(34.dp).clip(CircleShape).clickable { hist = removeHistory(ctxS, h) }.padding(7.dp), tint = W.Dim)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            if (q.isEmpty()) {
                val m = meta
                if (m != null) col {
                    Text("Continue Listening", style = ts(24, FontWeight.SemiBold)); Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(W.Pink).clickable { pc?.togglePlayPause() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(m.thumbnailUrl, 56.dp, 8)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(m.title, style = ts(18, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (playing) "Playing" else "Paused", style = ts(14, color = Color(0xFFFFD6E4)))
                        }
                        Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), null, Modifier.size(34.dp), tint = Color.White)
                    }
                    Spacer(Modifier.height(26.dp))
                }
                if (tiles.isNotEmpty()) col {
                    Text("Browse by language", style = ts(24, FontWeight.SemiBold)); Spacer(Modifier.height(12.dp))
                    tiles.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { t ->
                                val c = langColors[t.lang] ?: listOf(W.Pink, W.Maroon)
                                val lctx = LocalContext.current
                                val rid = remember(t.lang) { lctx.resources.getIdentifier("lang_" + t.lang, "drawable", lctx.packageName) }
                                Box(Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(c)).clickable { onLang(t) }) {
                                    if (rid != 0) {
                                        Image(painterResource(rid), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x55000000), 1f to Color(0xCC000000))))
                                    }
                                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                                        Text(t.title, style = ts(22, FontWeight.SemiBold), maxLines = 1, softWrap = false)
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else if (submitted == null) {
                val ql = q.trim().lowercase()
                val hm = hist.filter { it.lowercase().contains(ql) && !it.equals(q.trim(), true) }.take(3)
                val rows = (hm.map { it to true } + sugg.filter { s0 -> hm.none { it.equals(s0, true) } }.map { it to false }).take(8)
                col {
                    rows.forEach { (sg, isHist) ->
                        Row(Modifier.fillMaxWidth().clickable { go(sg) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(if (isHist) R.drawable.history else R.drawable.search), null, Modifier.size(20.dp), tint = W.Dim)
                            Text(sg, Modifier.weight(1f).padding(start = 14.dp), style = ts(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(painterResource(R.drawable.arrow_forward), null, Modifier.size(18.dp).clickable { q = sg }, tint = W.Dim)
                        }
                    }
                }
            } else {
                col {
                    Row(Modifier.padding(bottom = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("All", "Songs", "Videos", "Albums", "Artists", "Playlists").forEach { f ->
                            Text(f, Modifier.then(if (f == filter) Modifier.clip(RoundedCornerShape(50)).background(Color.White) else Modifier.glass(RoundedCornerShape(50))).clickable { filter = f }.padding(horizontal = 18.dp, vertical = 9.dp), style = ts(15, FontWeight.Medium, if (f == filter) Color.Black else Color.White), maxLines = 1, softWrap = false)
                        }
                    }
                }
                val sm = summary; val ty = typed
                if (filter == "All" && sm != null) {
                    val songsAll = sm.flatMap { it.items }.mapNotNull { it.asTrack() }
                    sm.forEach { sec ->
                        col { Text(sec.title, Modifier.padding(top = 14.dp, bottom = 6.dp), style = ts(20, FontWeight.SemiBold)) }
                        sec.items.forEach { it0 -> col { ResultRow(it0, pc, songsAll, onArtist = { n -> go(n); filter = "Songs" }) } }
                    }
                    if (sm.isEmpty() && !busy) col { Text(err ?: "No results.", style = ts(15, color = W.Dim)) }
                } else if (ty != null) {
                    val songsAll = ty.mapNotNull { it.asTrack() }
                    ty.forEach { it0 -> col { ResultRow(it0, pc, songsAll, onArtist = { n -> go(n); filter = "Songs" }) } }
                    if (nextTok != null) col { Text("Load more", Modifier.padding(vertical = 14.dp).clip(RoundedCornerShape(50)).glass(RoundedCornerShape(50)).clickable { val tk = nextTok; nextTok = null; if (tk != null) scopeS.launch { val p = meldMore(tk); typed = (typed.orEmpty() + p.items).distinctBy { it.id }; nextTok = p.next } }.padding(horizontal = 22.dp, vertical = 10.dp), style = ts(14, FontWeight.Medium)) }
                    if (ty.isEmpty()) col { Text(err ?: "No results.", style = ts(15, color = W.Dim)) }
                }
                if (busy) col { Text("Searching...", Modifier.padding(vertical = 16.dp), style = ts(14, color = W.Dim)) }
            }
        }
    }
}

@Composable
fun ResultRow(it0: com.metrolist.innertube.models.YTItem, pc: PlayerConnection?, songs: List<Track>, onArtist: (String) -> Unit) {
    val t = it0.asTrack()
    if (t != null) { TrackRow(t, pc) { playTracks(pc, songs, songs.indexOfFirst { it.id == t.id }.coerceAtLeast(0), "Search") }; return }
    val artist = it0 is com.metrolist.innertube.models.ArtistItem
    Row(Modifier.fillMaxWidth().clickable { if (artist) onArtist(it0.title) else playItem(pc, it0) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(it0.thumbnail, 56.dp, if (artist) 50 else 10)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(it0.title, style = ts(16, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(it0.sub(), style = ts(13, color = W.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Avatar(size: Int, onClick: () -> Unit) {
    var name by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { try { name = ((Api.get("profiles?id=eq.${Api.uid}&select=name") as? JSONArray)?.optJSONObject(0)?.optString("name")).orEmpty().ifBlank { Api.email } } catch (e: Exception) { } }
    val av = rememberAvatar(androidx.compose.ui.platform.LocalContext.current)
    Box(Modifier.size(size.dp).clip(CircleShape).background(W.Grad).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (av != null) Image(av.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.take(1).uppercase().ifEmpty { "W" }, style = ts(size / 2, FontWeight.Bold)) }
}

@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.arrow_back), null, Modifier.size(30.dp).clickable(onClick = onBack), tint = Color.White)
        Text(title, Modifier.weight(1f), style = ts(20, FontWeight.SemiBold), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.size(30.dp))
    }
}

@Composable
fun TrackListScreen(title: String, pc: PlayerConnection?, onBack: () -> Unit, load: suspend () -> List<Track>) {
    var list by remember { mutableStateOf<List<Track>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { try { list = load() } catch (e: ApiError) { err = humanErr(e) } }
    Backdrop(tint = Color(0xFF2B0B3A)) {
        Column(Modifier.fillMaxSize()) {
            BackBar(title, onBack)
            val l = list
            if (l == null) Text(err ?: "Loading...", Modifier.padding(24.dp), style = ts(15, color = W.Dim))
            else if (l.isEmpty()) Text("Nothing here yet.", Modifier.padding(24.dp), style = ts(15, color = W.Dim))
            else LazyColumn(contentPadding = PaddingValues(bottom = 210.dp)) {
                if (title == "Favourites" && pc != null) col { FavPlayer(pc) }
                col { Row(Modifier.padding(20.dp, 4.dp, 20.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${l.size} songs", Modifier.weight(1f), style = ts(14, color = W.Dim))
                    Text("Play all", Modifier.clip(RoundedCornerShape(50)).background(W.Pink).clickable { playTracks(pc, l, 0, title) }.padding(20.dp, 9.dp), style = ts(14, FontWeight.SemiBold)) } }
                l.forEachIndexed { i, t -> col { TrackRow(t, pc) { playTracks(pc, l, i, title) } } }
            }
        }
    }
}

suspend fun loadPlaylistTracks(id: Long): List<Track> {
    val rows = Api.get("playlist_tracks?playlist_id=eq.$id&select=track_id,position&order=position.asc&limit=200") as? JSONArray ?: return emptyList()
    val ids = (0 until rows.length()).map { rows.getJSONObject(it).getString("track_id") }
    if (ids.isEmpty()) return emptyList()
    val inList = ids.joinToString(",") { "\"" + it + "\"" }
    val t = Api.get("tracks?yt_video_id=in.(${java.net.URLEncoder.encode(inList, "UTF-8")})&select=yt_video_id,title,artist,cover_url,thumbnail_url,duration_s,artist_id,album") as? JSONArray
    val by = (0 until (t?.length() ?: 0)).map { t!!.getJSONObject(it).toTrack() }.associateBy { it.id }
    return ids.mapNotNull { by[it] }
}

@Composable
fun LibraryScreen(pc: PlayerConnection?, onProfile: () -> Unit, onPlaylist: (Long, String) -> Unit, onAddArtists: () -> Unit) {
    var lists by remember { mutableStateOf<List<Triple<Long, String, String?>>>(emptyList()) }
    var artists by remember { mutableStateOf<List<ArtistItem>>(emptyList()) }
    var chip by remember { mutableStateOf("Playlists") }
    LaunchedEffect(Unit) {
        try {
            val s = Api.rpc("profile_stats") as? JSONObject
            val a = s?.optJSONArray("playlists")
            if (a != null) lists = (0 until a.length()).map { a.getJSONObject(it) }.map { Triple(it.getLong("id"), if (it.optBoolean("is_favourite")) "Favourites" else it.optString("name"), if (it.isNull("cover_url")) null else it.optString("cover_url")) }
            artists = (Api.get("user_artists?select=artists(id,name,thumbnail_url)&order=created_at.asc") as? JSONArray)?.let { r ->
                (0 until r.length()).mapNotNull { r.getJSONObject(it).optJSONObject("artists") }.map { ArtistItem(it.optString("id"), it.optString("name"), if (it.isNull("thumbnail_url")) null else it.optString("thumbnail_url")) } } ?: emptyList()
        } catch (e: Exception) { }
    }
    Backdrop(streaks = false) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 210.dp)) {
            col { Row(Modifier.padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(44, onProfile)
                Text("Your Library", Modifier.padding(start = 14.dp).weight(1f), style = ts(28, FontWeight.SemiBold))
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Playlists", "Artists", "Downloads").forEach { c -> Text(c, Modifier.clip(RoundedCornerShape(50)).background(if (c == chip) W.Pink else Color(0x2EFFFFFF)).clickable { chip = c }.padding(horizontal = 20.dp, vertical = 10.dp), style = ts(15, FontWeight.Medium)) }
            }
            Spacer(Modifier.height(14.dp)) }
            if (chip == "Downloads") col {
                val dls by (WDl.util?.downloads ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())).collectAsState()
                val ids = WDl.doneIds(dls)
                val tl = remember(ids) { ids.mapNotNull { WDl.toTrack(it) } }
                if (tl.isEmpty()) Text("Open a song in the player, tap the tune icon and choose Download. Downloaded songs play offline.", Modifier.padding(24.dp), style = ts(15, color = W.Dim))
                else {
                    Row(Modifier.padding(20.dp, 4.dp, 20.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${tl.size} downloaded", Modifier.weight(1f), style = ts(14, color = W.Dim))
                        Text("Play all", Modifier.clip(RoundedCornerShape(50)).background(W.Pink).clickable { playTracks(pc, tl, 0, "Downloads") }.padding(20.dp, 9.dp), style = ts(14, FontWeight.SemiBold))
                    }
                    tl.forEachIndexed { i, t -> TrackRow(t, pc) { playTracks(pc, tl, i, "Downloads") } }
                }
            } else if (chip == "Playlists") {
                lists.forEach { (id, name, cover) -> col {
                    Row(Modifier.fillMaxWidth().clickable { onPlaylist(id, name) }.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (cover != null) Cover(cover, 64.dp, 8) else Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(W.Grad), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.favorite), null, Modifier.size(30.dp), tint = Color.White) }
                        Column(Modifier.padding(start = 16.dp)) { Text(name, style = ts(17, FontWeight.SemiBold)); Text("Playlist", style = ts(14, color = W.Dim)) }
                    } } }
                if (lists.isEmpty()) col { Text("Songs you play land in Favourites. Playlists you add show here.", Modifier.padding(24.dp), style = ts(15, color = W.Dim)) }
            } else col {
                artists.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { a -> Column(Modifier.weight(1f)) { Cover(a.thumb, 110.dp, 55, Modifier.align(Alignment.CenterHorizontally))
                            Text(a.name, Modifier.padding(top = 8.dp), style = ts(15, FontWeight.SemiBold), maxLines = 2); Text("Artist", style = ts(13, color = W.Dim)) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Column(Modifier.padding(16.dp).width(110.dp).clickable(onClick = onAddArtists), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(110.dp).clip(CircleShape).background(Color(0x2EFFFFFF)), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.add), null, Modifier.size(36.dp), tint = Color(0xFFBDB8C4)) }
                    Text("Add artists", Modifier.padding(top = 8.dp), style = ts(15, FontWeight.SemiBold))
                }
            }
        }
    }
}


// Player strip shown on top of the Favourites page.
@Composable
fun FavPlayer(pc: PlayerConnection) {
    val meta by pc.mediaMetadata.collectAsState()
    val playing by pc.isPlaying.collectAsState()
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(meta?.id) { while (true) { pos = pc.player.currentPosition; kotlinx.coroutines.delay(500) } }
    val m = meta ?: return
    val dur = (pc.player.duration.takeIf { it > 0 } ?: (m.duration * 1000L)).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).glass(RoundedCornerShape(18.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            coil3.compose.AsyncImage(m.thumbnailUrl, null, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(m.title, style = ts(18, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.artists.joinToString { it.name }, style = ts(14, color = W.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(painterResource(R.drawable.skip_previous), null, Modifier.size(34.dp).clickable { pc.seekToPrevious() }, tint = Color.White)
            Box(Modifier.padding(horizontal = 8.dp).size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White).clickable { if (pc.player.isPlaying) pc.player.pause() else pc.player.play() }, contentAlignment = Alignment.Center) {
                Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), null, Modifier.size(26.dp), tint = Color.Black) }
            Icon(painterResource(R.drawable.skip_next), null, Modifier.size(34.dp).clickable { pc.seekToNext() }, tint = Color.White)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x55FFFFFF))) { Box(Modifier.fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f)).fillMaxHeight().background(W.Pink)) }
    }
}
