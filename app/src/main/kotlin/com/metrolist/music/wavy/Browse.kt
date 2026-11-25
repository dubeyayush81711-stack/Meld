package com.metrolist.music.wavy

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
    LaunchedEffect(q) {
        if (q.trim().length < 2) { res = null; return@LaunchedEffect }
        delay(350); err = null
        try { res = Api.rpc("search_all", JSONObject().put("p_q", q.trim()).put("p_limit", 30)) as? JSONObject } catch (e: ApiError) { err = humanErr(e) }
    }
    val meta by (pc?.mediaMetadata ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val playing by (pc?.isPlaying ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    Backdrop(tint = Color(0xFF2B0B3A)) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 190.dp)) {
            col {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(50)).background(Color(0x55FFFFFF)).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.search), null, Modifier.size(24.dp), tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            if (q.isEmpty()) Text("Search", style = ts(18, color = Color(0xCCFFFFFF)))
                            BasicTextField(q, { q = it }, singleLine = true, textStyle = ts(18), cursorBrush = SolidColor(W.Pink), modifier = Modifier.fillMaxWidth())
                        }
                        if (q.isNotEmpty()) Icon(painterResource(R.drawable.close), null, Modifier.size(22.dp).clickable { q = "" }, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Avatar(40, onProfile)
                }
                Spacer(Modifier.height(16.dp))
            }
            val r = res
            if (q.trim().length < 2 || r == null) {
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
                                Column(Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(c)).clickable { onLang(t) }.padding(16.dp)) {
                                    Text(t.title, style = ts(24, FontWeight.SemiBold))
                                    Spacer(Modifier.weight(1f))
                                    if (t.top.isNotEmpty()) Text("Top: " + t.top, style = ts(13, FontWeight.Medium, Color(0xEEFFFFFF)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                col {
                    Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("All", "Songs", "Artists", "Albums").forEach { f ->
                            Text(f, Modifier.clip(RoundedCornerShape(50)).background(if (f == filter) W.Pink else Color(0x55FFFFFF)).clickable { filter = f }.padding(horizontal = 18.dp, vertical = 9.dp), style = ts(15, FontWeight.Medium))
                        }
                    }
                }
                val songs = r.optJSONArray("songs").tracks()
                val artists = r.optJSONArray("artists").artistItems()
                val albums = r.optJSONArray("albums")
                if (filter == "All" || filter == "Songs") {
                    if (songs.isNotEmpty()) col { Text("Songs", Modifier.padding(bottom = 6.dp), style = ts(20, FontWeight.SemiBold)) }
                    songs.forEachIndexed { i, t -> col { TrackRow(t, pc) { playTracks(pc, songs, i, "Search") } } }
                }
                if ((filter == "All" || filter == "Artists") && artists.isNotEmpty()) col {
                    Text("Artists", Modifier.padding(top = 16.dp, bottom = 10.dp), style = ts(20, FontWeight.SemiBold))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) { itemsIndexed(artists) { _, a ->
                        Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) { Cover(a.thumb, 100.dp, 50)
                            Text(a.name, Modifier.padding(top = 6.dp), style = ts(13, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis) } } }
                }
                if ((filter == "All" || filter == "Albums") && albums != null && albums.length() > 0) col {
                    Text("Albums", Modifier.padding(top = 16.dp, bottom = 10.dp), style = ts(20, FontWeight.SemiBold))
                    val list = (0 until albums.length()).mapNotNull { albums.optJSONObject(it) }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) { itemsIndexed(list) { _, a ->
                        val tr = a.optJSONArray("tracks").tracks()
                        MiniCard(a.optString("cover_url").ifBlank { tr.firstOrNull()?.cover }, a.optString("album", a.optString("title")), a.optString("artist")) { playTracks(pc, tr, 0, a.optString("album")) } } }
                }
                if (songs.isEmpty() && artists.isEmpty()) col { Text(err ?: "No results.", style = ts(15, color = W.Dim)) }
            }
        }
    }
}

@Composable
fun Avatar(size: Int, onClick: () -> Unit) {
    var name by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { try { name = ((Api.get("profiles?id=eq.${Api.uid}&select=name") as? JSONArray)?.optJSONObject(0)?.optString("name")).orEmpty().ifBlank { Api.email } } catch (e: Exception) { } }
    Box(Modifier.size(size.dp).clip(CircleShape).background(W.Grad).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase().ifEmpty { "W" }, style = ts(size / 2, FontWeight.Bold)) }
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
            else LazyColumn(contentPadding = PaddingValues(bottom = 190.dp)) {
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
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 190.dp)) {
            col { Row(Modifier.padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(44, onProfile)
                Text("Your Library", Modifier.padding(start = 14.dp).weight(1f), style = ts(28, FontWeight.SemiBold))
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Playlists", "Artists").forEach { c -> Text(c, Modifier.clip(RoundedCornerShape(50)).background(if (c == chip) W.Pink else Color(0xFF2A2A2E)).clickable { chip = c }.padding(horizontal = 20.dp, vertical = 10.dp), style = ts(15, FontWeight.Medium)) }
            }
            Spacer(Modifier.height(14.dp)) }
            if (chip == "Playlists") {
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
                    Box(Modifier.size(110.dp).clip(CircleShape).background(Color(0xFF2A2A2E)), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.add), null, Modifier.size(36.dp), tint = Color(0xFFBDB8C4)) }
                    Text("Add artists", Modifier.padding(top = 8.dp), style = ts(15, FontWeight.SemiBold))
                }
            }
        }
    }
}
