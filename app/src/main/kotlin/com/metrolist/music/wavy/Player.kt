package com.metrolist.music.wavy

import android.content.Intent
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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

fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

@Composable
fun PlayerScreen(pc: PlayerConnection, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val meta by pc.mediaMetadata.collectAsState()
    val playing by pc.isPlaying.collectAsState()
    val shuffle by pc.shuffleModeEnabled.collectAsState()
    val qTitle by pc.queueTitle.collectAsState()
    val m = meta
    var pos by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showAdv by remember { mutableStateOf(false) }
    var showOpts by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    var fullLy by remember { mutableStateOf<List<LyricLine>?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(m?.id) { while (true) { pos = pc.player.currentPosition; delay(400) } }
    val dur = pc.player.duration.takeIf { it > 0 } ?: ((m?.duration ?: 0) * 1000L)
    var tint by remember { mutableStateOf(Color(0xFF55091F)) }
    LaunchedEffect(m?.thumbnailUrl) {
        val u = m?.thumbnailUrl ?: return@LaunchedEffect
        try {
            val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(u).allowHardware(false).build())
            val bmp = r.image?.toBitmap() ?: return@LaunchedEffect
            val p = Palette.from(bmp).generate()
            val c = p.getDominantColor(p.getMutedColor(0xFF55091F.toInt()))
            val hsv = FloatArray(3); android.graphics.Color.colorToHSV(c, hsv)
            hsv[2] = hsv[2].coerceIn(0.45f, 0.72f); hsv[1] = hsv[1].coerceAtLeast(0.35f)
            tint = Color(android.graphics.Color.HSVToColor(hsv))
        } catch (e: Exception) { }
    }
    val bg by androidx.compose.animation.animateColorAsState(tint, tween(600), label = "bg")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0.55f).compositeOver(Color(0xFF0B0B0D)), Color(0xFF0B0B0D))))
        .pointerInput(Unit) { detectVerticalDragGestures { _, d -> if (d > 40f) onClose() } }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(scroll).padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.expand_more), null, Modifier.size(34.dp).clickable(onClick = onClose), tint = Color.White)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(qTitle ?: "Wavy", style = ts(15, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(painterResource(R.drawable.tune), null, Modifier.size(30.dp).clickable { showOpts = true }, tint = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp), 1f).padding(6.dp)) {
                AsyncImage(hiRes(m?.thumbnailUrl), null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(W.Card), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m?.title ?: "", style = ts(26, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.basicMarquee())
                    Text(m?.artists?.joinToString { it.name } ?: "", style = ts(16, color = Color(0xFFD6CCD3)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                var likedNow by remember(m?.id) { mutableStateOf(false) }
                Box(Modifier.padding(start = 16.dp).size(38.dp).clip(CircleShape).background(if (likedNow) W.Pink else Color.Transparent).border(2.dp, if (likedNow) W.Pink else Color.White, CircleShape).clickable { m?.let { x -> likedNow = true; scope.launch { likeTrack(x.id) } } }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(if (likedNow) R.drawable.check else R.drawable.add), null, Modifier.size(22.dp), tint = Color.White) }
            }
            Spacer(Modifier.height(18.dp))
            // seek bar
            var w by remember { mutableIntStateOf(1) }
            val frac = drag ?: (pos.toFloat() / dur.coerceAtLeast(1)).coerceIn(0f, 1f)
            WavySeek(frac, playing, androidx.compose.ui.graphics.lerp(bg, Color.White, 0.82f), onSeekTo = { pc.seekTo((it * dur).toLong()) }, onDrag = { drag = it })
            Row(Modifier.fillMaxWidth()) {
                Text(fmt((frac * dur).toLong()), style = ts(12, color = Color(0xFFD6CCD3))); Spacer(Modifier.weight(1f)); Text("-" + fmt((dur - (frac * dur).toLong()).coerceAtLeast(0)), style = ts(12, color = Color(0xFFD6CCD3)))
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(painterResource(R.drawable.shuffle), null, Modifier.size(34.dp).clickable { pc.player.shuffleModeEnabled = !shuffle }, tint = if (shuffle) W.Pink else Color.White)
                Icon(painterResource(R.drawable.skip_previous), null, Modifier.size(44.dp).clickable { pc.seekToPrevious() }, tint = Color.White)
                Box(Modifier.size(78.dp).clip(CircleShape).background(Color.White).clickable { pc.togglePlayPause() }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), null, Modifier.size(42.dp), tint = Color.Black) }
                Icon(painterResource(R.drawable.skip_next), null, Modifier.size(44.dp).clickable { pc.seekToNext() }, tint = Color.White)
                Icon(painterResource(R.drawable.repeat), null, Modifier.size(34.dp).clickable {
                    pc.player.repeatMode = when (pc.player.repeatMode) { 0 -> 2; 2 -> 1; else -> 0 } },
                    tint = if (pc.player.repeatMode != 0) W.Pink else Color.White)
            }
            Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                val dev by WavyFx.device
                Row(Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable {
                    val ok = try { ctx.startActivity(Intent("com.android.settings.panel.action.MEDIA_OUTPUT").putExtra("com.android.settings.panel.extra.PACKAGE_NAME", ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
                    if (!ok) try { ctx.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                }.padding(end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(if (dev.first == "earbuds") R.drawable.bluetooth else R.drawable.cast), null, Modifier.size(24.dp), tint = if (dev.first == "earbuds") W.Pink else Color.White)
                    Text(dev.second, Modifier.padding(start = 8.dp), style = ts(14, FontWeight.Medium, if (dev.first == "earbuds") W.Pink else Color(0xFFE9E4EE)), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Icon(painterResource(R.drawable.share), null, Modifier.size(30.dp).clickable {
                    val t = "${m?.title} - ${m?.artists?.joinToString { it.name }}\nhttps://music.youtube.com/watch?v=${m?.id}"
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, tint = Color.White)
                Spacer(Modifier.width(24.dp))
                Icon(painterResource(R.drawable.queue_music), null, Modifier.size(32.dp).clickable { showQueue = true }, tint = Color.White)
            }
            if (m != null) PlayerDetails(pc, m.id, m.title, m.artists.joinToString { it.name }, m.artists.map { it.name to it.id }, m.thumbnailUrl, m.album?.title, (m.duration ?: 0)) { fullLy = it }
        }
        // sticky bar when scrolled
        if (scroll.value > 900 && m != null) Row(Modifier.fillMaxWidth().background(GlassBar).statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.title, style = ts(16, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.artists.joinToString { it.name }, style = ts(13, color = Color(0xFFD6CCD3)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(painterResource(R.drawable.add_circle), null, Modifier.size(30.dp).clickable { scope.launch { likeTrack(m.id) } }, tint = Color.White)
            Spacer(Modifier.width(16.dp))
            Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), null, Modifier.size(30.dp).clickable { if (pc.player.isPlaying) pc.player.pause() else pc.player.play() }, tint = Color.White)
        }
        fullLy?.let { FullLyrics(pc, m?.title ?: "", m?.artists?.joinToString { it.name } ?: "", it) { fullLy = null } }
        if (showQueue) QueueSheet(pc) { showQueue = false }
        if (showOpts) PlayerOptions(pc, onBoost = { showOpts = false; showAdv = true }) { showOpts = false }
        if (showAdv) AdvancedPlayer(pc) { showAdv = false }
    }
}

@Composable
fun QueueSheet(pc: PlayerConnection, onClose: () -> Unit) {
    val idx by pc.currentMediaItemIndex.collectAsState()
    val p = pc.player
    val items = remember { (0 until p.mediaItemCount).map { p.getMediaItemAt(it) } }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.7f).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(GlassSheet).clickable(enabled = false) {}.padding(top = 16.dp)) {
            Text("Queue", Modifier.padding(start = 22.dp, bottom = 10.dp), style = ts(20, FontWeight.SemiBold))
            LazyColumn(Modifier.navigationBarsPadding()) {
                itemsIndexed(items) { i, it ->
                    Row(Modifier.fillMaxWidth().clickable { p.seekTo(i, 0); p.play() }.padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(it.mediaMetadata.artworkUri?.toString(), 48.dp, 8)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(it.mediaMetadata.title?.toString() ?: "", style = ts(15, FontWeight.SemiBold, if (i == idx) W.Pink else Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(it.mediaMetadata.artist?.toString() ?: "", style = ts(12, color = W.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
