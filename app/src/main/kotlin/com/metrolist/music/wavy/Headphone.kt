package com.metrolist.music.wavy

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer

/** Crisp vector headphone (drawn, not an image, so it is sharp at any size). It rises from behind the text with a spring, then floats. */
@Composable
fun HeadphoneHero(modifier: Modifier = Modifier) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 60f)) }
    val inf = rememberInfiniteTransition(label = "hp")
    val fl by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(2800, easing = EaseInOutSine), RepeatMode.Reverse), label = "fl")
    val glow by inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse), label = "gl")
    Box(modifier.graphicsLayer {
        val k = p.value
        translationY = (1f - k) * size.height * 0.55f + fl * 8.dp.toPx()
        val sc = 0.8f + 0.2f * k; scaleX = sc; scaleY = sc
        alpha = (k * 1.6f).coerceIn(0f, 1f)
    }) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            // soft glow behind
            drawCircle(Brush.radialGradient(listOf(Color(0x99C2288A).copy(alpha = 0.6f * glow), Color.Transparent), center = Offset(w * 0.5f, h * 0.6f), radius = w * 0.5f), w * 0.5f, Offset(w * 0.5f, h * 0.6f))
            // headband
            val bandBrush = Brush.linearGradient(listOf(Color(0xFF4F7CF0), Color(0xFF7B4AD8), Color(0xFFD0549C)), Offset(0f, 0f), Offset(w, h * 0.5f))
            val bw = w * 0.07f
            val r = w * 0.36f
            drawArc(bandBrush, 190f, 160f, false, Offset(w * 0.5f - r, h * 0.58f - r), Size(2 * r, 2 * r), style = Stroke(bw, cap = StrokeCap.Round))
            drawArc(Color(0x55FFFFFF), 205f, 70f, false, Offset(w * 0.5f - r, h * 0.58f - r), Size(2 * r, 2 * r), style = Stroke(bw * 0.18f, cap = StrokeCap.Round))
            // ear cups
            fun cup(cx: Float, flip: Float) {
                val cw = w * 0.25f; val ch = h * 0.40f
                val tl = Offset(cx - cw / 2, h * 0.58f - ch * 0.1f)
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2B3F86), Color(0xFF1B1F4A)), tl.y, tl.y + ch), tl, Size(cw, ch), CornerRadius(cw * 0.45f))
                // pad (cushion)
                val inner = Offset(tl.x + cw * (if (flip < 0) 0.18f else 0.06f), tl.y + ch * 0.1f)
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFF9B3FE0), Color(0xFFFF4F9A)), inner, Offset(inner.x + cw * 0.8f, inner.y + ch * 0.8f)), inner, Size(cw * 0.76f, ch * 0.8f), CornerRadius(cw * 0.36f))
                drawRoundRect(Color(0x44FFFFFF), Offset(inner.x + cw * 0.1f, inner.y + ch * 0.1f), Size(cw * 0.16f, ch * 0.5f), CornerRadius(cw * 0.08f))
                drawRoundRect(Color(0x66FFFFFF), tl, Size(cw, ch), CornerRadius(cw * 0.45f), style = Stroke(w * 0.006f))
            }
            cup(w * 0.17f, -1f); cup(w * 0.83f, 1f)
        }
    }
}
