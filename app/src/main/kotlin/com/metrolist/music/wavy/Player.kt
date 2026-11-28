package com.metrolist.music.wavy

import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineStart
import android.content.Intent
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
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
    LaunchedEffect(m?.id) { while (true) { pos = pc.player.currentPosition; delay(if (pc.player.isPlaying) 400 else 1000) } }
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
    // the player follows the finger when pulled down, then closes into the mini player past 160 px, or springs back
    val pullY = remember { androidx.compose.animation.core.Animatable(0f) }
    val pullScope = rememberCoroutineScope()
    // the content column scrolls and eats vertical drags, so the pull-down is also caught as leftover scroll at the top (works from the art area and anywhere on the page)
    val sheetOpenNow by rememberUpdatedState(showQueue || showOpts || showAdv || fullLy != null)
    val closeNow by rememberUpdatedState(onClose)
    val pullNsc = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (pullY.value > 0f && available.y < 0f) { val n = (pullY.value + available.y).coerceAtLeast(0f); val used = n - pullY.value; pullScope.launch(start = CoroutineStart.UNDISPATCHED) { pullY.snapTo(n) }; return androidx.compose.ui.geometry.Offset(0f, used) }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (!sheetOpenNow && available.y > 0f && source == NestedScrollSource.Drag) { val n = pullY.value + available.y; pullScope.launch(start = CoroutineStart.UNDISPATCHED) { pullY.snapTo(n) }; return androidx.compose.ui.geometry.Offset(0f, available.y) }
                return androidx.compose.ui.geometry.Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullY.value > 0f) { if (pullY.value > 160f || available.y > 1800f) closeNow() else pullY.animateTo(0f, androidx.compose.animation.core.spring(0.8f, 400f)); return available }
                return Velocity.Zero
            }
        }
    }
    Box(Modifier.fillMaxSize().nestedScroll(pullNsc).graphicsLayer { translationY = pullY.value; val f = (pullY.value / 900f).coerceIn(0f, 1f); scaleX = 1f - 0.14f * f; scaleY = 1f - 0.14f * f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f); shape = androidx.compose.foundation.shape.RoundedCornerShape((28f * f).dp); clip = f > 0f; alpha = (1f - pullY.value / 1400f).coerceIn(0.4f, 1f) }.background(Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0.55f).compositeOver(W.Base), W.Base)))
        .pointerInput(showQueue || showOpts || showAdv || fullLy != null) { if (!(showQueue || showOpts || showAdv || fullLy != null)) detectVerticalDragGestures(
            onDragEnd = { if (pullY.value > 160f) onClose() else pullScope.launch { pullY.animateTo(0f, androidx.compose.animation.core.spring(0.8f, 400f)) } },
            onDragCancel = { pullScope.launch { pullY.animateTo(0f) } }
        ) { _, d -> pullScope.launch { pullY.snapTo((pullY.value + d).coerceAtLeast(0f)) } } }) {
        WallpaperLayer("player")
        Column(Modifier.fillMaxSize().then(if ((showQueue || showOpts || showAdv || fullLy != null) && !Reduce.on) Modifier.blur(20.dp) else Modifier).statusBarsPadding().navigationBarsPadding().verticalScroll(scroll).padding(horizontal = 22.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).size(44.dp, 5.dp).clip(RoundedCornerShape(50)).background(Color(0x66FFFFFF)))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.expand_more), null, Modifier.size(34.dp).clickable(onClick = onClose), tint = Color.White)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(qTitle ?: "Wavy", style = ts(15, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(painterResource(R.drawable.tune), null, Modifier.size(30.dp).clickable { showOpts = true }, tint = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            // Apple Music behaviour: the art shrinks a little when paused and springs back to full size on play
            val artScale by androidx.compose.animation.core.animateFloatAsState(if (playing) 1f else 0.88f, androidx.compose.animation.core.spring(0.55f, 260f), label = "artScale")
            Box(Modifier.fillMaxWidth().graphicsLayer { scaleX = artScale; scaleY = artScale }.shadow(28.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = Color.Black)) {
                AsyncImage(hiRes(m?.thumbnailUrl), null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp)).background(W.Card), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m?.title ?: "", style = ts(28, FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.basicMarquee())
                    Text(m?.artists?.joinToString { it.name } ?: "", style = ts(18, color = Color(0xFFD6CCD3)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                var likedNow by remember(m?.id) { mutableStateOf(m?.id?.let { S.b("liked_$it") } ?: false) }
                var plDlg by remember { mutableStateOf(false) }
                LaunchedEffect(m?.id) { m?.id?.let { id -> val r = isLiked(id); if (r != null) { likedNow = r; S.put("liked_$id", r) } } }
                Icon(painterResource(R.drawable.add), null, Modifier.padding(start = 12.dp).size(34.dp).clip(CircleShape).clickable { plDlg = true }.padding(4.dp), tint = Color.White)
                Icon(painterResource(if (likedNow) R.drawable.favorite else R.drawable.favorite_border), null, Modifier.padding(start = 8.dp).size(38.dp).pressScale().clip(CircleShape).clickable { m?.let { x ->
                    val want = !likedNow; likedNow = want; S.put("liked_${x.id}", want)
                    scope.launch { val ok = if (want) likeTrack(x.id) else unlikeTrack(x.id); if (!ok) { likedNow = !want; S.put("liked_${x.id}", !want) } } } }.padding(4.dp), tint = if (likedNow) W.Pink else Color.White)
                Icon(painterResource(R.drawable.more_vert), null, Modifier.padding(start = 8.dp).size(36.dp).pressScale().clip(CircleShape).clickable { showOpts = true }.padding(4.dp), tint = Color.White)
                if (plDlg && m != null) AddToPlaylistDialog(m.id) { plDlg = false }
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                Icon(painterResource(R.drawable.skip_previous), null, Modifier.size(56.dp).pressScale().clickable { pc.seekToPrevious() }, tint = Color.White)
                Box(Modifier.size(84.dp).pressScale().clip(CircleShape).background(Color.White).clickable { pc.togglePlayPause() }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), null, Modifier.size(46.dp), tint = Color.Black) }
                Icon(painterResource(R.drawable.skip_next), null, Modifier.size(56.dp).pressScale().clickable { pc.seekToNext() }, tint = Color.White)
            }
            Spacer(Modifier.height(26.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                val dev by WavyFx.device
                Icon(painterResource(R.drawable.shuffle), null, Modifier.size(30.dp).pressScale().clickable { pc.player.shuffleModeEnabled = !shuffle }, tint = if (shuffle) W.Pink else Color.White)
                Spacer(Modifier.width(14.dp))
                Icon(painterResource(R.drawable.repeat), null, Modifier.size(30.dp).pressScale().clickable {
                    pc.player.repeatMode = when (pc.player.repeatMode) { 0 -> 2; 2 -> 1; else -> 0 } },
                    tint = if (pc.player.repeatMode != 0) W.Pink else Color.White)
                Spacer(Modifier.width(14.dp))
                Row(Modifier.weight(1f).padding(start = 6.dp).clip(RoundedCornerShape(50)).clickable {
                    val ok = try { ctx.startActivity(Intent("com.android.settings.panel.action.MEDIA_OUTPUT").putExtra("com.android.settings.panel.extra.PACKAGE_NAME", ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
                    if (!ok) try { ctx.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                }.padding(end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(if (dev.first == "earbuds") R.drawable.bluetooth else R.drawable.cast), null, Modifier.size(24.dp), tint = if (dev.first == "earbuds") W.Pink else Color.White)
                    Text(dev.second, Modifier.padding(start = 8.dp), style = ts(14, FontWeight.Medium, if (dev.first == "earbuds") W.Pink else Color(0xFFE9E4EE)), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Icon(painterResource(R.drawable.lyrics), null, Modifier.size(30.dp).pressScale().clip(CircleShape).clickable {
                    val mm = m ?: return@clickable
                    scope.launch {
                        val l = fetchLyrics(ctx, mm.id, mm.title, mm.artists.joinToString { it.name }, mm.duration ?: 0, mm.album?.title)
                        if (l.isEmpty()) android.widget.Toast.makeText(ctx, "No lyrics found for this song", android.widget.Toast.LENGTH_SHORT).show() else fullLy = l
                    }
                }, tint = Color.White)
                Spacer(Modifier.width(14.dp))
                Icon(painterResource(R.drawable.share), null, Modifier.size(30.dp).clickable {
                    val t = "${m?.title} - ${m?.artists?.joinToString { it.name }}\nhttps://music.youtube.com/watch?v=${m?.id}"
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, tint = Color.White)
                Spacer(Modifier.width(16.dp))
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
            var miniLiked by remember(m.id) { mutableStateOf(S.b("liked_${m.id}")) }
            Icon(painterResource(if (miniLiked) R.drawable.favorite else R.drawable.favorite_border), null, Modifier.size(30.dp).clickable { val want = !miniLiked; miniLiked = want; S.put("liked_${m.id}", want); scope.launch { if (want) likeTrack(m.id) else unlikeTrack(m.id) } }, tint = if (miniLiked) W.Pink else Color.White)
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
    var items by remember { mutableStateOf((0 until p.mediaItemCount).map { p.getMediaItemAt(it) }) }
    fun durOf(m: androidx.media3.common.MediaItem): Int = (m.localConfiguration?.tag as? com.metrolist.music.models.MediaMetadata)?.duration?.takeIf { it > 0 } ?: 0
    val total = items.sumOf { durOf(it) }
    var locked by remember { mutableStateOf(S.b("queue_locked")) }
    var dragIdx by remember { mutableIntStateOf(-1) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
    val sd = rememberSheetDrag(onClose)
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose)) {
        Column(Modifier.align(Alignment.BottomCenter).then(sd.mod).fillMaxWidth().fillMaxHeight(0.78f).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(GlassSheet).clickable(enabled = false) {}.padding(top = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Queue", Modifier.padding(start = 22.dp).weight(1f), style = ts(22, FontWeight.Bold))
                Text(if (locked) "Locked. Tap to unlock" else "Lock order", Modifier.clip(RoundedCornerShape(50)).background(if (locked) W.Pink else Color(0x22FFFFFF)).clickable { locked = !locked; S.put("queue_locked", locked) }.padding(14.dp, 7.dp), style = ts(12, FontWeight.Medium))
            }
            Text("${items.size} songs" + if (total > 0) " \u00B7 ${if (total >= 3600) "${total / 3600} hr " else ""}${(total % 3600) / 60} min" else "", Modifier.padding(start = 22.dp, bottom = 10.dp), style = ts(13, color = W.Dim))
            LazyColumn(Modifier.navigationBarsPadding()) {
                itemsIndexed(items, key = { i, it -> it.mediaId + "#" + i }) { i, it ->
                    val now = i == idx
                    Row(Modifier.fillMaxWidth().height(64.dp).graphicsLayer { translationY = if (dragIdx == i) dragDy else 0f; alpha = if (dragIdx == i) 0.85f else 1f }.then(if (now || dragIdx == i) Modifier.background(Color(0x22FFFFFF)) else Modifier).clickable { p.seekTo(i, 0); p.play() }.padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(it.mediaMetadata.artworkUri?.toString(), 48.dp, 8)
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(it.mediaMetadata.title?.toString() ?: "", style = ts(15, FontWeight.SemiBold, if (now) W.Pink else Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(it.mediaMetadata.artist?.toString() ?: "", style = ts(12, color = W.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (!locked) Text("\u2261", Modifier.pointerInput(i, items.size) { detectDragGestures(
                            onDragStart = { dragIdx = i; dragDy = 0f },
                            onDrag = { c, d -> c.consume(); dragDy += d.y },
                            onDragEnd = { val to = (i + Math.round(dragDy / rowPx)).coerceIn(0, items.size - 1); if (to != i) { p.moveMediaItem(i, to); items = (0 until p.mediaItemCount).map { k -> p.getMediaItemAt(k) } }; dragIdx = -1; dragDy = 0f },
                            onDragCancel = { dragIdx = -1; dragDy = 0f }) }.padding(horizontal = 12.dp, vertical = 10.dp), style = ts(24, color = W.Dim))
                        if (!now && !locked) Text("\u2715", Modifier.clickable { p.removeMediaItem(i); items = (0 until p.mediaItemCount).map { k -> p.getMediaItemAt(k) } }.padding(10.dp), style = ts(14, color = W.Dim))
                    }
                }
            }
        }
    }
}


private suspend fun favId(): Long? = try {
    val pls = (Api.rpc("profile_stats") as? JSONObject)?.optJSONArray("playlists")
    (0 until (pls?.length() ?: 0)).map { pls!!.getJSONObject(it) }.firstOrNull { it.optBoolean("is_favourite") }?.optLong("id")
} catch (e: Exception) { null }

/** null when the server could not be asked. */
suspend fun isLiked(id: String): Boolean? = try {
    val f = favId()
    if (f == null) null else ((Api.get("playlist_tracks?playlist_id=eq.$f&track_id=eq." + java.net.URLEncoder.encode(id, "UTF-8") + "&select=track_id") as? JSONArray)?.length() ?: 0) > 0
} catch (e: Exception) { null }

suspend fun unlikeTrack(id: String): Boolean = try {
    val f = favId()
    if (f == null) false else { Api.delete("playlist_tracks?playlist_id=eq.$f&track_id=eq." + java.net.URLEncoder.encode(id, "UTF-8")); true }
} catch (e: Exception) { false }

@Composable
fun AddToPlaylistDialog(trackId: String, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var pls by remember { mutableStateOf<List<Pair<Long, String>>?>(null) }
    LaunchedEffect(Unit) { pls = try { val arr = (Api.rpc("profile_stats") as? JSONObject)?.optJSONArray("playlists"); (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.filter { !it.optBoolean("is_favourite") }.map { it.optLong("id") to it.optString("name") } } catch (e: Exception) { emptyList() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF1A1620)).padding(vertical = 14.dp)) {
            Text("Add to playlist", Modifier.padding(20.dp, 6.dp, 20.dp, 10.dp), style = ts(18, FontWeight.SemiBold))
            val l = pls
            if (l == null) Text("Loading...", Modifier.padding(20.dp), style = ts(14, color = W.Dim))
            else if (l.isEmpty()) Text("You have no playlists yet. Create one from Library.", Modifier.padding(20.dp), style = ts(14, color = W.Dim))
            else l.forEach { (id, nm) -> Text(nm, Modifier.fillMaxWidth().clickable {
                onClose()
                scope.launch { val ok = try { Api.post("playlist_tracks", JSONObject().put("playlist_id", id).put("track_id", trackId).put("position", (System.currentTimeMillis() / 1000).toInt()).put("added_by", Api.uid)); true } catch (e: Exception) { false }
                    android.widget.Toast.makeText(ctx, if (ok) "Added to $nm" else "Could not add. Try again.", android.widget.Toast.LENGTH_SHORT).show() }
            }.padding(20.dp, 13.dp), style = ts(16), maxLines = 1) }
        }
    }
}
