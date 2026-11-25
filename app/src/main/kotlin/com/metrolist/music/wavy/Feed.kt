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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoView(id: String, startSec: Int, onTime: (Float) -> Unit, onErr: (Int) -> Unit, modifier: Modifier) {
    AndroidView(modifier = modifier, factory = { c ->
        WebView(c).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true; settings.mediaPlaybackRequiresUserGesture = false; settings.domStorageEnabled = true
            addJavascriptInterface(VideoBridge(onTime, onErr), "Android")
            val html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>html,body,#p,iframe{margin:0;width:100%;height:100%;background:#000;overflow:hidden}</style></head><body><div id='p'></div><script src='https://www.youtube.com/iframe_api'></script><script>var pl;function onYouTubeIframeAPIReady(){pl=new YT.Player('p',{width:'100%',height:'100%',videoId:'$id',playerVars:{autoplay:1,controls:0,playsinline:1,rel:0,modestbranding:1,start:$startSec,fs:0,disablekb:1},events:{onReady:function(e){e.target.playVideo();setInterval(function(){Android.onTime(pl.getCurrentTime())},500)},onError:function(e){Android.onErr(e.data)},onStateChange:function(e){Android.onState(e.data)}}})}</script></body></html>"
            loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
        }
    }, onRelease = { it.destroy() })
}

@Composable
fun MusicFeed(pc: PlayerConnection?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val meta by (pc?.mediaMetadata ?: remember { MutableStateFlow(null) }).collectAsState()
    var feed by remember { mutableStateOf<List<Track>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    val seed = remember { System.currentTimeMillis().toString() }
    // the song that is playing when the tab opens stays first, so nothing restarts
    val first = remember { meta?.let { m -> Track(m.id, m.title, m.artists.joinToString { it.name }, m.thumbnailUrl, m.duration, m.artists.firstOrNull()?.id, m.album?.title) } }
    LaunchedEffect(Unit) {
        try { feed = (Api.rpc("player_feed", JSONObject().put("p_limit", 40).put("p_offset", 0).put("p_region", "IN").put("p_seed", seed)) as? JSONArray).tracks() }
        catch (e: ApiError) { err = e.message }
    }
    val tracks = remember(feed) { (listOfNotNull(first) + feed.filter { it.id != first?.id }) }
    if (tracks.isEmpty()) {
        Backdrop { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(err ?: "Loading...", style = ts(15, color = W.Dim)) } }
        return
    }
    val pager = rememberPagerState { tracks.size }
    var queueOwned by remember { mutableStateOf(false) }
    var video by remember { mutableStateOf(false) }
    var vtime by remember { mutableFloatStateOf(0f) }
    var vnote by remember { mutableStateOf<String?>(null) }
    fun leaveVideo() { if (video) { video = false; pc?.player?.seekTo((vtime * 1000).toLong()); pc?.player?.play() } }
    LaunchedEffect(pager.settledPage, pc != null) {
        if (pc == null) return@LaunchedEffect
        val cur = tracks[pager.settledPage]
        if (meta?.id == cur.id) return@LaunchedEffect
        if (video) { vtime = 0f; return@LaunchedEffect }
        if (queueOwned && pc.player.mediaItemCount == tracks.size) pc.player.seekTo(pager.settledPage, 0L).also { pc.player.play() }
        else { playTracks(pc, tracks, pager.settledPage, "Music"); queueOwned = true }
    }
    DisposableEffect(Unit) { onDispose { if (video) { pc?.player?.seekTo((vtime * 1000).toLong()); pc?.player?.play() } } }
    Box(Modifier.fillMaxSize()) {
        VerticalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = true) { page ->
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
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.statusBarsPadding().height(96.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (video && page == pager.settledPage) {
                            VideoView(t.id, (vtime.toInt()).coerceAtLeast(0), { vtime = it }, { c -> vnote = "This video can't be played inside Wavy."; leaveVideo() }, Modifier.fillMaxSize())
                        } else AsyncImage(t.cover, null, Modifier.fillMaxSize().background(W.Card), contentScale = ContentScale.Crop)
                        Column(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Icon(painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border), null, Modifier.size(34.dp).clickable { liked = true; scope.launch { likeTrack(t.id) } }, tint = if (liked) W.Pink else Color.White)
                            Icon(painterResource(R.drawable.share), null, Modifier.size(32.dp).clickable {
                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${t.title} - ${t.artist}\nhttps://music.youtube.com/watch?v=${t.id}"), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }, tint = Color.White)
                        }
                    }
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 108.dp)) {
                        Text(t.title, style = ts(24, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, style = ts(18, color = Color(0xDDFFFFFF)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(14.dp))
                        var pos by remember { mutableLongStateOf(0L) }
                        LaunchedEffect(page, meta?.id, video) { while (true) { pos = if (video) (vtime * 1000).toLong() else if (meta?.id == t.id) (pc?.player?.currentPosition ?: 0L) else 0L; delay(400) } }
                        val dur = (t.dur * 1000L).coerceAtLeast(1)
                        Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x55FFFFFF))) { Box(Modifier.fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f)).fillMaxHeight().background(Color.White)) }
                        if (vnote != null) Text(vnote!!, Modifier.padding(top = 10.dp), style = ts(12, color = Color(0xFFFFB4C8)))
                    }
                }
            }
        }
        // Photo / Video switch
        Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 14.dp, end = 18.dp).clip(RoundedCornerShape(50)).background(Color(0x66000000)).padding(4.dp)) {
            listOf("Photo" to false, "Video" to true).forEach { (n, v) ->
                Text(n, Modifier.clip(RoundedCornerShape(50)).background(if (video == v) Color.White else Color.Transparent).clickable {
                    if (v && !video) { vnote = null; vtime = ((pc?.player?.currentPosition ?: 0L) / 1000f); pc?.player?.pause(); video = true }
                    else if (!v) leaveVideo()
                }.padding(horizontal = 20.dp, vertical = 10.dp), style = ts(16, FontWeight.Medium, if (video == v) Color(0xFFB0124A) else Color.White))
            }
        }
    }
}
