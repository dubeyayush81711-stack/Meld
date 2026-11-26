package com.metrolist.music.wavy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import com.metrolist.music.extensions.toMediaItem
import android.widget.Toast
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer

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

private val videoCandCache = HashMap<String, Pair<Long, List<VCand>>>()

@Composable
fun StreamVideo(id: String, pc: PlayerConnection?, modifier: Modifier, onDiag: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    var cands by remember(id) { mutableStateOf<List<VCand>>(videoCandCache[id]?.takeIf { System.currentTimeMillis() - it.first < 90 * 60_000L }?.second ?: emptyList()) }
    var idx by remember(id) { mutableIntStateOf(0) }
    var ready by remember(id) { mutableStateOf(false) }
    var arOv by remember(id) { mutableFloatStateOf(0f) }
    LaunchedEffect(id) {
        if (cands.isNotEmpty()) return@LaunchedEffect
        onDiag("video: loading")
        val (list, why) = fetchVideoCandidates(id)
        if (list.isEmpty()) onDiag(why.ifEmpty { "no video stream" }) else videoCandCache[id] = System.currentTimeMillis() to list
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
                if (kotlin.math.abs(ex.currentPosition - want) > 350) ex.seekTo(want)
            }
            delay(150)
        }
    }
    val tvRef = remember(ex) { mutableStateOf<android.view.TextureView?>(null) }
    val lifeOwner = (ctx as androidx.lifecycle.LifecycleOwner)
    DisposableEffect(ex, lifeOwner) {
        val o = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_START || e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // coming back from Recents: the video surface was torn down, attach it again and jump to the audio position
                tvRef.value?.let { try { ex.clearVideoTextureView(it); ex.setVideoTextureView(it) } catch (_: Exception) {} }
                pc?.player?.let { p -> try { if (ex.playbackState == androidx.media3.common.Player.STATE_IDLE) ex.prepare() } catch (_: Exception) {}; ex.seekTo(p.currentPosition); ex.playWhenReady = p.isPlaying }
            }
        }
        lifeOwner.lifecycle.addObserver(o)
        onDispose { lifeOwner.lifecycle.removeObserver(o) }
    }
    val ar = if (arOv > 0f) arOv else cand.w.toFloat() / cand.h.coerceAtLeast(1)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.clipToBounds()) {
        val h = maxHeight
        val fh = minOf(h * 0.9f, (maxWidth / ar) * 2f); val fw = fh * ar
        AndroidView(modifier = Modifier.align(Alignment.Center).offset(y = -(h * 0.04f)).requiredSize(fw, fh), factory = { c -> android.view.TextureView(c).also { tvRef.value = it; ex.setVideoTextureView(it) } })
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MusicFeed(pc: PlayerConnection?, onSearch: () -> Unit = {}, active: Boolean = true) {
    var showFx by remember { mutableStateOf(false) }
    val lyricsPref = boolPref("reel_lyrics", true); var showOpts by remember { mutableStateOf(false) }
    val glowPref = boolPref("reel_glow", true); val videoPref = boolPref("reel_video", false); val hookPref = boolPref("reel_hook", true)
    val videoOn = videoPref.value
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val playing by (pc?.isPlaying ?: remember { MutableStateFlow(false) }).collectAsState()
    val meta by (pc?.mediaMetadata ?: remember { MutableStateFlow(null) }).collectAsState()
    var feed by remember { mutableStateOf<List<Track>>(loadFeedCache(S.s("feed_langs", "Punjabi,Haryanvi"))) }
    var err by remember { mutableStateOf<String?>(null) }
    var seed by remember { mutableStateOf(System.currentTimeMillis().toString()) }
    var refreshN by remember { mutableIntStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    // the song that is playing when the tab opens stays first, so nothing restarts
    var first by remember { mutableStateOf(meta?.let { m -> Track(m.id, m.title, m.artists.joinToString { it.name }, m.thumbnailUrl, m.duration, m.artists.firstOrNull()?.id, m.album?.title) }) }
    var userMoved by remember { mutableStateOf(false) }
    var followed by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(Unit) { followed = followedSongs() }
    var langs by remember { mutableStateOf(S.s("feed_langs", "Punjabi,Haryanvi").split(",").filter { it.isNotBlank() }) }
    var showLang by remember { mutableStateOf(false) }
    var feedGen by remember { mutableIntStateOf(0) }
    var reloading by remember { mutableStateOf(false) }
    var langSkip by remember { mutableStateOf(true) }
    LaunchedEffect(refreshN, langs) {
        var attempt = 0
        reloading = refreshN > 0 || !langSkip
        while (true) {
            try {
                val got = mixFollowed(feedPage(langs, 0, seed), followedSongs())
                if (got.isNotEmpty()) { if (refreshN > 0) { first = null; feedGen++ }; feed = got; err = null; saveFeedCache(langs.joinToString(","), got); break }
                if (feed.isNotEmpty() && refreshN == 0) break
            } catch (e: ApiError) { err = humanErr(e) } catch (e: Exception) { err = "No connection. Retrying..." }
            attempt++; if (attempt >= 4) { if (feed.isEmpty()) err = err ?: "Could not load songs. Pull down to try again."; break }
            delay(1200L * attempt)
        }
        reloading = false
    }
    LaunchedEffect(meta?.id) {
        val m = meta ?: return@LaunchedEffect
        if (first?.id != m.id && feed.none { it.id == m.id }) first = Track(m.id, m.title, m.artists.joinToString { it.name }, m.thumbnailUrl, m.duration, m.artists.firstOrNull()?.id, m.album?.title)
    }
    val tracks = remember(feed, refreshN, first, meta?.id) {
        val curId = meta?.id
        val head = listOfNotNull(first); val hk = head.map { normKey(it) }.toSet()
        val curKey = feed.firstOrNull { it.id == curId }?.let { normKey(it) }
        // the song the player is actually on is never dropped, so the page can always follow it
        head + feed.filter { it.id != first?.id && (it.id == curId || (normKey(it) !in hk && normKey(it) != curKey)) }.distinctBy { if (it.id == curId) "cur:" + it.id else normKey(it) }
    }
    if (tracks.isEmpty()) {
        Backdrop { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (err == null || err!!.endsWith("...")) androidx.compose.material3.CircularProgressIndicator(Modifier.size(34.dp), color = W.Pink, strokeWidth = 3.dp)
                Spacer(Modifier.height(14.dp))
                Text(err ?: "Getting your songs ready...", style = ts(15, color = W.Dim))
                if (err != null && !err!!.endsWith("...")) Text("Tap to retry", Modifier.padding(top = 12.dp).clickable { err = null; refreshN++ }, style = ts(15, FontWeight.SemiBold, W.Pink))
            }
        } }
        return
    }
    var queueOwned by remember { mutableStateOf(false) }
    var lastDriven by remember { mutableIntStateOf(-1) }
    val pager = rememberPagerState { tracks.size }
    LaunchedEffect(pager.settledPage, tracks.size) {
        if (tracks.size - pager.settledPage <= 5 && !loadingMore && feed.size < 200) {
            loadingMore = true
            try { val more = feedPage(langs, feed.size, seed); val have = feed.map { it.id }.toSet(); feed = feed + mixFollowed(more, followed, HashSet(have)).filter { it.id !in have } } catch (e: Exception) { }
            loadingMore = false
        }
    }
    LaunchedEffect(MusicRefresh.n) { if (MusicRefresh.n > 0) { seed = System.currentTimeMillis().toString(); queueOwned = false; userMoved = true; first = null; refreshN++ } }
    LaunchedEffect(langs) { if (langSkip) langSkip = false else { seed = System.currentTimeMillis().toString(); queueOwned = false; userMoved = true; first = null; refreshN++ } }
    LaunchedEffect(refreshN) { if (refreshN > 0) { lastDriven = 0; pager.scrollToPage(0) } }
    // when the song changes by itself (auto next, notification, screen off/on), the pager follows it; it never forces a song
    LaunchedEffect(meta?.id, first?.id, tracks.size) {
        val idx = tracks.indexOfFirst { it.id == meta?.id }
        if (idx < 0) { meta?.let { m -> first = Track(m.id, m.title, m.artists.joinToString { it.name }, m.thumbnailUrl, m.duration, m.artists.firstOrNull()?.id, m.album?.title) } }
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
    LaunchedEffect(pager.interactionSource) { pager.interactionSource.interactions.collect { if (it is androidx.compose.foundation.interaction.DragInteraction.Start) userMoved = true } }
    LaunchedEffect(meta?.id) { meta?.let { m -> Played.mark(Track(m.id, m.title.toString(), m.artists.joinToString { it.name }, null, 0, null, null)) } }
    var genSeen by remember { mutableIntStateOf(0) }
    LaunchedEffect(pager.settledPage, pc != null, feedGen, active) {
        if (pc == null || !active) return@LaunchedEffect
        if (feedGen != genSeen) { genSeen = feedGen; lastDriven = -1; queueOwned = false; if (pager.settledPage != 0) { pager.scrollToPage(0); return@LaunchedEffect } }
        val cur = tracks[pager.settledPage.coerceAtMost(tracks.size - 1)]
        Played.mark(cur)
        if (meta?.id == cur.id) { lastDriven = pager.settledPage; return@LaunchedEffect }
        if (lastDriven == pager.settledPage) return@LaunchedEffect
        // opening the tab never replaces what is already playing; only a swipe or a refresh does
        if (!userMoved && refreshN == 0 && pc.player.mediaItemCount > 0) { lastDriven = pager.settledPage; return@LaunchedEffect }
        lastDriven = pager.settledPage
        if (queueOwned && pager.settledPage < pc.player.mediaItemCount && pc.player.getMediaItemAt(pager.settledPage).mediaId == cur.id) pc.player.seekTo(pager.settledPage, 0L).also { pc.player.play() }
        else { playTracks(pc, tracks, pager.settledPage, "Music"); queueOwned = true }
    }
    // if a song does not start, nudge it once, then move on to the next one
    LaunchedEffect(meta?.id, pc) {
        val p = pc?.player ?: return@LaunchedEffect
        if (meta == null) return@LaunchedEffect
        var waited = 0
        while (waited < 16_000) {
            delay(500); waited += 500
            if (p.isPlaying || !p.playWhenReady) return@LaunchedEffect
            if (waited == 8_000) { try { p.prepare(); p.play() } catch (_: Exception) { } }
        }
        if (!p.isPlaying && p.playWhenReady && p.hasNextMediaItem()) { try { p.seekToNext(); p.play() } catch (_: Exception) { } }
    }
    val hooked = remember { HashSet<String>() }
    LaunchedEffect(pager.settledPage, meta?.id, hookPref.value, active) {
        if (!hookPref.value || !active) return@LaunchedEffect
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
            if (p.isPlaying && p.currentPosition >= start + 25_000 && pager.settledPage < tracks.size - 1) { userMoved = true; pager.animateScrollToPage(pager.settledPage + 1); return@LaunchedEffect }
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
            var pos by remember(t.id) { mutableLongStateOf(0L) }
            LaunchedEffect(page, meta?.id) { while (true) { pos = if (meta?.id == t.id) (pc?.player?.currentPosition ?: 0L) else 0L; delay(250) } }
            var lyricStyle by remember { mutableIntStateOf(S.f("lyric_style", 0f).toInt()) }
            var showPlain by remember(t.id) { mutableStateOf(false) }
            var lyrics by remember(t.id) { mutableStateOf<List<LyricLine>>(emptyList()) }
            LaunchedEffect(t.id) { lyrics = fetchLyrics(ctx, t.id, t.title, t.artist, t.dur, t.album) }
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
                if (glowPref.value && page == pager.settledPage && active && playing) ReelGlow(bg)
                if (videoOn) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xFF0B0A10), 1f to Color(0xFF000000))))
                    if (page == pager.settledPage && active) StreamVideo(t.id, pc, Modifier.fillMaxSize()) { diag = it }
                    run {
                        val synced = if (lyricsPref.value) lyrics.filter { it.t >= 0 } else emptyList()
                        val ci = synced.indexOfLast { it.t <= pos + 250 }
                        if (synced.isNotEmpty() && ci >= 0) { Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(420.dp).background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0x99000000)))); 
if (synced.isNotEmpty() && ci >= 0) LyricsView(synced, ci, pos, lyricStyle, Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, end = 90.dp, top = 64.dp).fillMaxWidth(0.7f).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { lyricStyle = (lyricStyle + 1) % LYRIC_STYLES.size; S.put("lyric_style", lyricStyle.toFloat()) }) }
                    }
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val heroH = (maxHeight - 392.dp).coerceIn(300.dp, maxWidth)
                        val side = (maxWidth - 64.dp).coerceAtMost(maxHeight - 430.dp).coerceAtLeast(200.dp)
                        var art by remember(t.id) { mutableStateOf(t.cover) }
                        LaunchedEffect(t.id) { art = squareArt(t) ?: t.cover }
                        if (!Reduce.on) AsyncImage(hiRes(art), null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.32f }.blur(60.dp), contentScale = ContentScale.Crop)
                        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(side).shadow(24.dp, RoundedCornerShape(22.dp), clip = false, ambientColor = Color.Black, spotColor = Color.Black).clip(RoundedCornerShape(22.dp)).background(Color(0xFF1A1620))) {
                                AsyncImage(hiRes(art), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                            Box(Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 64.dp, max = 120.dp)) {
                                val synced = if (lyricsPref.value) lyrics.filter { it.t >= 0 } else emptyList()
                                val ci = synced.indexOfLast { it.t <= pos + 250 }
                            if (synced.isNotEmpty() && ci >= 0) LyricsView(synced, ci, pos, lyricStyle, Modifier.align(Alignment.CenterStart).padding(horizontal = 28.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { lyricStyle = (lyricStyle + 1) % LYRIC_STYLES.size; S.put("lyric_style", lyricStyle.toFloat()) })
                            else if (lyricsPref.value && lyrics.none { it.t >= 0 } && lyrics.isNotEmpty()) Text("Lyrics", Modifier.align(Alignment.CenterStart).padding(start = 28.dp).clip(RoundedCornerShape(50)).background(Color(0x55000000)).border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(50)).clickable { showPlain = true }.padding(horizontal = 20.dp, vertical = 9.dp), style = ts(15, FontWeight.SemiBold))
                            }
                        }
                    }
                }
                if (showPlain) androidx.compose.ui.window.Dialog(onDismissRequest = { showPlain = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D)).statusBarsPadding()) {
                        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 80.dp, bottom = 60.dp)) {
                            item { Text(t.title, style = ts(22, FontWeight.Bold)); Text(t.artist, Modifier.padding(bottom = 18.dp), style = ts(15, color = Color(0xB3FFFFFF))) }
                            items(lyrics.size) { i -> Text(lyrics[i].text, Modifier.padding(vertical = 5.dp), style = ts(19, FontWeight.Medium)) }
                        }
                        Icon(painterResource(R.drawable.close), "Close", Modifier.padding(20.dp).size(28.dp).clickable { showPlain = false }, tint = Color.White)
                    }
                }
                if (flash == 1) Box(Modifier.align(Alignment.Center).size(84.dp).clip(CircleShape).background(Color(0x88000000)), contentAlignment = Alignment.Center) { Icon(painterResource(if (playing) R.drawable.play else R.drawable.pause), null, Modifier.size(46.dp), tint = Color.White) }
                if (flash > 1) Text(when (flash) { 2 -> "-10s"; else -> "+10s" }, Modifier.align(when (flash) { 2 -> Alignment.CenterStart; 3 -> Alignment.CenterEnd; else -> Alignment.Center }).padding(horizontal = 40.dp).clip(RoundedCornerShape(50)).background(Color(0x88000000)).padding(horizontal = 18.dp, vertical = 10.dp), style = ts(18, FontWeight.SemiBold))
                if (hs > 0.01f) Icon(painterResource(R.drawable.favorite), null, Modifier.align(Alignment.Center).size((60 + 80 * hs).dp), tint = W.Pink.copy(alpha = hs))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x55000000), 0.2f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color(0xDD000000))))
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clip(RoundedCornerShape(50)).clickable { showLang = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (langs.isEmpty()) "For You" else langs.joinToString(" + "), style = ts(18, FontWeight.SemiBold), maxLines = 1)
                        Icon(painterResource(R.drawable.expand_more), null, Modifier.size(22.dp), tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(painterResource(R.drawable.search), null, Modifier.clickable { onSearch() }.padding(8.dp).size(24.dp), tint = Color.White)
                    Icon(painterResource(R.drawable.more_vert), null, Modifier.clickable { showFx = true }.padding(8.dp).size(24.dp), tint = Color.White)
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 84.dp)) {
                    Text(t.title, style = ts(26, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.artist, Modifier.weight(1f, fill = false), style = ts(17, color = Color(0xDDFFFFFF)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(12.dp))
                        FollowButton(t.artistId, t.artist)
                    }
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        @Composable fun Act(icon: Int, tint: Color = Color.White, on: () -> Unit) = Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x22FFFFFF)).clickable { on() }, contentAlignment = Alignment.Center) { Icon(painterResource(icon), null, Modifier.size(24.dp), tint = tint) }
                        Act(if (liked) R.drawable.favorite else R.drawable.favorite_border, if (liked) W.Pink else Color.White) { liked = true; scope.launch { likeTrack(t.id) } }
                        Act(R.drawable.share) { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${t.title} - ${t.artist}\nhttps://music.youtube.com/watch?v=${t.id}"), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        Act(R.drawable.more_vert) { showFx = true }
                    }
                    Spacer(Modifier.height(10.dp))
                    val dur = ((if (meta?.id == t.id) pc?.player?.duration?.takeIf { it > 0 } else null) ?: (t.dur * 1000L)).coerceAtLeast(1)
                    WavySeek((pos.toFloat() / dur).coerceIn(0f, 1f), playing && meta?.id == t.id, androidx.compose.ui.graphics.lerp(bg, Color.White, 0.82f), onSeekTo = { f -> pc?.seekTo((f * dur).toLong()) })
                    fun mmss(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }
                    Row(Modifier.fillMaxWidth()) { Text(mmss(pos), style = ts(12, color = Color(0xCCFFFFFF))); Spacer(Modifier.weight(1f)); Text(mmss(dur), style = ts(12, color = Color(0xCCFFFFFF))) }
                }
            }
        }
        if (reloading) Text("Updating songs...", Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp).clip(RoundedCornerShape(50)).background(Color(0xCC14080F)).padding(horizontal = 16.dp, vertical = 7.dp), style = ts(13, FontWeight.Medium))
        var cutOpen by remember { mutableStateOf(false) }
        if (cutOpen) { val ct0 = tracks.getOrNull(pager.currentPage); if (ct0 != null) RingtoneCutter(ct0, pc) { cutOpen = false } }
        run {
            val bufState by (pc?.playbackState ?: remember { MutableStateFlow(0) }).collectAsState()
            if (bufState == androidx.media3.common.Player.STATE_BUFFERING && meta != null && !playing)
                androidx.compose.material3.CircularProgressIndicator(Modifier.align(Alignment.Center).size(38.dp), color = Color.White.copy(alpha = 0.85f), strokeWidth = 3.dp)
        }
        if (showLang) {
            var titles by remember { mutableStateOf<List<String>>(emptyList()) }
            LaunchedEffect(Unit) { titles = languageTiles().map { it.second } }
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { showLang = false })
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color(0xF0121016)).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF)))
                Text("What do you want to hear?", style = ts(18, FontWeight.SemiBold))
                Text("Pick one or more. The feed follows them.", Modifier.padding(top = 4.dp, bottom = 14.dp), style = ts(13, color = Color(0xFFB9B2C4)))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    @Composable fun Chip(label: String, on: Boolean, click: () -> Unit) = Text(label, Modifier.clip(RoundedCornerShape(50)).background(if (on) W.Pink else Color(0x22FFFFFF)).clickable { click() }.padding(horizontal = 18.dp, vertical = 10.dp), style = ts(15, FontWeight.Medium))
                    Chip("Mixed", langs.isEmpty()) { langs = emptyList(); S.put("feed_langs", ""); showLang = false }
                    titles.forEach { t -> val on = langs.any { it.equals(t, true) }
                        Chip(t, on) { langs = if (on) langs.filterNot { it.equals(t, true) } else langs + t; S.put("feed_langs", langs.joinToString(",")) } }
                }
            }
        }
        if (showOpts && pc != null) PlayerOptions(pc, onBoost = {}, onClose = { showOpts = false })
        if (showFx) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { showFx = false })
            BoxWithConstraints(Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.86f).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(Color(0xF0121016)).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(top = 14.dp, bottom = 120.dp)) {
                Text("Music features", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = ts(18, FontWeight.SemiBold))
                SToggle("Video player", "Play the music video behind the song. Off shows the cover.", videoPref)
                SToggle("Lyrics", "Show synced lyrics on the cover when a song has them.", lyricsPref)
                SToggle("Animated light", "Soft moving glow behind the song while it plays.", glowPref)
                SToggle("Hook only", "Play about 25 seconds of each song, then move to the next.", hookPref)
                SToggle("Reduce effects", "Turns off blur, the drifting background and the moving wave. Smoother on slower phones.", Reduce.state)
                val cur = meta
                if (cur != null) {
                    @Composable fun Row2(title: String, sub: String, on: () -> Unit) = Column(Modifier.fillMaxWidth().clickable { on() }.padding(horizontal = 20.dp, vertical = 12.dp)) { Text(title, style = ts(17, FontWeight.Medium)); Text(sub, style = ts(13, color = Color(0xFFB9B2C4))) }
                    Row2("Copy link", "Copy the YouTube Music link of this song.") {
                        (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("song", "https://music.youtube.com/watch?v=${cur.id}")); showFx = false
                    }
                    val ct = tracks.getOrNull(pager.settledPage)
                    if (ct != null) {
                        Text("This song", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = ts(14, FontWeight.SemiBold, W.Pink))
                        Row2("Add to favourites", "Save it to your liked songs.") { scope.launch { Toast.makeText(ctx, if (likeTrack(ct.id)) "Added to Favourites" else "Could not add. Try again.", Toast.LENGTH_SHORT).show() } }
                        Row2("Play next", "Plays right after this song.") { pc?.playNext(ct.toMeta().toMediaItem()); Toast.makeText(ctx, "Playing next", Toast.LENGTH_SHORT).show() }
                        Row2("Ringtone cutter", "Trim the song and save it as a ringtone.") { cutOpen = true; showFx = false }
                        Row2("Add to queue", "Adds it to the end of the queue.") { pc?.addToQueue(ct.toMeta().toMediaItem()); Toast.makeText(ctx, "Added to queue", Toast.LENGTH_SHORT).show() }
                        var plOpen by remember { mutableStateOf(false) }
                        var pls by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
                        Row2("Add to playlist", "Pick one of your playlists.") { plOpen = !plOpen; if (plOpen) scope.launch { try { val arr = (Api.rpc("profile_stats") as? JSONObject)?.optJSONArray("playlists"); pls = (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.filter { !it.optBoolean("is_favourite") }.map { it.optLong("id") to it.optString("name") } } catch (e: Exception) { } } }
                        if (plOpen) {
                            if (pls.isEmpty()) Text("No playlists yet", Modifier.padding(horizontal = 40.dp, vertical = 8.dp), style = ts(14, color = W.Dim))
                            pls.forEach { (id, nm) -> Text(nm, Modifier.fillMaxWidth().clickable { plOpen = false; scope.launch { val ok = try { Api.post("playlist_tracks", JSONObject().put("playlist_id", id).put("track_id", ct.id).put("position", (System.currentTimeMillis() / 1000).toInt()).put("added_by", Api.uid)); true } catch (e: Exception) { false }; Toast.makeText(ctx, if (ok) "Added to $nm" else "Could not add. Try again.", Toast.LENGTH_SHORT).show() } }.padding(horizontal = 40.dp, vertical = 12.dp), style = ts(16)) }
                        }
                        var sleepMin by remember { mutableIntStateOf(0) }
                        Row2("Sleep timer", if (sleepMin == 0) "Off. Tap to pause the music after 15, 30 or 60 minutes." else "Pausing in $sleepMin min. Tap to change.") {
                            sleepMin = when (sleepMin) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 }
                            SleepTimer.set(pc, sleepMin)
                        }
                    }
                    if (pc != null) Row2("Player options", "Equalizer, tempo and pitch, download and more for this song.") { showFx = false; showOpts = true }
                }
            }
            }
        }
    }
}
/** Double-tapping the Music item in the nav bar bumps this; the feed refreshes. */
object MusicRefresh { var n by androidx.compose.runtime.mutableIntStateOf(0) }

object SleepTimer {
    /** Uses the player service's own sleep timer, so it survives this screen closing and fades the music out. */
    fun set(pc: com.metrolist.music.playback.PlayerConnection?, min: Int) {
        try { if (min <= 0) pc?.service?.sleepTimer?.clear() else pc?.service?.sleepTimer?.start(min) } catch (e: Exception) { }
    }
}

private val artCache = HashMap<String, String?>()
/** Best square artwork for a song: the album art YouTube Music has for it, not the 16:9 video frame. */
suspend fun squareArt(t: Track): String? {
    if (artCache.containsKey(t.id)) return artCache[t.id]
    val u = try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.metrolist.innertube.YouTube.queue(listOf(t.id)).getOrNull()?.firstOrNull()?.thumbnail } } catch (e: Exception) { null }
    val good = u?.takeIf { it.contains("googleusercontent.com") || it.contains("ggpht.com") }
    artCache[t.id] = good
    return good
}

private fun loadFeedCache(langs: String): List<Track> = try {
    val a = org.json.JSONArray(S.s("feed_cache_" + langs, "[]"))
    (0 until a.length()).map { val o = a.getJSONObject(it); Track(o.getString("i"), o.getString("t"), o.getString("a"), o.optString("c").ifBlank { null }, o.optInt("d"), o.optString("x").ifBlank { null }, o.optString("l").ifBlank { null }) }
} catch (e: Exception) { emptyList() }
private fun saveFeedCache(langs: String, l: List<Track>) { try {
    val a = org.json.JSONArray(); l.take(24).forEach { a.put(org.json.JSONObject().put("i", it.id).put("t", it.title).put("a", it.artist).put("c", it.cover ?: "").put("d", it.dur).put("x", it.artistId ?: "").put("l", it.album ?: "")) }
    S.put("feed_cache_" + langs, a.toString())
} catch (_: Exception) { } }


/** Two soft colour blobs drifting slowly. Plain gradients only, no blur, so it stays cheap on slow phones. */
@Composable
fun ReelGlow(tint: Color) {
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "glow")
    val a by tr.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(9000, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "ga")
    val b by tr.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(13000, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "gb")
    Box(Modifier.fillMaxSize().drawBehind {
        val r = size.maxDimension * 0.55f
        val c1 = androidx.compose.ui.geometry.Offset(size.width * (0.15f + 0.7f * a), size.height * (0.25f + 0.2f * b))
        val c2 = androidx.compose.ui.geometry.Offset(size.width * (0.85f - 0.7f * b), size.height * (0.7f - 0.2f * a))
        drawCircle(Brush.radialGradient(listOf(W.Pink.copy(alpha = 0.22f), Color.Transparent), c1, r), r, c1)
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.55f), Color.Transparent), c2, r), r, c2)
    })
}
