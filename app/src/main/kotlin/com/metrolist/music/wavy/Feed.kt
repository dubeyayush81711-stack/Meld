package com.metrolist.music.wavy

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.ui.viewinterop.AndroidView
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


class VideoBridge(val onTime: (Float) -> Unit, val onErr: (Int) -> Unit) {
    @JavascriptInterface fun onTime(t: Float) = onTime.invoke(t)
    @JavascriptInterface fun onErr(c: Int) = onErr.invoke(c)
    @JavascriptInterface fun onState(c: Int) {}
}

@Composable
fun StreamVideo(id: String, pc: PlayerConnection?, modifier: Modifier, onDiag: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    var cands by remember(id) { mutableStateOf<List<VCand>>(emptyList()) }
    var idx by remember(id) { mutableIntStateOf(0) }
    var ready by remember(id) { mutableStateOf(false) }
    var arOv by remember(id) { mutableFloatStateOf(0f) }
    LaunchedEffect(id) {
        onDiag("video: loading")
        val (list, why) = fetchVideoCandidates(id)
        if (list.isEmpty()) onDiag(why.ifEmpty { "no video stream" })
        cands = list
    }
    val cand = cands.getOrNull(idx) ?: return
    val ex = remember(cand.url) {
        androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build().apply {
            volume = 0f
            val http = androidx.media3.datasource.DefaultHttpDataSource.Factory().setUserAgent(cand.ua).setAllowCrossProtocolRedirects(true)
            val dsf = if (cand.hls || !cand.chunked) http else androidx.media3.datasource.DataSource.Factory { ChunkedDS(http) }
            val item = androidx.media3.common.MediaItem.fromUri(cand.url)
            if (cand.hls) setMediaSource(androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dsf).createMediaSource(item))
            else setMediaSource(androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dsf).createMediaSource(item))
            prepare()
        }
    }
    DisposableEffect(ex) {
        val l = object : androidx.media3.common.Player.Listener {
            override fun onRenderedFirstFrame() { ready = true; onDiag("playing via ${cand.client} ${if (cand.hls) "HLS" else cand.h.toString() + "p"}${if (cand.chunked) "" else " plain"} (${idx + 1}/${cands.size})") }
            override fun onVideoSizeChanged(vs: androidx.media3.common.VideoSize) { if (vs.height > 0 && vs.width > 0) arOv = vs.width * vs.pixelWidthHeightRatio / vs.height }
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) { val code = (generateSequence(e as Throwable?) { it.cause }.filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>().firstOrNull())?.responseCode
                onDiag("video err ${e.errorCodeName} ${code ?: ""} ${cand.client} ${if (cand.hls) "HLS" else cand.h.toString() + "p"}${if (cand.chunked) "" else " plain"} (${idx + 1}/${cands.size})"); if (idx < cands.size - 1) idx++ }
        }
        ex.addListener(l)
        onDispose { ex.removeListener(l); ex.release() }
    }
    LaunchedEffect(ex, pc) {
        while (true) {
            val p = pc?.player
            if (p != null) {
                val want = p.currentPosition
                ex.playWhenReady = p.isPlaying
                if (kotlin.math.abs(ex.currentPosition - want) > 700) ex.seekTo(want)
            }
            delay(500)
        }
    }
    val ar = if (arOv > 0f) arOv else cand.w.toFloat() / cand.h.coerceAtLeast(1)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.clipToBounds()) {
        val h = maxHeight
        val fw = minOf(maxWidth, h * ar); val fh = fw / ar
        AndroidView(modifier = Modifier.align(Alignment.Center).requiredSize(fw, fh), factory = { c -> android.view.TextureView(c).also { ex.setVideoTextureView(it) } })
    }
}

@Composable
fun MusicFeed(pc: PlayerConnection?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playing by (pc?.isPlaying ?: remember { MutableStateFlow(false) }).collectAsState()
    val meta by (pc?.mediaMetadata ?: remember { MutableStateFlow(null) }).collectAsState()
    var feed by remember { mutableStateOf<List<Track>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var seed by remember { mutableStateOf(System.currentTimeMillis().toString()) }
    var refreshN by remember { mutableIntStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    // the song that is playing when the tab opens stays first, so nothing restarts
    val first = remember { meta?.let { m -> Track(m.id, m.title, m.artists.joinToString { it.name }, m.thumbnailUrl, m.duration, m.artists.firstOrNull()?.id, m.album?.title) } }
    LaunchedEffect(refreshN) {
        try { feed = (Api.rpc("player_feed_ig", JSONObject().put("p_limit", 30).put("p_offset", 0).put("p_region", "IN").put("p_seed", seed)) as? JSONArray).tracks(); err = null}
        catch (e: ApiError) { err = humanErr(e) }
    }
    val tracks = remember(feed, refreshN) { (if (refreshN == 0) listOfNotNull(first) else emptyList()) + feed.filter { it.id != first?.id || refreshN > 0 } }
    if (tracks.isEmpty()) {
        Backdrop { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(err ?: "Loading...", style = ts(15, color = W.Dim)) } }
        return
    }
    var queueOwned by remember { mutableStateOf(false) }
    var lastDriven by remember { mutableIntStateOf(-1) }
    val pager = rememberPagerState { tracks.size }
    LaunchedEffect(pager.settledPage, tracks.size) {
        if (tracks.size - pager.settledPage <= 5 && !loadingMore && feed.size < 200) {
            loadingMore = true
            try { val more = (Api.rpc("player_feed_ig", JSONObject().put("p_limit", 30).put("p_offset", feed.size).put("p_region", "IN").put("p_seed", seed)) as? JSONArray).tracks(); val have = feed.map { it.id }.toSet(); feed = feed + more.filter { it.id !in have } } catch (e: Exception) { }
            loadingMore = false
        }
    }
    LaunchedEffect(refreshN) { if (refreshN > 0) { lastDriven = 0; pager.scrollToPage(0) } }
    // when the song changes by itself (auto next, notification, screen off/on), the pager follows it; it never forces a song
    LaunchedEffect(meta?.id) {
        val idx = tracks.indexOfFirst { it.id == meta?.id }
        if (idx >= 0 && idx != pager.currentPage && !pager.isScrollInProgress) { lastDriven = idx; pager.scrollToPage(idx) }
    }
    var pull by remember { mutableFloatStateOf(0f) }
    val nsc = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (pager.currentPage == 0 && available.y > 0) pull += available.y else if (available.y < 0) pull = 0f
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (pull > 160f) { seed = System.currentTimeMillis().toString(); queueOwned = false; refreshN++ }
                pull = 0f
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
    LaunchedEffect(pager.settledPage, pc != null) {
        if (pc == null) return@LaunchedEffect
        val cur = tracks[pager.settledPage]
        if (meta?.id == cur.id) { lastDriven = pager.settledPage; return@LaunchedEffect }
        if (lastDriven == pager.settledPage) return@LaunchedEffect
        lastDriven = pager.settledPage
        if (queueOwned && pager.settledPage < pc.player.mediaItemCount && pc.player.getMediaItemAt(pager.settledPage).mediaId == cur.id) pc.player.seekTo(pager.settledPage, 0L).also { pc.player.play() }
        else { playTracks(pc, tracks, pager.settledPage, "Music"); queueOwned = true }
    }
    val hooked = remember { HashSet<String>() }
    LaunchedEffect(pager.settledPage, meta?.id) {
        val cur = tracks.getOrNull(pager.settledPage) ?: return@LaunchedEffect
        val p = pc?.player ?: return@LaunchedEffect
        if (meta?.id != cur.id) return@LaunchedEffect
        var tries = 0
        while (p.duration <= 0 && tries < 20) { delay(250); tries++ }
        val dur = p.duration
        var start = 0L
        if (dur > 75_000 && hooked.add(cur.id)) { start = (dur * 0.30).toLong().coerceAtMost(dur - 40_000); p.seekTo(start) }
        else if (dur > 75_000) start = (dur * 0.30).toLong().coerceAtMost(dur - 40_000)
        if (dur <= 75_000) return@LaunchedEffect
        // play only the hook (about 25 s), then move to the next song
        while (true) {
            delay(500)
            if (meta?.id != cur.id) return@LaunchedEffect
            if (p.isPlaying && p.currentPosition >= start + 25_000 && pager.settledPage < tracks.size - 1) { pager.animateScrollToPage(pager.settledPage + 1); return@LaunchedEffect }
        }
    }
    Box(Modifier.fillMaxSize()) {
        VerticalPager(pager, Modifier.fillMaxSize().nestedScroll(nsc), userScrollEnabled = true) { page ->
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
            var diag by remember(t.id) { mutableStateOf("") }
            LaunchedEffect(diag) { if (diag.startsWith("playing via")) { delay(5000); diag = "" } }
            var burst by remember(t.id) { mutableIntStateOf(0) }
            var flash by remember(t.id) { mutableIntStateOf(0) }
            var flashN by remember(t.id) { mutableIntStateOf(0) }
            LaunchedEffect(flashN) { if (flashN > 0) { delay(700); flash = 0 } }
            val hs by animateFloatAsState(if (burst > 0) 1f else 0f, tween(350), label = "h")
            LaunchedEffect(burst) { if (burst > 0) { delay(700); burst = 0 } }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, Color(0xFF14080F), Color(0xFF0B0B0D))))
                .pointerInput(t.id) { detectTapGestures(
                    onTap = { pc?.player?.let { p -> if (p.isPlaying) p.pause() else p.play() }; flash = 1; flashN++ },
                    onDoubleTap = { o ->
                        val p = pc?.player
                        if (o.x < size.width * 0.33f) { p?.seekTo((p.currentPosition - 10000).coerceAtLeast(0)); flash = 2; flashN++ }
                        else if (o.x > size.width * 0.67f) { p?.seekTo(p.currentPosition + 10000); flash = 3; flashN++ }
                        else { if (!liked) { liked = true; scope.launch { likeTrack(t.id) } }; burst++ }
                    }) }) {
                AsyncImage(t.cover, null, Modifier.fillMaxSize().blur(28.dp), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                if (page == pager.settledPage) StreamVideo(t.id, pc, Modifier.fillMaxSize()) { diag = it }
                if (flash != 0) Text(when (flash) { 1 -> if (playing) "Playing" else "Paused"; 2 -> "-10s"; else -> "+10s" }, Modifier.align(when (flash) { 2 -> Alignment.CenterStart; 3 -> Alignment.CenterEnd; else -> Alignment.Center }).padding(horizontal = 40.dp).clip(RoundedCornerShape(50)).background(Color(0x88000000)).padding(horizontal = 18.dp, vertical = 10.dp), style = ts(18, FontWeight.SemiBold))
                if (hs > 0.01f) Icon(painterResource(R.drawable.favorite), null, Modifier.align(Alignment.Center).size((60 + 80 * hs).dp), tint = W.Pink.copy(alpha = hs))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x66000000), 0.25f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color(0xCC000000))))
                Column(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 170.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Icon(painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border), null, Modifier.size(34.dp).clickable { liked = true; scope.launch { likeTrack(t.id) } }, tint = if (liked) W.Pink else Color.White)
                    Icon(painterResource(R.drawable.share), null, Modifier.size(32.dp).clickable {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${t.title} - ${t.artist}\nhttps://music.youtube.com/watch?v=${t.id}"), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, tint = Color.White)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 80.dp, bottom = 100.dp)) {
                    Text(t.title, style = ts(24, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, style = ts(18, color = Color(0xDDFFFFFF)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    var pos by remember { mutableLongStateOf(0L) }
                    var dragF by remember { mutableStateOf<Float?>(null) }
                    var wpx by remember { mutableIntStateOf(1) }
                    LaunchedEffect(page, meta?.id) { while (true) { pos = if (meta?.id == t.id) (pc?.player?.currentPosition ?: 0L) else 0L; delay(400) } }
                    val dur = ((if (meta?.id == t.id) pc?.player?.duration?.takeIf { it > 0 } else null) ?: (t.dur * 1000L)).coerceAtLeast(1)
                    val fr = dragF ?: (pos.toFloat() / dur).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(24.dp).onSizeChanged { wpx = it.width.coerceAtLeast(1) }
                        .pointerInput(dur) { detectTapGestures { pc?.seekTo(((it.x / wpx).coerceIn(0f, 1f) * dur).toLong()) } }
                        .pointerInput(dur) { detectHorizontalDragGestures(onDragStart = { dragF = (it.x / wpx).coerceIn(0f, 1f) }, onDragEnd = { dragF?.let { f -> pc?.seekTo((f * dur).toLong()) }; dragF = null }, onDragCancel = { dragF = null }) { c, _ -> dragF = (c.position.x / wpx).coerceIn(0f, 1f) } },
                        contentAlignment = Alignment.CenterStart) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x55FFFFFF)))
                        Box(Modifier.fillMaxWidth(fr).height(3.dp).background(Color.White))
                    }
                    if (diag.isNotEmpty()) Text(diag, Modifier.padding(top = 4.dp), style = ts(11, color = Color(0xFFFFB4C8)))
                }
            }
        }
    }
}
