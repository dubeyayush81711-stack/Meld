package com.metrolist.music.wavy

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import kotlinx.coroutines.delay

/** Apple Music style thin bar with a small dot. Used for song progress and for volume. Tap to jump, drag to scrub. onDrag(null) means the finger lifted. */
@Composable
fun AppleBar(frac: Float, onSeekTo: (Float) -> Unit, onDrag: (Float?) -> Unit, modifier: Modifier = Modifier, color: Color = Color.White) {
    var dragging by remember { mutableStateOf(false) }
    var last by remember { mutableFloatStateOf(0f) }
    Box(
        modifier.fillMaxWidth().height(28.dp)
            .pointerInput(Unit) { detectTapGestures { o -> onSeekTo((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; last = (o.x / size.width).coerceIn(0f, 1f); onDrag(last) },
                    onHorizontalDrag = { c, _ -> c.consume(); last = (c.position.x / size.width).coerceIn(0f, 1f); onDrag(last) },
                    onDragEnd = { dragging = false; onSeekTo(last); onDrag(null) },
                    onDragCancel = { dragging = false; onDrag(null) }
                )
            }
    ) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val h = (if (dragging) 6.dp else 4.dp).toPx()
            val y = (size.height - h) / 2f
            drawRoundRect(color.copy(alpha = 0.3f), Offset(0f, y), Size(size.width, h), CornerRadius(h / 2f))
            val w = size.width * frac.coerceIn(0f, 1f)
            drawRoundRect(color.copy(alpha = 0.9f), Offset(0f, y), Size(w, h), CornerRadius(h / 2f))
            drawCircle(color, (if (dragging) 8.dp else 5.dp).toPx(), Offset(w, size.height / 2f))
        }
    }
}

/** Real system media volume (the phone's music stream), with speaker icons like Apple Music. */
@Composable
fun VolumeRow(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var v by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max) }
    var touching by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val c = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
            if (!touching && kotlin.math.abs(c - v) > 0.01f) v = c
        }
    }
    fun set(f: Float) { v = f; am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(f * max), 0) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(R.drawable.volume_mute), null, Modifier.size(20.dp), tint = Color(0xB3FFFFFF))
        AppleBar(v, onSeekTo = { f -> set(f) }, onDrag = { f -> if (f == null) touching = false else { touching = true; set(f) } }, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.volume_up), null, Modifier.size(20.dp), tint = Color(0xB3FFFFFF))
    }
}
