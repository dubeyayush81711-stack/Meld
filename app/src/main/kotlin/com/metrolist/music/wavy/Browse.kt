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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var trending by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(Unit) {
        // real chart rank (cross-list: YouTube Music + Instagram + Spotify India), best first, only the user's languages; re-read every 30 min while open
        while (true) {
            try { val got = LangGuard.filter((Api.rpc("player_trending", JSONObject().put("p_limit", 30).put("p_region", "IN")) as? JSONArray).tracks()).take(10); if (got.isNotEmpty()) trending = got } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { }
            kotlinx.coroutines.delay(30 * 60_000L)
        }
    }
    val ctxS = androidx.compose.ui.platform.LocalContext.current
    var hist by remember { mutableStateOf(loadHistory(ctxS)) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { sugg = emptyList(); return@LaunchedEffect }
        delay(150)
        val ytm = try { com.metrolist.innertube.YouTube.searchSuggestions(q.trim()).getOrNull()?.queries ?: emptyList() } catch (e: Exception) { emptyList() }
        val ac = autocomplete(q.trim())
        sugg = TasteProfile.rankSuggestions((ytm.take(3) + ac + ytm.drop(3)).map { it.trim() }.filter { it.isNotEmpty() && !it.equals(q.trim(), true) }.distinctBy { it.lowercase() }).take(6)
    }
    var liveSongs by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(q) {
        if (q.trim().length < 2) { liveSongs = emptyList(); return@LaunchedEffect }
        delay(300)
        val qq = q.trim()
        val got = withContext(Dispatchers.IO) {
            try {
                val items = com.metrolist.innertube.YouTube.search(qq, com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty()
                rankTyped(qq, items).mapNotNull { it.asTrack() }.filter { LangGuard.ok(it) && TasteProfile.affinity(it.artist) >= -2.5 }.distinctBy { normKey(it) }.take(10)
            } catch (e: Exception) { emptyList<Track>() }
        }
        liveSongs = got
    }
    var submitted by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<List<SSection>?>(null) }
    var typed by remember { mutableStateOf<List<com.metrolist.innertube.models.YTItem>?>(null) }
    var nextTok by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showMic by remember { mutableStateOf(false) }
    var vscores by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    LaunchedEffect(summary, typed) {
        val ids = ((summary?.flatMap { it.items } ?: emptyList()) + (typed ?: emptyList())).filterIsInstance<com.metrolist.innertube.models.SongItem>().map { it.id }.distinct().take(15)
        if (ids.size < 2) { vscores = emptyMap(); return@LaunchedEffect }
        try { Fame.warm(((summary?.flatMap { it.items } ?: emptyList()) + (typed ?: emptyList())).filterIsInstance<com.metrolist.innertube.models.SongItem>().flatMap { s -> s.artists.map { it.id } }) } catch (e: Exception) { }
        vscores = try { viewScores(ids) } catch (e: Exception) { emptyMap() }
    }
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
    val kbc = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(submitted, filter) {
        val sq = submitted ?: return@LaunchedEffect
        err = null; busy = true
        try {
            if (filter == "All") { typed = null; summary = meldSummary(sq) }
            else { summary = null; typed = null; val p = meldTyped(sq, filter); typed = p.items; nextTok = p.next }
        } catch (e: Exception) { err = "Search failed. Check your connection." }
        busy = false
        try { kbc?.hide() } catch (e: Exception) { }
    }
    val meta by (pc?.mediaMetadata ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val playing by (pc?.isPlaying ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    if (showMic) MicScreen(onClose = { showMic = false }, onResult = { said -> showMic = false; q = said; submitted = said; filter = "All"; saveHistory(ctxS, said); hist = loadHistory(ctxS) })
    var searchFocused by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(false) }
    val focusReq = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.activity.compose.BackHandler(enabled = page) { page = false; q = ""; submitted = null }
    LaunchedEffect(searchFocused) { if (searchFocused) page = true }
    LaunchedEffect(page) { if (page) { try { focusReq.requestFocus() } catch (e: Exception) { } } }
    Box(Modifier.fillMaxSize().background(W.Bg)) {
        WallpaperLayer("search")
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 210.dp)) {
            col {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (page && q.isEmpty()) { Icon(painterResource(R.drawable.arrow_back), null, Modifier.padding(end = 12.dp).size(28.dp).clickable { page = false; q = ""; submitted = null }, tint = Color.White) }
                    Row(Modifier.weight(1f).height(56.dp).glass(RoundedCornerShape(50)).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.search), null, Modifier.size(24.dp), tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            if (q.isEmpty()) Text(if (page) "Songs, artists, albums" else "Search", style = ts(17, color = Color(0x99FFFFFF)), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            BasicTextField(q, { q = it; submitted = null }, singleLine = true, textStyle = ts(18), cursorBrush = SolidColor(W.Pink), modifier = Modifier.fillMaxWidth().focusRequester(focusReq).onFocusChanged { searchFocused = it.isFocused }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { go(q) }))
                        }
                        if (q.isNotEmpty()) Icon(painterResource(R.drawable.close), null, Modifier.size(22.dp).clickable { q = ""; submitted = null }, tint = Color.White)
                        if (q.isEmpty()) Icon(painterResource(R.drawable.mic), null, Modifier.padding(start = 10.dp).size(24.dp).clickable { showMic = true }, tint = Color.White)
                    }
                    if (!page) { Spacer(Modifier.width(12.dp)); Avatar(40, onProfile) }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (q.isEmpty() && page && hist.isEmpty()) col {
                Column(Modifier.fillMaxWidth().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Play what you love", style = ts(22, FontWeight.Bold))
                    Spacer(Modifier.height(6.dp))
                    Text("Search for artists, songs, playlists.", style = ts(15, color = W.Dim))
                }
            }
            if (q.isEmpty() && hist.isNotEmpty() && page) col {
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
            if (q.isEmpty() && !page && trending.isNotEmpty()) col {
                Text("Trending now", Modifier.padding(bottom = 4.dp), style = ts(20, FontWeight.SemiBold))
            }
            if (q.isEmpty() && !page) trending.forEachIndexed { i, t -> col { TrackRow(t, pc) { playTracks(pc, trending, i, "Trending now") } } }
            if (q.isEmpty() && !page) col { Spacer(Modifier.height(18.dp)) }
            if (q.isEmpty() && !page) {
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
                                val rid = when (t.lang) { "punjabi" -> R.drawable.lang_punjabi; "hindi" -> R.drawable.lang_hindi; "bhojpuri" -> R.drawable.lang_bhojpuri; "haryanvi" -> R.drawable.lang_haryanvi; "international" -> R.drawable.lang_international; "south" -> R.drawable.lang_south; else -> 0 }
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
                    col {
                    Text("Browse all", Modifier.padding(bottom = 12.dp), style = ts(20, FontWeight.SemiBold))
                    listOf("Pop" to 0xFFE8115B, "Hip-hop" to 0xFF8D67AB, "Romance" to 0xFFE91E63, "Party" to 0xFFF59B23, "Workout" to 0xFF1E3264, "Chill" to 0xFF477D95,
                        "Sad songs" to 0xFF503750, "Devotional" to 0xFFBA5D07, "Focus" to 0xFF148A08, "Retro" to 0xFF7D4B32, "Rock" to 0xFFBC5900, "Dance" to 0xFF0D73EC).chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { (name, c) ->
                                Box(Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(c), Color(c).copy(alpha = 0.55f)))).clickable { page = true; go(name + " songs") }.padding(14.dp)) {
                                    Text(name, style = ts(17, FontWeight.Bold), modifier = Modifier.align(Alignment.TopStart))
                                }
                            }
                        }
                    }
                }
            } else if (submitted == null) {
                val ql = q.trim().lowercase()
                val hm = hist.filter { it.lowercase().contains(ql) && !it.equals(q.trim(), true) }.take(3)
                val fy = if (ql.isEmpty()) TasteProfile.forYouSuggestions(6) else emptyList()
                val rows = if (ql.isEmpty()) fy.map { it to hist.any { h -> h.equals(it, true) } } else (hm.map { it to true } + sugg.filter { s0 -> hm.none { it.equals(s0, true) } }.map { it to false }).take(8)
                if (ql.isNotEmpty()) liveSongs.forEachIndexed { i, t -> col(key = "ls_" + t.id) { TrackRow(t, pc) { try { kbc?.hide() } catch (e: Exception) { }; playTracks(pc, liveSongs, i, "Search") } } }
                col {
                    (if (ql.isNotEmpty() && liveSongs.isNotEmpty()) rows.take(3) else rows).forEach { (sg, isHist) ->
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
                val sm = summary?.map { SSection(it.title, byViews(dedupeItems(it.items), vscores)) }; val ty = typed?.let { byViews(dedupeItems(it), vscores) }
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
    Row(Modifier.fillMaxWidth().clickable { if (artist) (ArtistNav.open?.invoke(it0.id, it0.title) ?: onArtist(it0.title)) else playItem(pc, it0) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Box(Modifier.fillMaxSize().background(W.Bg)) {
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(pc: PlayerConnection?, onProfile: () -> Unit, onPlaylist: (Long, String) -> Unit, onAddArtists: () -> Unit) {
    var lists by remember { mutableStateOf<List<Triple<Long, String, String?>>>(emptyList()) }
    var artists by remember { mutableStateOf<List<ArtistItem>>(emptyList()) }
    var chip by remember { mutableStateOf("Playlists") }
    val ctx = LocalContext.current
    val orderPrefs = remember { ctx.getSharedPreferences("wavy_order", 0) }
    var menuFor by remember { mutableStateOf<Triple<Long, String, String?>?>(null) }
    val scope = rememberCoroutineScope()
    fun applyOrder(l: List<Triple<Long, String, String?>>): List<Triple<Long, String, String?>> {
        val ord = (orderPrefs.getString("pl", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }
        return l.sortedBy { val i = ord.indexOf(it.first); if (i < 0) Int.MAX_VALUE else i }
    }
    fun move(id: Long, d: Int) {
        val cur = lists.toMutableList(); val i = cur.indexOfFirst { it.first == id }; val j = i + d
        if (i < 0 || j < 0 || j >= cur.size) return
        val x = cur.removeAt(i); cur.add(j, x); lists = cur
        orderPrefs.edit().putString("pl", cur.joinToString(",") { it.first.toString() }).apply()
    }
    LaunchedEffect(Unit) {
        try {
            val s = Api.rpc("profile_stats") as? JSONObject
            val a = s?.optJSONArray("playlists")
            if (a != null) lists = (0 until a.length()).map { a.getJSONObject(it) }.map { Triple(it.getLong("id"), if (it.optBoolean("is_favourite")) "Favourites" else it.optString("name"), if (it.isNull("cover_url")) null else it.optString("cover_url")) }.let { l -> applyOrder(l) }
            artists = (Api.get("user_artists?select=artists(id,name,thumbnail_url)&order=created_at.asc") as? JSONArray)?.let { r ->
                (0 until r.length()).mapNotNull { r.getJSONObject(it).optJSONObject("artists") }.map { ArtistItem(it.optString("id"), it.optString("name"), if (it.isNull("thumbnail_url")) null else it.optString("thumbnail_url")) } } ?: emptyList()
        } catch (e: Exception) { }
    }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var az by remember { mutableStateOf(false) }
    var asList by remember { mutableStateOf(false) }
    val q = query.trim().lowercase()
    Box(Modifier.fillMaxSize().background(W.Bg)) {
        WallpaperLayer("library")
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 210.dp)) {
            col { Row(Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(38, onProfile)
                if (searching) {
                    BasicTextField(query, { query = it }, Modifier.padding(start = 14.dp).weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A)).padding(12.dp, 9.dp), singleLine = true,
                        textStyle = ts(16), cursorBrush = SolidColor(Color.White))
                    Icon(painterResource(R.drawable.close), null, Modifier.padding(start = 12.dp).size(28.dp).clickable { query = ""; searching = false }, tint = Color.White)
                } else {
                    Text("Your Library", Modifier.padding(start = 14.dp).weight(1f), style = ts(24, FontWeight.Bold))
                    Icon(painterResource(R.drawable.search), null, Modifier.size(30.dp).clickable { searching = true }, tint = Color.White)
                    Icon(painterResource(R.drawable.add), null, Modifier.padding(start = 22.dp).size(32.dp).clickable(onClick = onAddArtists), tint = Color.White)
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Playlists", "Artists", "Following", "Downloads", "Ringtones").forEach { c -> Text(c, Modifier.clip(RoundedCornerShape(50)).background(if (c == chip) W.Pink else Color(0xFF2A2A2A)).clickable { chip = c }.padding(horizontal = 16.dp, vertical = 8.dp), style = ts(14, FontWeight.Medium), maxLines = 1, softWrap = false) }
            }
            if (chip != "Downloads" && chip != "Ringtones" && chip != "Following") Row(Modifier.fillMaxWidth().padding(16.dp, 14.dp, 16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable { az = !az }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.wavy_sort), null, Modifier.size(22.dp), tint = Color.White)
                    Text(if (az) "A-Z" else "Recents", Modifier.padding(start = 12.dp), style = ts(15, FontWeight.Bold))
                }
                if (chip == "Artists") Icon(painterResource(if (asList) R.drawable.grid_view else R.drawable.list), null, Modifier.size(24.dp).clickable { asList = !asList }, tint = Color.White)
            }
            Spacer(Modifier.height(6.dp)) }
            if (chip == "Playlists" && q.isEmpty()) col { LibTiles(lists.firstOrNull { it.second == "Favourites" }?.first, onPlaylist) { chip = it } }
            if (chip == "Top 50" || chip == "History") col {
                LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Recent.load() } }
                val rl = Recent.list
                val tl = if (chip == "Top 50") rl.filter { PlayCount.of(it.id) > 0 }.sortedByDescending { PlayCount.of(it.id) }.take(50) else rl
                if (tl.isEmpty()) Text("Nothing here yet. Play some songs and they show up.", Modifier.padding(24.dp), style = ts(15, color = W.Dim))
                else {
                    Row(Modifier.padding(20.dp, 4.dp, 20.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${tl.size} songs", Modifier.weight(1f), style = ts(14, color = W.Dim))
                        Text("Play all", Modifier.clip(RoundedCornerShape(50)).background(W.Pink).clickable { playTracks(pc, tl, 0, chip) }.padding(20.dp, 9.dp), style = ts(14, FontWeight.SemiBold))
                    }
                    tl.forEachIndexed { i, t -> TrackRow(t, pc) { playTracks(pc, tl, i, chip) } }
                }
            } else if (chip == "Following") col {
                val fl = followedArtistIds().map { Triple(it, S.s("fname_$it"), S.s("fthumb_$it").ifBlank { null }) }.filter { it.second.isNotBlank() }
                if (fl.isEmpty()) Text("Artists you follow show here. Open an artist and tap Follow. Their popular songs get extra weight in your picks.", Modifier.padding(24.dp), style = ts(15, color = W.Dim))
                else fl.forEach { (id, nm, th) -> Row(Modifier.fillMaxWidth().clickable { ArtistNav.open?.invoke(id, nm) }.padding(16.dp, 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(th, 64.dp, 32)
                    Column(Modifier.padding(start = 16.dp)) { Text(nm, style = ts(17, FontWeight.SemiBold), maxLines = 1); Text("Following", style = ts(14, color = W.Dim)) } } }
            } else if (chip == "Ringtones") col { TonesTab() } else if (chip == "Downloads") col {
                val dls by (WDl.util?.downloads ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())).collectAsState()
                val ids = WDl.doneIds(dls)
                val tl by produceState(emptyList<Track>(), ids) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ids.mapNotNull { WDl.toTrack(it) } } }
                if (tl.isEmpty()) Text("Open a song in the player, tap the tune icon and choose Download. Downloaded songs play offline.", Modifier.padding(24.dp), style = ts(15, color = W.Dim))
                else {
                    Row(Modifier.padding(20.dp, 4.dp, 20.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${tl.size} downloaded", Modifier.weight(1f), style = ts(14, color = W.Dim))
                        Text("Play all", Modifier.clip(RoundedCornerShape(50)).background(W.Pink).clickable { playTracks(pc, tl, 0, "Downloads") }.padding(20.dp, 9.dp), style = ts(14, FontWeight.SemiBold))
                    }
                    tl.forEachIndexed { i, t -> TrackRow(t, pc) { playTracks(pc, tl, i, "Downloads") } }
                }
            } else if (chip == "Playlists") {
                (if (az) lists.sortedBy { it.second.lowercase() } else lists).filter { q.isEmpty() || it.second.lowercase().contains(q) }.forEach { (id, name, cover) -> col {
                    Row(Modifier.fillMaxWidth().combinedClickable(onClick = { onPlaylist(id, name) }, onLongClick = { menuFor = Triple(id, name, cover) }).padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (cover != null) Cover(cover, 64.dp, 8) else Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(W.Grad), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.favorite), null, Modifier.size(30.dp), tint = Color.White) }
                        Column(Modifier.padding(start = 16.dp)) { Text(name, style = ts(17, FontWeight.SemiBold)); Text("Playlist", style = ts(14, color = W.Dim)) }
                    } } }
                if (lists.size > 1) col { Text("Hold a playlist to move or delete it", Modifier.padding(20.dp, 6.dp), style = ts(13, color = W.Dim)) }
                if (lists.isEmpty()) col { Text("Songs you play land in Favourites. Playlists you add show here.", Modifier.padding(24.dp), style = ts(15, color = W.Dim)) }
            } else col {
                val shown = (if (az) artists.sortedBy { it.name.lowercase() } else artists).filter { q.isEmpty() || it.name.lowercase().contains(q) }
                if (asList) {
                    shown.forEach { a -> Row(Modifier.fillMaxWidth().clickable { ArtistNav.open?.invoke(a.id, a.name) }.padding(16.dp, 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(a.thumb, 64.dp, 32)
                        Column(Modifier.padding(start = 16.dp)) { Text(a.name, style = ts(17, FontWeight.SemiBold), maxLines = 1); Text("Artist", style = ts(14, color = W.Dim)) } } }
                    Row(Modifier.fillMaxWidth().clickable(onClick = onAddArtists).padding(16.dp, 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.add), null, Modifier.size(28.dp), tint = Color(0xFFBDB8C4)) }
                        Text("Add artists", Modifier.padding(start = 16.dp), style = ts(17, FontWeight.SemiBold)) }
                } else {
                    val cells = shown.map { it as ArtistItem? } + listOf<ArtistItem?>(null)
                    cells.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { a ->
                                if (a != null) Column(Modifier.weight(1f).clickable { ArtistNav.open?.invoke(a.id, a.name) }) { Cover(a.thumb, 108.dp, 54, Modifier.align(Alignment.CenterHorizontally))
                                    Text(a.name, Modifier.padding(top = 8.dp), style = ts(16), maxLines = 2); Text("Artist", style = ts(14, color = W.Dim)) }
                                else Column(Modifier.weight(1f).clickable(onClick = onAddArtists)) {
                                    Box(Modifier.size(108.dp).align(Alignment.CenterHorizontally).clip(CircleShape).background(Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.add), null, Modifier.size(36.dp), tint = Color(0xFFBDB8C4)) }
                                    Text("Add artists", Modifier.padding(top = 8.dp), style = ts(16)) }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }

    menuFor?.let { m ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { menuFor = null }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF1A1620)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp)).padding(vertical = 14.dp)) {
                Text(m.second, Modifier.padding(20.dp, 6.dp, 20.dp, 10.dp), style = ts(18, FontWeight.SemiBold), maxLines = 1)
                Text("Move up", Modifier.fillMaxWidth().clickable { move(m.first, -1); menuFor = null }.padding(20.dp, 14.dp), style = ts(16))
                Text("Move down", Modifier.fillMaxWidth().clickable { move(m.first, 1); menuFor = null }.padding(20.dp, 14.dp), style = ts(16))
                if (m.second != "Favourites") Text("Delete playlist", Modifier.fillMaxWidth().clickable {
                    val id = m.first; menuFor = null
                    scope.launch { try { Api.delete("playlists?id=eq.$id"); lists = lists.filter { it.first != id } } catch (e: Exception) { } }
                }.padding(20.dp, 14.dp), style = ts(16, color = Color(0xFFFF6B6B)))
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

/** Endless list for one language: backend chart first (Most viewed) or fresh searches (Latest), then older famous songs from YouTube Music, deduped, paged with continuation tokens. */
class LangPager(val code: String, val title: String, val latest: Boolean) {
    val seen = HashSet<String>()
    private var off = 0; private var backendDone = latest
    private var cont: String? = null; private var qi = 0
    private val queries: List<String> = (if (latest) listOf("new $title songs 2026", "latest $title songs", "$title new releases") else emptyList()) +
        listOf("best $title songs of all time", "$title evergreen hits", "top $title songs 2025", "$title hit songs 2024", "$title old classics", "$title hit songs 2023", "$title superhit songs 2022", "$title songs 2021", "$title songs 2020", "$title hits 2019", "$title hits 2018", "$title hits 2017", "$title hits 2016", "$title hits 2015")
    var exhausted = false
    suspend fun more(): List<Track> {
        val out = ArrayList<Track>()
        var guard = 0
        while (out.size < 15 && !exhausted && guard++ < 6) {
            var got: List<Track> = emptyList()
            if (!backendDone) {
                got = try { (Api.rpc("language_top", JSONObject().put("p_lang", code).put("p_limit", 30).put("p_offset", off)) as? JSONArray).tracks() } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; emptyList() }
                off += 30; if (got.size < 30) backendDone = true
            } else {
                val r = withContext(Dispatchers.IO) {
                    try {
                        val c = cont
                        if (c != null) com.metrolist.innertube.YouTube.searchContinuation(c).getOrNull()?.let { it.items to it.continuation }
                        else if (qi < queries.size) com.metrolist.innertube.YouTube.search(queries[qi++], com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()?.let { it.items to it.continuation }
                        else null
                    } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; null }
                }
                if (r == null) { if (cont != null) cont = null else if (qi >= queries.size) exhausted = true }
                else { cont = r.second; got = r.first.mapNotNull { it.asTrack() } }
            }
            got.forEach { if (seen.add(it.id)) out.add(it) }
        }
        return Fame.lift(out)
    }
}

/** Language tile screen: "Most viewed" and "Latest hits" are both endless. */
@Composable
fun LangListScreen(title: String, code: String, pc: PlayerConnection?, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val pagers = remember(code) { listOf(LangPager(code, title, false), LangPager(code, title, true)) }
    val lists = remember(code) { listOf(mutableStateListOf<Track>(), mutableStateListOf<Track>()) }
    var loading by remember { mutableStateOf(false) }
    val state = rememberLazyListState()
    val pager = pagers[tab]; val l = lists[tab]
    LaunchedEffect(tab, l.size) {
        if (loading || pager.exhausted) return@LaunchedEffect
        val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (l.isEmpty() || last >= l.size - 6) { loading = true; try { l.addAll(pager.more()) } finally { loading = false } }
    }
    LaunchedEffect(tab) { snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
        if (!loading && !pager.exhausted && l.isNotEmpty() && last >= l.size - 6) { loading = true; try { l.addAll(pager.more()) } finally { loading = false } } } }
    val name = if (tab == 0) "Most viewed in $title" else "Latest $title hits"
    Box(Modifier.fillMaxSize().background(W.Bg)) {
        Column(Modifier.fillMaxSize()) {
            BackBar(title, onBack)
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Most viewed", "Latest hits").forEachIndexed { i, s ->
                    Text(s, Modifier.clip(RoundedCornerShape(50)).background(if (tab == i) W.Pink else Color(0x22FFFFFF)).clickable { tab = i }.padding(horizontal = 18.dp, vertical = 9.dp), style = ts(14, FontWeight.Medium))
                }
            }
            if (l.isEmpty()) Text(if (pager.exhausted) "Nothing here yet." else "Loading...", Modifier.padding(24.dp), style = ts(15, color = W.Dim))
            else LazyColumn(state = state, contentPadding = PaddingValues(bottom = 210.dp)) {
                col { Row(Modifier.padding(20.dp, 8.dp, 20.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), style = ts(14, color = W.Dim))
                    Text("Play all", Modifier.clip(RoundedCornerShape(50)).background(W.Pink).clickable { playTracks(pc, l.toList(), 0, name) }.padding(20.dp, 9.dp), style = ts(14, FontWeight.SemiBold)) } }
                l.forEachIndexed { i, t -> col { TrackRow(t, pc) { playTracks(pc, l.toList(), i, name) } } }
                if (loading) col { Text("Loading more...", Modifier.padding(20.dp), style = ts(13, color = W.Dim)) }
            }
        }
    }
}
