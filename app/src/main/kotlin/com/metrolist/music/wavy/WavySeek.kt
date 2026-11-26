package com.metrolist.music.wavy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Wavy seek bar: the played part is an animated sine wave (flat while paused or dragging), the rest is a thin flat track. */
@Composable
fun WavySeek(fraction: Float, playing: Boolean, color: Color, onSeekTo: (Float) -> Unit, onDrag: (Float?) -> Unit = {}, modifier: Modifier = Modifier) {
    var w by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    var df by remember { mutableFloatStateOf(0f) }
    val moving = playing && !Reduce.on
    val phase by (if (moving) rememberInfiniteTransition(label = "w").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "p") else remember { mutableStateOf(0f) })
    val amp by animateFloatAsState(if (moving && !dragging) 1f else 0f, tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "a")
    val thumb by animateFloatAsState(if (dragging) 11f else 8f, tween(150), label = "t")
    val sm by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(450, easing = LinearEasing), label = "f")
    val f = if (dragging) df else sm
    Canvas(modifier.fillMaxWidth().height(32.dp)
        .pointerInput(Unit) { detectTapGestures { onSeekTo((it.x / size.width).coerceIn(0f, 1f)) } }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { dragging = true; df = (it.x / size.width).coerceIn(0f, 1f); onDrag(df) },
                onDragEnd = { dragging = false; onSeekTo(df); onDrag(null) },
                onDragCancel = { dragging = false; onDrag(null) }) { c, _ -> df = (c.position.x / size.width).coerceIn(0f, 1f); onDrag(df) }
        }) {
        val cy = size.height / 2; val x = size.width * f
        val sw = 4.dp.toPx()
        drawLine(color.copy(alpha = 0.3f), Offset(x + 6.dp.toPx(), cy), Offset(size.width, cy), sw, StrokeCap.Round)
        val p = Path(); val a = 4.dp.toPx() * amp; val wl = 28.dp.toPx()
        p.moveTo(0f, cy)
        var i = 0f
        val ramp = 14.dp.toPx()
        while (i <= x) { val e = (i / ramp).coerceAtMost(1f); p.lineTo(i, cy + a * e * sin(i / wl * 2 * PI.toFloat() - phase)); i += 2f }
        p.lineTo(x, cy + a * (x / ramp).coerceAtMost(1f) * sin(x / wl * 2 * PI.toFloat() - phase))
        drawPath(p, color, style = Stroke(sw, cap = StrokeCap.Round))
        drawCircle(color, thumb.dp.toPx(), Offset(x, cy + a * (x / ramp).coerceAtMost(1f) * sin(x / wl * 2 * PI.toFloat() - phase)))
    }
}
