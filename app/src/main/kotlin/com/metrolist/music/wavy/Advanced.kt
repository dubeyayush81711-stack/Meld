package com.metrolist.music.wavy

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import kotlinx.coroutines.delay
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
    /** Volume boost is software gain (WavySpatial.gain, 100 to 200%) applied in the audio chain with a soft limiter. */
    fun apply(pc: PlayerConnection, v: Float) { val cap = if (try { WavyFx.headphones() } catch (e: Exception) { false }) 0.5f else 1f; WavySpatial.gain = 1f + v.coerceIn(0f, cap) }
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
            // curved song progress: drag to seek
            var posMs by remember { mutableStateOf(0L) }
            var durMs by remember { mutableStateOf(0L) }
            var dragging by remember { mutableStateOf(false) }
            var dragFrac by remember { mutableStateOf(0f) }
            LaunchedEffect(m?.id) { while (true) { if (!dragging) { posMs = pc.player.currentPosition.coerceAtLeast(0); durMs = pc.player.duration.coerceAtLeast(0) }; delay(300) } }
            val frac = if (dragging) dragFrac else if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
            fun fmt(ms: Long): String { val s = (ms / 1000).toInt(); return "%d:%02d".format(s / 60, s % 60) }
            Box(Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 24.dp).pointerInput(durMs) {
                detectDragGestures(
                    onDragStart = { o -> dragging = true; dragFrac = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { if (durMs > 0) pc.player.seekTo((dragFrac * durMs).toLong()); dragging = false },
                    onDragCancel = { dragging = false }
                ) { c, _ -> dragFrac = (c.position.x / size.width).coerceIn(0f, 1f) }
            }) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    val steps = 60
                    fun ay(t: Float): Float { val d = (t - 0.5f) * 2f; return 6f + (h - 24f) * (1f - d * d) * 0f + (h - 24f) * (1f - (1f - d * d)) * 0f + (h - 24f) * (d * d - 1f) * -1f - (h - 24f) * 0f }
                    fun curveY(t: Float): Float { val d = (t - 0.5f) * 2f; return 6f + (h - 30f) * (1f - d * d) }
                    val arc = Path()
                    for (i in 0..steps) { val t = i / steps.toFloat(); val y = h - 18f - curveY(t) + 6f; if (i == 0) arc.moveTo(t * w, y) else arc.lineTo(t * w, y) }
                    drawPath(arc, Color(0x33FFFFFF), style = Stroke(5f))
                    val done = Path()
                    val upto = (frac * steps).toInt()
                    for (i in 0..upto) { val t = i / steps.toFloat(); val y = h - 18f - curveY(t) + 6f; if (i == 0) done.moveTo(t * w, y) else done.lineTo(t * w, y) }
                    drawPath(done, W.Pink, style = Stroke(6f))
                    drawCircle(Color(0x44FFFFFF), 13.dp.toPx(), Offset(frac * w, h - 18f - curveY(frac) + 6f))
                    drawCircle(Color.White, 7.dp.toPx(), Offset(frac * w, h - 18f - curveY(frac) + 6f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp).offset(y = (-6).dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt((frac * durMs).toLong()), style = ts(14, color = Color(0xFFE6DDE9))); Text("-" + fmt((durMs - (frac * durMs).toLong()).coerceAtLeast(0)), style = ts(14, color = Color(0xFFE6DDE9)))
            }
            Row(Modifier.fillMaxWidth().offset(y = (-6).dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.replay), null, Modifier.size(38.dp).clickable { pc.player.seekTo((pc.player.currentPosition - 10000).coerceAtLeast(0)) }, tint = Color.White)
                Icon(painterResource(R.drawable.skip_previous), null, Modifier.size(40.dp).clickable { pc.seekToPrevious() }, tint = Color.White)
                Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0x33FFFFFF)).border(1.dp, Color(0x55FFFFFF), CircleShape).clickable { pc.player.let { if (it.isPlaying) it.pause() else it.play() } }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), null, Modifier.size(36.dp), tint = Color.White) }
                Icon(painterResource(R.drawable.skip_next), null, Modifier.size(40.dp).clickable { pc.seekToNext() }, tint = Color.White)
                Icon(painterResource(R.drawable.fast_forward), null, Modifier.size(38.dp).clickable { pc.player.seekTo(pc.player.currentPosition + 10000) }, tint = Color.White)
            }
            Spacer(Modifier.height(26.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(22.dp)).background(Color(0x1AFFFFFF)).border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(22.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Volume boost", Modifier.weight(1f), style = ts(17, FontWeight.Medium))
                    Text("${100 + (boost * 100).toInt()}%", style = ts(17, FontWeight.SemiBold, W.Pink))
                }
                Slider(value = boost, onValueChange = { boost = it }, modifier = Modifier.padding(top = 4.dp), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = W.Pink, inactiveTrackColor = Color(0x33FFFFFF)))
                Text("Above 100% the sound is amplified in software, a soft limiter stops clipping. With earphones connected the boost stops at 150% to protect your ears. Keep the volume low when you first raise it.", style = ts(12, color = Color(0xFFB9B2C4)))
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                @Composable fun Fx(label: String, icon: Int, on: Boolean, mod: Modifier, tap: () -> Unit) {
                    Column(mod.width(130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(68.dp).clip(CircleShape).background(if (on) W.Pink.copy(alpha = 0.45f) else Color(0x1AFFFFFF)).border(1.dp, if (on) W.Pink else Color(0x33FFFFFF), CircleShape).clickable(onClick = tap), contentAlignment = Alignment.Center) {
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
