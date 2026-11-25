package com.metrolist.music.wavy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import com.metrolist.music.R

object W {
    val Pink = Color(0xFFFF2D78)
    val Pink2 = Color(0xFFF53D8C)
    val Violet = Color(0xFF8E3BEA)
    val Bg = Color(0xFF0B0B0D)
    val Bg2 = Color(0xFF0D0A12)
    val Card = Color(0xFF1B1920)
    val Maroon = Color(0xFF3B0C20)
    val Dim = Color(0xFFA9A3B3)
    val Grad = Brush.linearGradient(listOf(Color(0xFFFF3D8B), Color(0xFF8E3BEA)))
}

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

fun ts(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Color.White) =
    TextStyle(fontFamily = Poppins, fontSize = size.sp, fontWeight = weight, color = color, lineHeight = (size * 1.3f).sp)

/** Dark backdrop with soft magenta/violet glow and diagonal streaks, like the mockups. */
@Composable
fun Backdrop(modifier: Modifier = Modifier.fillMaxSize(), streaks: Boolean = true, tint: Color = Color(0xFF3B0C3A), content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(W.Bg)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), Color.Transparent), endY = size.height * 0.55f))
            drawRect(Brush.radialGradient(listOf(Color(0x55801080), Color.Transparent), center = Offset(size.width, size.height * 0.95f), radius = size.width * 0.9f))
            if (streaks) {
                val n = 14
                for (i in 0 until n) {
                    val x = size.width * (i / n.toFloat()) * 1.6f - size.width * 0.2f
                    val a = if (i % 3 == 0) 0.10f else 0.05f
                    drawLine(
                        Color(0xFFFF2D78).copy(alpha = a), Offset(x, 0f), Offset(x - size.width * 0.5f, size.height),
                        strokeWidth = size.width * (0.012f + (i % 4) * 0.01f), cap = StrokeCap.Round,
                    )
                }
            }
        }
        content()
    }
}
