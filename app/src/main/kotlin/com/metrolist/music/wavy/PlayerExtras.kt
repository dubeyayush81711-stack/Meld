package com.metrolist.music.wavy

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Card colours for the player detail sections. Restyle here.
object PX {
    val card = Color(0xFF2A2A2D)
    val lyricsCard = Color(0xFF7A1B3A)
    val chip = Color(0xFF3A3A3E)
}

data class LyricLine(val t: Long, val text: String)

fun parseLyrics(raw: String): List<LyricLine> {
    val lead = Regex("^((?:\\[\\d+:\\d+(?:[.:]\\d+)?\\])+)(.*)$")
    val one = Regex("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]")
    val word = Regex("<\\d+:\\d+(?:[.:]\\d+)?>")
    var offset = 0L
    Regex("\\[offset:\\s*([+-]?\\d+)\\s*]", RegexOption.IGNORE_CASE).find(raw)?.let { offset = it.groupValues[1].toLongOrNull() ?: 0L }
    val out = ArrayList<LyricLine>()
    var synced = false
    for (l in raw.lines()) {
        val m = lead.find(l.trim())
        if (m != null) {
            val tx = m.groupValues[2].replace(word, "").replace(Regex("\\s+"), " ").trim()
            if (tx.isEmpty()) continue
            // a chorus line can carry several timestamps: one entry for each
            for (t in one.findAll(m.groupValues[1])) {
                val ms = (t.groupValues[1].toLong() * 60 + t.groupValues[2].toLong()) * 1000 + (t.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0)
                out.add(LyricLine((ms - offset).coerceAtLeast(0), tx)); synced = true
            }
        } else if (l.isNotBlank() && !l.trim().startsWith("[")) out.add(LyricLine(-1, l.trim()))
    }
    return if (synced) out.filter { it.t >= 0 }.sortedBy { it.t } + out.filter { it.t < 0 } else out
}

fun SongItem.toTrack() = Track(id, title, artists.joinToString { it.name }, thumbnail, duration ?: 0, artists.firstOrNull()?.id, album?.name)

fun stripContacts(s: String?): String? = s?.replace(Regex("[\\w.+-]+@[\\w-]+\\.[\\w.-]+"), "")?.replace(Regex("(?i)contact\\s*:?"), "")?.trim()?.takeIf { it.isNotBlank() }

@Composable
fun FollowButton(artistId: String?, name: String, big: Boolean = false) {
    var on by remember(artistId) { mutableStateOf(artistId != null && S.b("follow_$artistId")) }
    val scope = rememberCoroutineScope()
    Text(if (on) "Following" else "Follow", Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color(0xFF9A9A9A), RoundedCornerShape(50)).clickable(enabled = artistId != null) {
        on = !on; S.put("follow_$artistId", on)
        scope.launch { try { if (on) Api.post("user_artists", org.json.JSONObject().put("artist_id", artistId), true) else Api.delete("user_artists?artist_id=eq.$artistId") } catch (e: Exception) { } }
    }.padding(horizontal = if (big) 22.dp else 16.dp, vertical = if (big) 12.dp else 8.dp), style = ts(if (big) 16 else 14, FontWeight.SemiBold))
}

@Composable
fun PlayerDetails(pc: PlayerConnection, id: String, title: String, artistNames: String, artists: List<Pair<String, String?>>, cover: String?, album: String?, durSec: Int, onFullLyrics: (List<LyricLine>) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lyrics by remember(id) { mutableStateOf<List<LyricLine>>(emptyList()) }
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(id) {
        lyrics = fetchLyrics(ctx, id, title, artistNames, durSec, album)
    }
    LaunchedEffect(id) { while (true) { pos = pc.player.currentPosition; delay(500) } }
    val mainId = artists.firstOrNull()?.second
    var page by remember(id) { mutableStateOf<com.metrolist.innertube.pages.ArtistPage?>(null) }
    LaunchedEffect(mainId) { if (mainId != null) page = withContext(Dispatchers.IO) { YouTube.artist(mainId).getOrNull() } }
    Column(Modifier.fillMaxWidth().padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (lyrics.isNotEmpty()) {
            val synced = lyrics.any { it.t >= 0 }
            val cur = if (synced) lyrics.indexOfLast { it.t in 0..pos }.coerceAtLeast(0) else 0
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PX.lyricsCard).padding(20.dp)) {
                Text("Lyrics preview", style = ts(18, FontWeight.Bold))
                Spacer(Modifier.height(12.dp))
                lyrics.drop((cur - 1).coerceAtLeast(0)).take(5).forEachIndexed { i, l ->
                    val isCur = (i + (cur - 1).coerceAtLeast(0)) == cur
                    Text(l.text, Modifier.padding(vertical = 4.dp), style = ts(if (isCur) 22 else 19, FontWeight.Bold, if (isCur) Color.White else Color(0xFFF5A3BD)))
                }
                Spacer(Modifier.height(12.dp))
                Text("Show lyrics", Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { onFullLyrics(lyrics) }.padding(horizontal = 22.dp, vertical = 12.dp), style = ts(16, FontWeight.Bold, Color.Black))
            }
        }
        val pg = page
        if (pg != null) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PX.card)) {
                Box {
                    AsyncImage(pg.artist.thumbnail, null, Modifier.fillMaxWidth().height(240.dp), contentScale = ContentScale.Crop)
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color(0x66000000), 0.5f to Color.Transparent)))
                    Text("About the artist", Modifier.padding(18.dp), style = ts(20, FontWeight.Bold))
                }
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pg.artist.title, style = ts(26, FontWeight.Bold))
                            (pg.monthlyListenerCount ?: pg.subscriberCountText)?.let { Text(it, style = ts(15, color = Color(0xFFB8B8B8))) }
                        }
                        FollowButton(mainId, pg.artist.title, big = true)
                    }
                    stripContacts(pg.description)?.let { Spacer(Modifier.height(12.dp)); Text(it, style = ts(15, color = Color(0xFFC8C8C8)), maxLines = 6, overflow = TextOverflow.Ellipsis) }
                }
            }
            // Explore
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PX.card).padding(16.dp)) {
                Text("Explore ${pg.artist.title}", style = ts(20, FontWeight.Bold))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    @Composable fun Card(label: String, img: String?, load: suspend () -> List<Track>) {
                        Box(Modifier.weight(1f).aspectRatio(0.62f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111)).clickable {
                            scope.launch { val l = try { load() } catch (e: Exception) { emptyList() }; if (l.isNotEmpty()) playTracks(pc, l, 0, label) }
                        }) {
                            AsyncImage(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0xCC000000))))
                            Text(label, Modifier.align(Alignment.BottomStart).padding(10.dp), style = ts(16, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Card("Songs by ${pg.artist.title}", pg.artist.thumbnail) { withContext(Dispatchers.IO) { pg.sections.flatMap { it.items }.filterIsInstance<SongItem>().map { it.toTrack() } } }
                    Card("Similar to ${pg.artist.title}", cover) { withContext(Dispatchers.IO) { pg.artist.radioEndpoint?.let { YouTube.next(it).getOrNull()?.items?.map { s -> s.toTrack() } } ?: emptyList() } }
                    Card("Similar to $title", cover) { withContext(Dispatchers.IO) { YouTube.next(WatchEndpoint(videoId = id, playlistId = "RDAMVM$id")).getOrNull()?.items?.map { s -> s.toTrack() } ?: emptyList() } }
                }
            }
        }
        // Credits
        var all by remember(id) { mutableStateOf(false) }
        if (artists.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PX.card).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Credits", Modifier.weight(1f), style = ts(20, FontWeight.Bold))
                if (artists.size > 3) Text(if (all) "Show less" else "Show all", Modifier.clickable { all = !all }, style = ts(15, FontWeight.SemiBold, W.Pink))
            }
            artists.take(if (all) artists.size else 3).forEachIndexed { i, a ->
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.first, style = ts(18, FontWeight.SemiBold))
                        Text("Main Artist", style = ts(14, color = Color(0xFFB8B8B8)))
                    }
                    FollowButton(a.second, a.first)
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun FullLyrics(pc: PlayerConnection, title: String, artist: String, lines: List<LyricLine>, onClose: () -> Unit) {
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { pos = pc.player.currentPosition; delay(400) } }
    val synced = lines.any { it.t >= 0 }
    val cur = if (synced) lines.indexOfLast { it.t in 0..pos }.coerceAtLeast(0) else -1
    val st = rememberLazyListState()
    LaunchedEffect(cur) { if (cur >= 0) st.animateScrollToItem((cur - 2).coerceAtLeast(0)) }
    Column(Modifier.fillMaxSize().background(PX.lyricsCard).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.expand_more), null, Modifier.size(32.dp).clickable(onClick = onClose), tint = Color.White)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = ts(17, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, style = ts(14, color = Color(0xFFF5C8D6)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 22.dp), state = st) {
            itemsIndexed(lines) { i, l ->
                Text(l.text, Modifier.padding(vertical = 10.dp).clickable(enabled = l.t >= 0) { pc.seekTo(l.t) }, style = ts(28, FontWeight.Bold, if (cur < 0 || i == cur) Color.White else Color(0x99FFFFFF)))
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
