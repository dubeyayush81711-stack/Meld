package com.metrolist.music.wavy

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

suspend fun likeTrack(id: String): Boolean = try {
    val pls = (Api.rpc("profile_stats") as? JSONObject)?.optJSONArray("playlists")
    val fav = (0 until (pls?.length() ?: 0)).map { pls!!.getJSONObject(it) }.firstOrNull { it.optBoolean("is_favourite") }?.optLong("id")
    if (fav == null) false else {
        Api.post("playlist_tracks", JSONObject().put("playlist_id", fav).put("track_id", id).put("position", (System.currentTimeMillis() / 1000).toInt()).put("added_by", Api.uid)); true
    }
} catch (e: Exception) { false }

@Composable
fun MusicFeed(pc: PlayerConnection?) {
    val ctx = LocalContext.current
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    val seed = remember { System.currentTimeMillis().toString() }
    LaunchedEffect(Unit) {
        try { tracks = (Api.rpc("player_feed", JSONObject().put("p_limit", 40).put("p_offset", 0).put("p_region", "IN").put("p_seed", seed)) as? JSONArray).tracks() }
        catch (e: ApiError) { err = e.message }
    }
    if (tracks.isEmpty()) {
        Backdrop { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(err ?: "Loading...", style = ts(15, color = W.Dim)) } }
        return
    }
    val pager = rememberPagerState { tracks.size }
    val meta by (pc?.mediaMetadata ?: remember { MutableStateFlow(null) }).collectAsState()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(pager.settledPage, pc != null) {
        if (pc == null) return@LaunchedEffect
        val cur = tracks[pager.settledPage]
        if (!started && meta != null && meta?.id != cur.id) { started = true }
        playTracks(pc, tracks, pager.settledPage, "Music"); started = true
    }
    VerticalPager(pager, Modifier.fillMaxSize()) { page ->
        val t = tracks[page]
        var tint by remember(t.id) { mutableStateOf(Color(0xFF7A1B3A)) }
        var liked by remember(t.id) { mutableStateOf(false) }
        LaunchedEffect(t.cover) {
            val u = t.cover ?: return@LaunchedEffect
            try {
                val bmp = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(u).allowHardware(false).build()).image?.toBitmap() ?: return@LaunchedEffect
                val p = Palette.from(bmp).generate(); tint = Color(p.getDarkVibrantColor(p.getDarkMutedColor(0xFF7A1B3A.toInt())))
            } catch (e: Exception) { }
        }
        val bg by animateColorAsState(tint, tween(500), label = "fb")
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, Color(0xFF14080F), Color(0xFF0B0B0D))))) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 170.dp)) {
                Text(t.title, Modifier.padding(start = 24.dp, end = 24.dp, top = 40.dp), style = ts(30, FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(t.artist, Modifier.padding(horizontal = 24.dp, vertical = 4.dp), style = ts(22, color = Color(0xCCFFFFFF)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                AsyncImage(t.cover, null, Modifier.fillMaxWidth().aspectRatio(1f).background(W.Card), contentScale = ContentScale.Crop)
                Spacer(Modifier.weight(0.6f))
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(t.title, style = ts(24, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, style = ts(18, color = Color(0xDDFFFFFF)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    var pos by remember { mutableLongStateOf(0L) }
                    LaunchedEffect(page, meta?.id) { while (true) { pos = if (meta?.id == t.id) (pc?.player?.currentPosition ?: 0L) else 0L; delay(500) } }
                    val dur = (t.dur * 1000L).coerceAtLeast(1)
                    Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x55FFFFFF))) { Box(Modifier.fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f)).fillMaxHeight().background(Color.White)) }
                }
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 18.dp, top = 120.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                Icon(painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border), null, Modifier.size(34.dp).clickable { liked = true; kotlinx.coroutines.MainScope().launch2 { likeTrack(t.id) } }, tint = if (liked) W.Pink else Color.White)
                Icon(painterResource(R.drawable.share), null, Modifier.size(32.dp).clickable {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${t.title} - ${t.artist}\nhttps://music.youtube.com/watch?v=${t.id}"), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, tint = Color.White)
            }
        }
    }
}

fun kotlinx.coroutines.CoroutineScope.launch2(b: suspend () -> Unit) { launch { b() } }
