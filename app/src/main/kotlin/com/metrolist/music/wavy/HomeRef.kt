package com.metrolist.music.wavy

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How many times each song was opened on this phone (real counts, kept per account). Feeds "Quick access". */
object PlayCount {
    private var mem: HashMap<String, Int>? = null
    private fun key() = "recent_tracks_pc_" + (Api.uid ?: "")
    @Synchronized private fun load(): HashMap<String, Int> {
        mem?.let { return it }
        val m = HashMap<String, Int>()
        S.s(key()).split("\n").forEach { l -> val i = l.lastIndexOf('|'); if (i > 0) l.substring(i + 1).toIntOrNull()?.let { m[l.substring(0, i)] = it } }
        mem = m; return m
    }
    @Synchronized fun reset() { mem = null }
    @Synchronized fun bump(id: String) {
        val m = load(); m[id] = (m[id] ?: 0) + 1
        val snap = m.entries.sortedByDescending { it.value }.take(300).joinToString("\n") { it.key + "|" + it.value }
        Thread { S.put(key(), snap) }.start()
    }
    @Synchronized fun of(id: String): Int = load()[id] ?: 0
}

@Composable
fun HomeHead(title: String, sub: String? = null, onAll: (() -> Unit)? = null, arrow: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (sub != null) Text(sub, style = ts(12, color = Color(0xB3FFFFFF)))
            Text(title, style = ts(24, FontWeight.SemiBold, W.Pink), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onAll != null) Text("Play all", Modifier.clip(RoundedCornerShape(50)).border(1.dp, W.Pink, RoundedCornerShape(50)).clickable(onClick = onAll).padding(horizontal = 16.dp, vertical = 7.dp), style = ts(14, FontWeight.Medium, W.Pink))
        if (arrow != null) Icon(painterResource(R.drawable.arrow_forward), null, Modifier.clip(CircleShape).clickable(onClick = arrow).padding(6.dp).size(24.dp), tint = W.Pink)
    }
}

/** Recently played: last songs opened, as list rows. */
@Composable
fun RecentList(pc: PlayerConnection?) {
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Recent.load() } }
    val l = Recent.list
    if (l.isEmpty()) return
    Column {
        HomeHead("Heard lately", onAll = { playTracks(pc, l, 0, "Heard lately") })
        l.take(4).forEachIndexed { i, t -> TrackRow(t, pc) { playTracks(pc, l, i, "Heard lately") } }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DiceTile(modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(W.Pink.copy(alpha = 0.28f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(64.dp)) {
            val r = size.minDimension * 0.09f
            listOf(Offset(0.22f, 0.22f), Offset(0.78f, 0.22f), Offset(0.5f, 0.5f), Offset(0.22f, 0.78f), Offset(0.78f, 0.78f)).forEach { drawCircle(W.Pink.copy(alpha = 0.85f), r * 1.6f, Offset(it.x * size.width, it.y * size.height)) }
        }
    }
}

/** Quick access: 3 x 3 grid of the songs you open most. Last tile plays a random one of them. Pages of 9. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedDial(pc: PlayerConnection?) {
    val rec = Recent.list
    if (rec.size < 3) return
    val songs = remember(rec) { rec.sortedByDescending { PlayCount.of(it.id) }.take(17) }
    val pages = remember(songs) { (songs.map<Track, Track?> { it } + listOf<Track?>(null)).chunked(9) }
    val ps = rememberPagerState { pages.size }
    Column {
        HomeHead("Quick access")
        HorizontalPager(ps, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 8.dp) { p ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pages[p].chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { t ->
                            if (t == null) DiceTile(Modifier.weight(1f).aspectRatio(1f)) { playTracks(pc, songs.shuffled(), 0, "Quick access") }
                            else Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { playTracks(pc, songs, songs.indexOf(t), "Quick access") }) {
                                Cover(t.cover, 140.dp, 0, Modifier.fillMaxWidth().aspectRatio(1f))
                                Box(Modifier.fillMaxWidth().align(Alignment.BottomStart).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 5.dp)) {
                                    Text(t.title, style = ts(13, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (pages.size > 1) Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i -> Box(Modifier.padding(horizontal = 3.dp).size(7.dp).clip(CircleShape).background(if (i == ps.currentPage) W.Pink else Color(0x44FFFFFF))) }
        }
        Spacer(Modifier.height(22.dp))
    }
}

/** Pick up again: older songs from your history in a two-row card grid. */
@Composable
fun KeepListening(pc: PlayerConnection?) {
    val rec = Recent.list
    val songs = remember(rec) { val top = rec.sortedByDescending { PlayCount.of(it.id) }.take(17).map { it.id }.toSet(); rec.filter { it.id !in top }.take(12) }
    if (songs.size < 2) return
    Column {
        HomeHead("Pick up again")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(songs.chunked(2)) { ci, pair ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEachIndexed { j, t ->
                        Column(Modifier.width(150.dp).pressScale().clickable { playTracks(pc, songs, ci * 2 + j, "Pick up again") }) {
                            Box {
                                Cover(t.cover, 150.dp, 8, Modifier.fillMaxWidth().height(110.dp))
                                Box(Modifier.align(Alignment.Center).size(34.dp).clip(CircleShape).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                                    Icon(painterResource(R.drawable.play), null, Modifier.size(20.dp), tint = Color.White)
                                }
                            }
                            Text(t.title, Modifier.padding(top = 6.dp), style = ts(15, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(t.artist, style = ts(12, color = Color(0xB3FFFFFF)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(26.dp))
    }
}

/** A list in pages of 4 rows (next page peeks in from the right), with a "Play all" pill. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagedTracks(title: String, tracks: List<Track>, pc: PlayerConnection?, sub: String?) {
    if (tracks.size < 3) return
    val list = tracks.take(20)
    val pages = list.chunked(4)
    Column {
        HomeHead(title, sub, onAll = { playTracks(pc, list, 0, title) })
        HorizontalPager(rememberPagerState { pages.size }, contentPadding = PaddingValues(end = 28.dp)) { p ->
            Column { pages[p].forEachIndexed { i, t -> TrackRow(t, pc) { playTracks(pc, list, p * 4 + i, title) } } }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private val MOODS = listOf("Chill", "Focus", "Commute", "Gaming", "Energize", "Feel good", "Party", "Romance", "Workout", "Sleep")

/** Moods and styles: tap one, Wavy searches live songs for it in your languages and plays them. */
@Composable
fun MoodsGrid(pc: PlayerConnection?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Column {
        HomeHead("Moods and styles")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MOODS.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { m ->
                        Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF)).clickable {
                            if (busy) return@clickable
                            busy = true; Toast.makeText(ctx, "Loading $m songs...", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                val l = try {
                                    val lg = userLanguages().firstOrNull()?.let { if (it == "south") "tamil telugu" else it } ?: "hindi"
                                    LangGuard.filter(liveSongs("$lg $m songs").tracks()).filter { !Regex("(?i)cover|karaoke|slowed|reverb|8d|sped up").containsMatchIn(it.title) }
                                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emptyList() }
                                busy = false
                                if (l.isEmpty()) Toast.makeText(ctx, "Could not load $m songs. Try again.", Toast.LENGTH_SHORT).show() else playTracks(pc, l, 0, m)
                            }
                        }.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) { Text(m, style = ts(16, FontWeight.Medium)) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(26.dp))
    }
}

private val liveCache = HashMap<String, List<Track>>()

@Composable
private fun SearchShelf(key: String, q: String, keep: Regex?, content: @Composable (List<Track>) -> Unit) {
    var l by remember { mutableStateOf(liveCache[key] ?: emptyList()) }
    LaunchedEffect(key) {
        if (l.isNotEmpty()) return@LaunchedEffect
        val lg = userLanguages().firstOrNull()?.let { if (it == "south") "tamil telugu" else it } ?: "hindi"
        val r = try { LangGuard.filter(liveSongs("$lg $q").tracks()).filter { keep == null || keep.containsMatchIn(it.title) } } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emptyList() }
        if (r.isNotEmpty()) { liveCache[key] = r; l = r }
    }
    if (l.size >= 3) content(l)
}

/** Live and unplugged: live and acoustic versions found by live search (title keyword rule, so it is approximate). */
@Composable
fun LiveRow(pc: PlayerConnection?) {
    SearchShelf("live", "live performance unplugged songs", Regex("(?i)live|unplugged|acoustic|session|mtv")) { PagedTracks("Live and unplugged", it, pc, null) }
}

/** Wedding season: songs from a live search for wedding songs in your language. */
@Composable
fun WeddingRow(pc: PlayerConnection?) {
    SearchShelf("wedding", "wedding shaadi songs", null) { l ->
        Column {
            HomeHead("Wedding season", "Shaadi playlist")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(l) { i, t -> MiniCard(t.cover, t.title, t.artist) { playTracks(pc, l, i, "Wedding season") } }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Vice theme sheets use the theme's glass colour; other themes keep their own colour. */
fun viceOr(c: Color): Color = if (Theming.cur().id == "vice") GlassSheet else c

/** Placeholder blocks that pulse while Home loads. */
@Composable
fun HomeShimmer() {
    val a by androidx.compose.animation.core.rememberInfiniteTransition(label = "sh").animateFloat(0.25f, 0.6f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "a")
    val c = Color.White.copy(alpha = a * 0.25f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(150.dp).height(24.dp).clip(RoundedCornerShape(8.dp)).background(c))
        repeat(4) { Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(c))
            Column(Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(180.dp).height(14.dp).clip(RoundedCornerShape(6.dp)).background(c)); Box(Modifier.width(110.dp).height(11.dp).clip(RoundedCornerShape(6.dp)).background(c)) } } }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { repeat(2) { Box(Modifier.size(148.dp).clip(RoundedCornerShape(18.dp)).background(c)) } }
    }
}

/** Library tiles: Liked songs, Downloaded, My top 50 (by real play counts on this phone) and History. */
@Composable
fun LibTiles(favId: Long?, onPlaylist: (Long, String) -> Unit, setChip: (String) -> Unit) {
    data class T(val n: String, val sub: String, val icon: Int, val go: () -> Unit)
    val all = buildList {
        if (favId != null) add(T("Liked songs", "Your favourites", R.drawable.favorite) { onPlaylist(favId, "Favourites") })
        add(T("Downloaded", "Plays offline", R.drawable.library_music) { setChip("Downloads") })
        add(T("My top 50", "Most played here", R.drawable.shuffle) { setChip("Top 50") })
        add(T("History", "Recently played", R.drawable.history) { setChip("History") })
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        all.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { t ->
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0x22FFFFFF)).clickable(onClick = t.go).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(W.Grad), contentAlignment = Alignment.Center) { Icon(painterResource(t.icon), null, Modifier.size(22.dp), tint = Color.White) }
                        Column(Modifier.padding(start = 10.dp)) { Text(t.n, style = ts(14, FontWeight.SemiBold), maxLines = 1); Text(t.sub, style = ts(11, color = Color(0xB3FFFFFF)), maxLines = 1) }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
