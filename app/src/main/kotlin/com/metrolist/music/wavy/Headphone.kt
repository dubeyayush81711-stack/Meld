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
import androidx.compose.ui.unit.dp

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
            drawCircle(Brush.radialGradient(listOf(Color(0xFFC2288A).copy(alpha = 0.55f * glow), Color(0xFF5B2AD8).copy(alpha = 0.18f * glow), Color.Transparent), center = Offset(w * 0.55f, h * 0.55f), radius = w * 0.62f), w * 0.62f, Offset(w * 0.55f, h * 0.55f))
        }
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.metrolist.music.R.drawable.headphone_ref), null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
    }
}
