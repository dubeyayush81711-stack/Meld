package com.metrolist.music.wavy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Set by the app shell. Any screen can open an artist page with the YouTube Music artist id (or just a name). */
object ArtistNav { var open: ((String?, String) -> Unit)? = null }

private val artistCache = HashMap<String, ArtistPage>()

private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** Drops repeated songs (same id, or same title and artist) while keeping order. */
fun dedupeItems(l: List<YTItem>): List<YTItem> {
    val seen = HashSet<String>()
    return l.filter { x ->
        val t = x.asTrack()
        if (t == null) true else { val a = seen.add("i:" + t.id); val b = seen.add("n:" + norm(t.title) + "|" + norm(t.artist)); a && b }
    }
}

private suspend fun albumTracks(a: AlbumItem): List<Track> = withContext(Dispatchers.IO) {
    YouTube.albumSongs(a.playlistId, a).getOrNull()?.map { it.toTrack() } ?: emptyList()
}

@Composable
fun ArtistScreen(artistId: String?, name: String, pc: PlayerConnection?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var page by remember(artistId, name) { mutableStateOf<ArtistPage?>(artistId?.let { artistCache[it] }) }
    var failed by remember(artistId, name) { mutableStateOf(false) }
    var fullSongs by remember(artistId, name) { mutableStateOf<List<Track>?>(null) }
    var loadingAll by remember(artistId, name) { mutableStateOf(false) }
    var bioOpen by remember(artistId, name) { mutableStateOf(false) }
    LaunchedEffect(artistId, name) {
        try {
            val rid = artistId ?: withContext(Dispatchers.IO) { meldTyped(name, "Artists").items.filterIsInstance<com.metrolist.innertube.models.ArtistItem>().firstOrNull()?.id }
            if (rid == null) { if (page == null) failed = true; return@LaunchedEffect }
            val p = withContext(Dispatchers.IO) { YouTube.artist(rid).getOrNull() }
            if (p != null) { artistCache[rid] = p; page = p } else if (page == null) failed = true
        } catch (e: Exception) { if (page == null) failed = true }
    }
    Backdrop(streaks = false) {
        val pg = page
        if (pg == null) {
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                BackDot(onBack)
                Text(if (failed) "Could not load this artist. Check your connection and try again." else "Loading...", Modifier.align(Alignment.Center).padding(32.dp), style = ts(15, color = W.Dim))
            }
            return@Backdrop
        }
        val sections = pg.sections
        val songSecs = sections.filter { s -> s.items.any { it is SongItem } && s.items.none { it is AlbumItem } }
        val videoSec = songSecs.firstOrNull { it.title.contains("video", true) }
        val popularSec = songSecs.firstOrNull { it !== videoSec }
        val popular = popularSec?.items?.filterIsInstance<SongItem>()?.map { it.toTrack() } ?: emptyList()
        val shown = fullSongs ?: popular.take(5)
        val albumSecs = sections.filter { s -> s.items.any { it is AlbumItem } }
        val latest = albumSecs.flatMap { it.items }.filterIsInstance<AlbumItem>().maxByOrNull { it.year ?: 0 }
        val aName = pg.artist.title
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 210.dp)) {
            col {
                Box(Modifier.fillMaxWidth().height(380.dp)) {
                    AsyncImage(pg.artist.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x66000000), 0.35f to Color.Transparent, 1f to W.Bg)))
                    Box(Modifier.statusBarsPadding()) { BackDot(onBack) }
                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp)) {
                        Text(aName, style = ts(40, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        (pg.monthlyListenerCount ?: pg.subscriberCountText)?.let { Text(it, Modifier.padding(top = 4.dp), style = ts(14, color = Color(0xFFD6D0DE))) }
                    }
                }
                Row(Modifier.padding(20.dp, 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FollowButton(pg.artist.id, aName, big = true)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(48.dp).glass(CircleShape).clickable { if (popular.isNotEmpty()) playTracks(pc, popular.shuffled(), 0, aName) }, contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.shuffle), null, Modifier.size(24.dp), tint = Color.White) }
                    Box(Modifier.size(56.dp).clip(CircleShape).background(W.Pink).clickable { if (popular.isNotEmpty()) playTracks(pc, popular, 0, aName) }, contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.play), null, Modifier.size(30.dp), tint = Color.White) }
                }
            }
            if (popular.isNotEmpty()) {
                col {
                    SectionHeader("Popular")
                }
                itemsIndexed(shown) { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", Modifier.width(34.dp).padding(start = 16.dp), style = ts(15, FontWeight.Medium, W.Dim))
                        Box(Modifier.weight(1f)) { TrackRow(t, pc) { playTracks(pc, shown, i, aName) } }
                    }
                }
                col {
                    val more = popularSec?.moreEndpoint
                    if (fullSongs == null && (more != null || popular.size > 5)) Text(if (loadingAll) "Loading..." else "See more", Modifier.padding(20.dp, 10.dp).clip(RoundedCornerShape(50)).glass(RoundedCornerShape(50)).clickable(enabled = !loadingAll) {
                        loadingAll = true
                        scope.launch {
                            val out = ArrayList<Track>(popular)
                            try {
                                if (more != null) withContext(Dispatchers.IO) {
                                    val first = YouTube.artistItems(more).getOrNull()
                                    first?.items?.filterIsInstance<SongItem>()?.forEach { out.add(it.toTrack()) }
                                    var tok = first?.continuation; var n = 0
                                    while (tok != null && n < 4) { val c = YouTube.artistItemsContinuation(tok).getOrNull() ?: break
                                        c.items.filterIsInstance<SongItem>().forEach { out.add(it.toTrack()) }; tok = c.continuation; n++ }
                                }
                            } catch (e: Exception) { }
                            fullSongs = out.distinctBy { it.id }.distinctBy { norm(it.title) + "|" + norm(it.artist) }
                            loadingAll = false
                        }
                    }.padding(horizontal = 22.dp, vertical = 10.dp), style = ts(14, FontWeight.Medium))
                    Spacer(Modifier.height(18.dp))
                }
            }
            if (latest != null) col {
                SectionHeader("Latest release")
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable { scope.launch { val l = albumTracks(latest); if (l.isNotEmpty()) playTracks(pc, l, 0, latest.title) } }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(latest.thumbnail, 84.dp, 14)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(latest.title, style = ts(17, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(latest.year?.toString(), "Release").joinToString(" - "), style = ts(13, color = W.Dim))
                    }
                    Box(Modifier.size(44.dp).glass(CircleShape, 2.2f), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.play), null, Modifier.size(24.dp), tint = Color.White) }
                }
                Spacer(Modifier.height(26.dp))
            }
            // Radio
            pg.artist.radioEndpoint?.let { rep -> col {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable {
                    scope.launch { val l = withContext(Dispatchers.IO) { try { YouTube.next(rep).getOrNull()?.items?.map { it.toTrack() } ?: emptyList() } catch (e: Exception) { emptyList() } }
                        if (l.isNotEmpty()) playTracks(pc, l, 0, "$aName radio") }
                }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.radio), null, Modifier.size(26.dp), tint = Color.White)
                    Column(Modifier.padding(start = 14.dp)) { Text("$aName radio", style = ts(16, FontWeight.SemiBold)); Text("Endless songs like this artist", style = ts(13, color = W.Dim)) }
                }
                Spacer(Modifier.height(26.dp))
            } }
            for (s in sections) {
                val albums = s.items.filterIsInstance<AlbumItem>()
                val artists = s.items.filterIsInstance<com.metrolist.innertube.models.ArtistItem>()
                val lists = s.items.filterIsInstance<PlaylistItem>()
                when {
                    albums.isNotEmpty() -> col {
                        SectionHeader(s.title.ifBlank { "Releases" })
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(albums) { _, a -> MiniCard(a.thumbnail, a.title, listOfNotNull(a.year?.toString(), a.artists?.firstOrNull()?.name).joinToString(" - ")) {
                                scope.launch { val l = albumTracks(a); if (l.isNotEmpty()) playTracks(pc, l, 0, a.title) } } }
                        }
                        Spacer(Modifier.height(26.dp))
                    }
                    artists.isNotEmpty() -> col {
                        SectionHeader(s.title.ifBlank { "Fans also like" })
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            itemsIndexed(artists) { _, a ->
                                Column(Modifier.width(130.dp).clickable { ArtistNav.open?.invoke(a.id, a.title) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Cover(a.thumbnail, 130.dp, 65)
                                    Text(a.title, Modifier.padding(top = 8.dp), style = ts(14, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Spacer(Modifier.height(26.dp))
                    }
                    lists.isNotEmpty() -> col {
                        SectionHeader(s.title.ifBlank { "Playlists" })
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(lists) { _, p -> MiniCard(p.thumbnail, p.title, p.author?.name ?: "Playlist") {
                                scope.launch { val l = withContext(Dispatchers.IO) { try { YouTube.playlist(p.id.removePrefix("VL")).getOrNull()?.songs?.map { it.toTrack() } ?: emptyList() } catch (e: Exception) { emptyList() } }
                                    if (l.isNotEmpty()) playTracks(pc, l, 0, p.title) } } }
                        }
                        Spacer(Modifier.height(26.dp))
                    }
                    s === videoSec -> col {
                        val vids = s.items.filterIsInstance<SongItem>().map { it.toTrack() }
                        SectionHeader("Clips")
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(vids) { i, t ->
                                Column(Modifier.width(240.dp).clickable { playTracks(pc, vids, i, "$aName clips") }) {
                                    AsyncImage(t.cover, null, Modifier.width(240.dp).height(135.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1A1620)), contentScale = ContentScale.Crop)
                                    Text(t.title, Modifier.padding(top = 8.dp), style = ts(14, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Spacer(Modifier.height(26.dp))
                    }
                }
            }
            val bio = stripContacts(pg.description)
            if (bio != null) col {
                SectionHeader("About")
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable { bioOpen = !bioOpen }.padding(18.dp)) {
                    Text(aName, style = ts(20, FontWeight.Bold))
                    (pg.monthlyListenerCount ?: pg.subscriberCountText)?.let { Text(it, Modifier.padding(top = 2.dp), style = ts(13, color = W.Dim)) }
                    Text(bio, Modifier.padding(top = 10.dp), style = ts(14, color = Color(0xFFD6D0DE)), maxLines = if (bioOpen) 60 else 5, overflow = TextOverflow.Ellipsis)
                    Text(if (bioOpen) "Show less" else "Show more", Modifier.padding(top = 8.dp), style = ts(13, FontWeight.SemiBold, W.Pink))
                }
            }
        }
    }
}

@Composable
private fun BackDot(onBack: () -> Unit) {
    Box(Modifier.padding(14.dp).size(42.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.arrow_back), null, Modifier.size(24.dp), tint = Color.White)
    }
}
