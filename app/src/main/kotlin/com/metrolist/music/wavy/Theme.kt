package com.metrolist.music.wavy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import com.metrolist.music.R

const val WAVY_BUILD = "dev-79"

object W {
    val Pink = Color(0xFFFF2D78)
    val Pink2 = Color(0xFFF53D8C)
    val Violet = Color(0xFF8E3BEA)
    val Bg = Color(0xFF0B141A)
    val Bg2 = Color(0xFF0D0A12)
    val Card = Color(0xFF1B1920)
    val Maroon = Color(0xFF3B0C20)
    val Dim = Color(0xFFA9A3B3)
    val Grad = Brush.linearGradient(listOf(Color(0xFFFF3D8B), Color(0xFF8E3BEA)))
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Figtree = FontFamily(
    *listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
        Font(R.font.figtree, w, variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(w.weight)))
    }.toTypedArray()
)

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

fun ts(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Color.White) =
    TextStyle(fontFamily = Figtree, fontSize = (size * 0.92f).sp, fontWeight = weight, color = color, lineHeight = (size * 0.92f * 1.25f).sp)

/** Liquid-glass surface (from Ayush's glass design): translucent gradient, light rim, soft shadow. Central place to restyle. */
fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape, strength: Float = 1f): Modifier = this
    .clip(shape)
    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.13f * strength), Color.White.copy(alpha = 0.08f * strength), Color.White.copy(alpha = 0.10f * strength))))
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.12f))), shape)

val GlassSheet = Color(0xE6111B21)
val GlassBar = Color(0x99111B21)

/** Midnight canvas with three drifting liquid orbs and a diagonal sheen, same as the glass design background. */
@Composable
fun Backdrop(modifier: Modifier = Modifier.fillMaxSize(), streaks: Boolean = true, tint: Color = Color(0xFF3B0C3A), content: @Composable BoxScope.() -> Unit) {
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "orbs")
    val p1 by (if (Reduce.on) androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0.35f) } else tr.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(14000, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "o1"))
    val p2 by (if (Reduce.on) androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0.5f) } else tr.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(19000, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "o2"))
    val p3 by (if (Reduce.on) androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0.65f) } else tr.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(23000, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "o3"))
    Box(modifier.background(W.Bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = minOf(size.height, w * 2.2f)
            fun orb(c: Color, cx: Float, cy: Float, r: Float) = drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0.34f * c.alpha), Color.Transparent), center = Offset(cx, cy), radius = r), r, Offset(cx, cy))
            orb(Color(0xFFFF2E54).copy(alpha = 0.50f), w * (0.15f + 0.55f * p1), h * (0.12f + 0.25f * p2), w * 0.62f)
            orb(Color(0xFF10B981).copy(alpha = 0.36f), w * (0.85f - 0.55f * p2), h * (0.45f + 0.2f * p3), w * 0.66f)
            orb(Color(0xFFFBBF24).copy(alpha = 0.34f), w * (0.2f + 0.6f * p3), h * (0.78f - 0.2f * p1), w * 0.60f)
            drawRect(Brush.linearGradient(0.16f to Color.Transparent, 0.33f to Color(0x0EFFFFFF), 0.48f to Color(0x04FFFFFF), 0.66f to Color(0x0BFFFFFF), 0.84f to Color.Transparent, start = Offset(0f, 0f), end = Offset(w, size.height)))
        }
        content()
    }
}

/** Highest-resolution square art: Google-hosted covers accept a size suffix, so ask for a large square. */
fun hiRes(u: String?): String? {
    if (u == null) return null
    return if ((u.contains("googleusercontent.com") || u.contains("ggpht.com")) && u.contains("=")) u.substringBeforeLast("=") + "=w1024-h1024-l90-rj" else u
}
