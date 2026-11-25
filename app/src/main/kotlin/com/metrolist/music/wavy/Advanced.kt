package com.metrolist.music.wavy

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Path
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection

object Booster {
    private var le: android.media.audiofx.LoudnessEnhancer? = null
    private var sid = -1
    fun apply(pc: PlayerConnection, v: Float) {
        try {
            val id = pc.player.audioSessionId
            if (id <= 0) return
            if (le == null || sid != id) { try { le?.release() } catch (e: Exception) { }; le = android.media.audiofx.LoudnessEnhancer(id); sid = id }
            le?.setTargetGain((v * 1500).toInt()); le?.enabled = v > 0.01f
        } catch (e: Exception) { }
    }
}

@Composable
fun AdvancedPlayer(pc: PlayerConnection, onClose: () -> Unit) {
    val meta by pc.mediaMetadata.collectAsState()
    val playing by pc.isPlaying.collectAsState()
    val m = meta
    var boost by floatPref("vol_boost", 0f)
    var flash by boolPref("fx_flash")
    var vib by boolPref("fx_vibrate")
    var strobe by boolPref("fx_strobe")
    LaunchedEffect(m?.id, boost) { Booster.apply(pc, boost) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1A0B2E), Color(0xFF0B0612), Color(0xFF220A22)))).pointerInput(Unit) { detectDragGestures { _, _ -> } }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.arrow_back), null, Modifier.size(28.dp).clickable(onClick = onClose), tint = Color.White)
                Text("${m?.title ?: ""} - ${m?.artists?.joinToString { it.name } ?: ""}", Modifier.weight(1f).padding(horizontal = 12.dp), style = ts(17, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Spacer(Modifier.size(28.dp))
            }
            Spacer(Modifier.height(10.dp))
            AsyncImage(m?.thumbnailUrl, null, Modifier.width(230.dp).height(300.dp).clip(RoundedCornerShape(topStart = 115.dp, topEnd = 115.dp)).background(W.Card), contentScale = ContentScale.Crop)
            Spacer(Modifier.height(14.dp))
            // curved volume booster dial
            var wpx by remember { mutableIntStateOf(1) }
            Box(Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 24.dp).pointerInput(Unit) {
                detectDragGestures(onDragStart = { o -> boost = (o.x / size.width).coerceIn(0f, 1f) }) { c, _ -> boost = (c.position.x / size.width).coerceIn(0f, 1f) }
            }) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    val steps = 60
                    fun ay(t: Float): Float { val d = (t - 0.5f) * 2f; return 2f + (h - 24f) * (1f - d * d) * 0f + (h - 24f) * d * d * -1f + (h - 24f) }
                    val arc = Path()
                    for (i in 0..steps) { val t = i / steps.toFloat(); val y = ay(t); if (i == 0) arc.moveTo(t * w, y) else arc.lineTo(t * w, y) }
                    drawPath(arc, Color(0xFFEDE7F0), style = Stroke(3f))
                    val kx = boost * w; val kd = (boost - 0.5f) * 2f
                    drawCircle(Color.White, 8.dp.toPx(), Offset(kx, ay(boost)))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp).offset(y = (-14).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Min", style = ts(15, color = Color(0xFFE6DDE9))); Text("Max", style = ts(15, color = Color(0xFFE6DDE9)))
            }
            Row(Modifier.fillMaxWidth().offset(y = (-6).dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.replay), null, Modifier.size(38.dp).clickable { pc.player.seekTo((pc.player.currentPosition - 10000).coerceAtLeast(0)) }, tint = Color.White)
                Icon(painterResource(R.drawable.skip_previous), null, Modifier.size(40.dp).clickable { pc.seekToPrevious() }, tint = Color.White)
                Box(Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable { pc.player.let { if (it.isPlaying) it.pause() else it.play() } }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), null, Modifier.size(36.dp), tint = Color.Black) }
                Icon(painterResource(R.drawable.skip_next), null, Modifier.size(40.dp).clickable { pc.seekToNext() }, tint = Color.White)
                Icon(painterResource(R.drawable.fast_forward), null, Modifier.size(38.dp).clickable { pc.player.seekTo(pc.player.currentPosition + 10000) }, tint = Color.White)
            }
            Spacer(Modifier.height(26.dp))
            Text("Volume Booster", style = ts(21, FontWeight.Medium))
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                @Composable fun Fx(label: String, icon: Int, on: Boolean, mod: Modifier, tap: () -> Unit) {
                    Column(mod.width(130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(68.dp).clip(CircleShape).background(if (on) W.Pink else Color(0x44B0124A)).clickable(onClick = tap), contentAlignment = Alignment.Center) {
                            Icon(painterResource(icon), null, Modifier.size(30.dp), tint = if (on) Color.White else W.Pink) }
                        Spacer(Modifier.height(10.dp))
                        Text(label, style = ts(17, color = Color(0xFFEFE8F2)), textAlign = TextAlign.Center)
                    }
                }
                Fx("Musical\nFlashlight", R.drawable.wv_flash, flash, Modifier.align(Alignment.TopStart).padding(start = 8.dp)) { flash = !flash; WavyFx.refresh() }
                Fx("Vibrate", R.drawable.wv_vibrate, vib, Modifier.align(Alignment.TopCenter).padding(top = 48.dp)) { vib = !vib; WavyFx.refresh() }
                Fx("Color\nStrobe", R.drawable.wv_strobe, strobe, Modifier.align(Alignment.TopEnd).padding(end = 8.dp)) { strobe = !strobe; WavyFx.refresh() }
            }
        }
    }
}
