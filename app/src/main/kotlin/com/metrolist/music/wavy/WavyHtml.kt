package com.metrolist.music.wavy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos

/* Native (Compose) conversion of Ayush's welcome.html and login.html. All sizes are in "cqw" (1/100 of the screen-box width), exactly like his CSS. */

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r / 255f, g / 255f, b / 255f, a)
private val EaseC = CubicBezierEasing(.25f, .1f, .25f, 1f)
private val EaseIO = CubicBezierEasing(.42f, 0f, .58f, 1f)
private val EaseOut = CubicBezierEasing(0f, 0f, .58f, 1f)
private val UpE = CubicBezierEasing(.2f, .8f, .2f, 1f)
private val PopE = CubicBezierEasing(.2f, 1.4f, .4f, 1f)
private val PINK = Color(0xFFE5115F)
private val PoppinsBI = FontFamily(Font(R.font.poppins_bold_italic, FontWeight.Bold, FontStyle.Italic))

private fun prog(t: Float, delay: Float, dur: Float, e: Easing = EaseC): Float = if (t < delay) 0f else e.transform(((t - delay) / dur).coerceIn(0f, 1f))
private fun ping(t: Float, delay: Float, cycle: Float, e: Easing = EaseIO): Float {
    if (t < delay) return 0f
    val x = ((t - delay) % cycle) / cycle
    return if (x < .5f) e.transform(x * 2f) else e.transform((1f - x) * 2f)
}
private fun lin(t: Float, delay: Float, cycle: Float): Float = if (t < delay) -1f else ((t - delay) % cycle) / cycle

class Cq(val u: Float, val d: Density, val w: Float, val h: Float) {
    fun dp(x: Float): Dp = with(d) { (x * u).toDp() }
    fun sp(x: Float): TextUnit = with(d) { (x * u).toSp() }
    fun spPx(px: Float): TextUnit = with(d) { px.toSp() }
}

private fun Modifier.at(c: Cq, x: Float, y: Float) = offset { IntOffset((x * c.u).roundToInt(), (y * c.u).roundToInt()) }
private fun Modifier.sz(c: Cq, w: Float, h: Float) = requiredSize(c.dp(w), c.dp(h))
private fun Modifier.upIn(c: Cq, tt: () -> Float, delay: Float) = graphicsLayer {
    val p = prog(tt(), delay, .8f, UpE); alpha = p; translationY = (1f - p) * 5f * c.u
}

private fun cssLinear(angle: Float, w: Float, h: Float, ox: Float, oy: Float, vararg stops: Pair<Float, Color>): Brush {
    val a = Math.toRadians(angle.toDouble())
    val dx = sin(a).toFloat(); val dy = -cos(a).toFloat()
    val len = abs(w * dx) + abs(h * dy)
    val cx = ox + w / 2f; val cy = oy + h / 2f
    return Brush.linearGradient(colorStops = stops, start = Offset(cx - dx * len / 2f, cy - dy * len / 2f), end = Offset(cx + dx * len / 2f, cy + dy * len / 2f))
}
private fun DrawScope.vGrad(vararg stops: Pair<Float, Color>) = Brush.verticalGradient(colorStops = stops, startY = 0f, endY = size.height)

/** Soft outer shadow approximated with stacked rounded rects. */
private fun DrawScope.softShadow(r: Rect, rad: Float, blur: Float, color: Color, dy: Float = 0f) {
    val n = 7
    for (i in 0 until n) {
        val f = (i + 1f) / n
        val g = blur * (1f - f * .9f)
        drawRoundRect(color.copy(alpha = color.alpha * .22f * (1f / (n * .55f)) * 3f / n * (n - i) / n * 2f), Offset(r.left - g, r.top - g + dy), Size(r.width + 2 * g, r.height + 2 * g), CornerRadius(rad + g))
    }
}
private fun DrawScope.ellipseGlow(cx: Float, cy: Float, rx: Float, ry: Float, vararg stops: Pair<Float, Color>) {
    withTransform({ scale(1f, ry / rx, Offset(cx, cy)) }) {
        drawCircle(Brush.radialGradient(colorStops = stops, center = Offset(cx, cy), radius = rx), rx, Offset(cx, cy))
    }
}
private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

@Composable
private fun CqStage(ratio: Float, content: @Composable BoxScope.(Cq, () -> Float) -> Unit) {
    val d = LocalDensity.current
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val t0 = withFrameMillis { it }
        while (true) withFrameMillis { clock.longValue = it - t0 }
    }
    val hold = remember { floatArrayOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val wPx = constraints.maxWidth.toFloat()
        hold[0] = max(hold[0], constraints.maxHeight.toFloat())
        val hPx = hold[0]
        val aw = min(wPx, hPx * ratio); val ah = aw / ratio
        val c = Cq(aw / 100f, d, aw, ah)
        Box(Modifier.offset { IntOffset(((wPx - aw) / 2f).toInt(), ((hPx - ah) / 2f).toInt()) }
            .requiredSize(with(d) { aw.toDp() }, with(d) { ah.toDp() }).clipToBounds().background(Color(0xFF06000D))) {
            content(c) { clock.longValue / 1000f }
        }
    }
}

@Composable
private fun fitPx(c: Cq, text: String, target: Float, family: FontFamily, weight: FontWeight, style: FontStyle = FontStyle.Normal, ls: Float = 0f): Float {
    val tm = rememberTextMeasurer()
    return remember(c.u, text) {
        val r = tm.measure(text, TextStyle(fontFamily = family, fontWeight = weight, fontStyle = style, fontSize = c.spPx(100f), letterSpacing = c.sp(ls)), softWrap = false, maxLines = 1)
        100f * target * c.u / r.size.width.toFloat()
    }
}

@Composable
private fun BgImage(c: Cq, tt: () -> Float) {
    Image(ImageBitmap.imageResource(R.drawable.wv_bg), null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier.at(c, -3f, -3f).sz(c, 106f, 106f * c.h / c.w).graphicsLayer {
            val p = ping(tt(), 0f, 32f); val s = 1f + .06f * p
            scaleX = s; scaleY = s; translationX = -1f * c.u * p; translationY = 1f * c.u * p
        })
}

private class Pt(var x: Float, var y: Float, var r: Float, var v: Float, var ph: Float, var c: Int, var a: Float)

@Composable
private fun Particles(c: Cq, tt: () -> Float) {
    val w = c.w; val h = c.h; val s = w / 390f
    val rnd = remember { kotlin.random.Random(System.nanoTime()) }
    fun reset(p: Pt, init: Boolean) {
        p.x = rnd.nextFloat() * w; p.y = if (init) rnd.nextFloat() * h else h + 10f * s
        p.r = (rnd.nextFloat() * 1.6f + .6f) * s; p.v = (rnd.nextFloat() * .35f + .12f) * s
        p.ph = rnd.nextFloat() * 6.28f; p.c = if (rnd.nextFloat() < .6f) 0 else 1; p.a = rnd.nextFloat() * .5f + .35f
    }
    val pts = remember(w) { List(42) { Pt(0f, 0f, 0f, 0f, 0f, 0, 0f).also { reset(it, true) } } }
    val last = remember { floatArrayOf(0f) }
    val c0 = rgba(255, 70, 150, 1f); val c1 = rgba(170, 120, 255, 1f)
    Canvas(Modifier.fillMaxSize()) {
        val t = tt() * 1000f
        val dt = (t - last[0]).coerceIn(0f, 50f) / 16.667f; last[0] = t
        for (p in pts) {
            p.y -= p.v * dt; p.x += sin(t / 1500f + p.ph) * .25f * s * dt
            if (p.y < -10f * s) reset(p, false)
            val k = .6f + .4f * sin(t / 500f + p.ph)
            val col = if (p.c == 0) c0 else c1
            val ctr = Offset(p.x, p.y)
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = p.a * k), col.copy(alpha = 0f)), center = ctr, radius = p.r * 4f), p.r * 4f, ctr)
        }
    }
}

private fun DrawScope.streak(c: Cq, t: Float, top: Float, delay: Float, dur: Float, hgt: Float, a1: Float, a2: Float, ca: Float) {
    val q = lin(t, delay, dur); if (q < 0f) return
    val al = when { q < .15f -> a1 * q / .15f; q < .8f -> a1 + (a2 - a1) * (q - .15f) / .65f; else -> a2 * (1f - (q - .8f) / .2f) }
    val u = c.u
    val x0 = -60f * u; val y0 = top * u; val w = 60f * u; val hh = hgt * u
    withTransform({
        translate(200f * u * q, -115f * u * q)
        rotate(-30f, Offset(x0 + w / 2f, y0 + hh / 2f))
    }) {
        val br = Brush.horizontalGradient(listOf(Color.Transparent, rgba(255, 70, 160, ca * al), Color.Transparent), x0, x0 + w)
        drawRoundRect(br, Offset(x0, y0 - hh * .6f), Size(w, hh * 2.2f), CornerRadius(hh * 1.1f), alpha = .35f)
        drawRoundRect(br, Offset(x0, y0), Size(w, hh), CornerRadius(hh))
    }
}

/* ============================== WELCOME ============================== */
@Composable
fun WelcomeUi(onStart: () -> Unit) {
    val scope = rememberCoroutineScope()
    val go by rememberUpdatedState(onStart)
    CqStage(1089f / 2307f) { c, tt ->
        val u = c.u
        BgImage(c, tt)
        // bgglow
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = ping(tt(), 0f, 5f) }) {
            ellipseGlow(size.width * .8f, size.height * .72f, size.width * .6f, size.height * .18f, 0f to rgba(200, 25, 110, .35f), 1f to Color.Transparent)
            ellipseGlow(size.width * .15f, size.height * .90f, size.width * .5f, size.height * .14f, 0f to rgba(140, 20, 150, .35f), 1f to Color.Transparent)
        }
        // fx: rings + streaks
        Canvas(Modifier.fillMaxSize()) {
            val t = tt()
            for (d in floatArrayOf(0f, 1.7f, 3.4f)) {
                val q = lin(t, d, 5f); if (q < 0f) continue
                val sc = .7f + 1.8f * EaseOut.transform(q)
                val al = if (q < .15f) .6f * EaseOut.transform(q / .15f) else .6f * (1f - EaseOut.transform((q - .15f) / .85f))
                val ctr = Offset(49.6f * u, 76f * u); val rad = 20f * u * sc
                drawCircle(rgba(255, 60, 150, .35f * al), rad, ctr, style = Stroke(3f * u))
                drawCircle(rgba(255, 80, 170, al), rad, ctr, style = Stroke(.3f * u))
                drawCircle(rgba(255, 60, 150, .25f * al), rad - 1.5f * u, ctr, style = Stroke(3f * u))
            }
            streak(c, t, 150f, 0f, 7f, 2f, .85f, .5f, .6f)
            streak(c, t, 110f, 2.4f, 9f, 1.4f, .85f, .5f, .6f)
            streak(c, t, 185f, 4.6f, 8f, 2f, .85f, .5f, .6f)
        }
        Particles(c, tt)
        // logo glow
        Canvas(Modifier.at(c, 11f, 18.5f).sz(c, 33f, 14f).graphicsLayer { alpha = ping(tt(), 1f, 3.4f) }) {
            ellipseGlow(size.width / 2f, size.height / 2f, size.width / 2f, size.height / 2f, 0f to rgba(255, 60, 130, .5f), 1f to Color.Transparent)
        }
        // logo
        Row(Modifier.at(c, 14.6f, 18.2f).graphicsLayer { alpha = prog(tt(), .15f, .9f, EaseOut) }, verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.sz(c, 9.4f, 9.4f)) {
                val t = tt(); val r = 2.8f * u
                drawCircle(Brush.radialGradient(listOf(rgba(255, 50, 130, .45f), Color.Transparent), center, 9.7f * u), 9.7f * u, center)
                val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
                clipPath(shape) {
                    drawRect(cssLinear(145f, size.width, size.height, 0f, 0f, 0f to Color(0xFFFF5A9B), .62f to Color(0xFFC10E66), 1f to Color(0xFF6A0A7A)))
                    drawRoundRect(vGrad(0f to Color.White.copy(alpha = .5f), 1f to Color.White.copy(alpha = .04f)).let { Brush.verticalGradient(listOf(Color.White.copy(alpha = .5f), Color.White.copy(alpha = .04f)), 0f, size.height * .52f) },
                        Offset.Zero, Size(size.width, size.height * .52f), CornerRadius(r))
                    val delays = floatArrayOf(0f, -.35f, -.7f, -.2f)
                    val bw = .95f * u; val gap = .75f * u; val tot = 4 * bw + 3 * gap
                    for (i in 0 until 4) {
                        val hh = (2f + 3.4f * ping(t, delays[i], 1.1f)) * u
                        val x = (size.width - tot) / 2f + i * (bw + gap)
                        drawRoundRect(Color.White, Offset(x, (size.height - hh) / 2f), Size(bw, hh), CornerRadius(bw / 2f))
                    }
                }
                drawRoundRect(Color.White.copy(alpha = .25f), Offset.Zero, size, CornerRadius(r), style = Stroke(.25f * u))
            }
            Spacer(Modifier.width(c.dp(2.4f)))
            Row {
                val cols = listOf("W" to (Color(0xFFFFF1F6) to Color(0xFFFFC4D9)), "a" to (Color(0xFFFFE0EC) to Color(0xFFFFA3C3)),
                    "v" to (Color(0xFFFFC2D8) to Color(0xFFFF7FAE)), "y" to (Color(0xFFFF9CC0) to Color(0xFFFF2F7D)))
                cols.forEachIndexed { i, (ch, g) ->
                    Text(ch, Modifier.padding(end = c.dp(.5f)).graphicsLayer { translationY = -1f * u * ping(tt(), i * .15f, 2.4f) },
                        style = TextStyle(brush = Brush.verticalGradient(listOf(g.first, g.second)), fontFamily = PoppinsBI, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic,
                            fontSize = c.sp(9.2f), lineHeight = c.sp(11.5f)), softWrap = false)
                }
            }
        }
        // stars
        val star = remember { path("M50 0C54 30 70 46 100 50 70 54 54 70 50 100 46 70 30 54 0 50 30 46 46 30 50 0Z") }
        data class St(val x: Float, val y: Float, val s: Float, val d1: Float, val d2: Float)
        for (st in listOf(St(15f, 29.7f, 9.6f, .6f, 1.3f), St(14f, 37f, 4.1f, .8f, 2.1f), St(79.5f, 114.4f, 6f, 1.3f, .4f))) {
            Canvas(Modifier.at(c, st.x, st.y).sz(c, st.s, st.s).graphicsLayer {
                val t = tt(); val sp = prog(t, st.d1, .7f, EaseOut)
                val tw = if (t >= st.d2) ping(t, st.d2, 3f) else -1f
                alpha = if (tw >= 0f) 1f - .55f * tw else sp
                val s1 = if (t < st.d1) 1f else .2f + .8f * sp
                val s2 = s1 * (if (tw >= 0f) 1f - .2f * tw else 1f)
                scaleX = s2; scaleY = s2; rotationZ = if (t < st.d1) 0f else -60f * (1f - sp)
            }) {
                drawCircle(rgba(190, 170, 255, .22f), size.width * .8f, center)
                scale(size.width / 100f, Offset.Zero) { drawPath(star, Brush.linearGradient(listOf(Color(0xFFF4EEFF), Color(0xFFA99BE0)), Offset.Zero, Offset(100f, 100f))) }
            }
        }
        // halo
        Canvas(Modifier.at(c, 14f, 44f).sz(c, 72f, 72f).graphicsLayer { val t = tt(); alpha = if (t < 1.6f) prog(t, .4f, 1.2f) else ping(t, 1.6f, 3.4f) }) {
            val r = size.width / 2f
            drawCircle(Brush.radialGradient(0f to rgba(200, 40, 170, .28f), .6f to rgba(90, 30, 200, .12f), 1f to Color.Transparent, center = center, radius = r), r, center)
        }
        // shadow
        Canvas(Modifier.at(c, 24f, 117f).sz(c, 52f, 9f).graphicsLayer {
            val t = tt()
            if (t < 1.5f) { alpha = prog(t, .9f, 1f) } else { val f = ping(t, 1.5f, 5f); alpha = .95f - .3f * f; scaleX = 1f - .16f * f; scaleY = 1f - .16f * f }
        }) {
            ellipseGlow(size.width / 2f, size.height / 2f, size.width / 2f, size.height / 2f, 0f to rgba(0, 0, 0, .92f), .6f to rgba(60, 0, 50, .55f), 1f to Color.Transparent)
        }
        // headphones
        Box(Modifier.at(c, 8.4f, 28.9f).sz(c, 82.3f, 82.3f * 920f / 800f).graphicsLayer {
            val t = tt()
            if (t < 1.5f) { val p = prog(t, .3f, 1.2f, UpE); alpha = p; translationY = (1f - p) * 8f * u; scaleX = .88f + .12f * p; scaleY = .88f + .12f * p }
            else { val f = ping(t, 1.5f, 5f); translationY = -2.6f * u * f; rotationZ = .6f * f }
        }) {
            val mask: androidx.compose.ui.draw.CacheDrawScope.() -> androidx.compose.ui.draw.DrawResult = {
                onDrawWithContent {
                    drawContent()
                    val cx = size.width / 2f; val cy = size.height / 2f
                    withTransform({ scale(1f, size.height / size.width, Offset(cx, cy)) }) {
                        drawRect(Brush.radialGradient(0f to Color.Black, .86f to Color.Black, 1f to Color.Transparent, center = Offset(cx, cy), radius = size.width / 2f),
                            Offset(0f, cy - size.width / 2f), Size(size.width, size.width), blendMode = BlendMode.DstIn)
                    }
                }
            }
            Image(ImageBitmap.imageResource(R.drawable.wv_hp), null, contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache(mask))
            Image(ImageBitmap.imageResource(R.drawable.wv_hpglow), null, contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = ping(tt(), 0f, 3.4f) }.drawWithCache(mask))
        }
        // glass
        Box(Modifier.at(c, 6f, 128f).sz(c, 88f, 76f).graphicsLayer { alpha = prog(tt(), .8f, .9f) }.drawBehind {
            val t = tt(); val r = 9f * u; val rect = Rect(0f, 0f, size.width, size.height)
            softShadow(rect, r, 8f * u, Color.Black.copy(alpha = .4f), 4f * u)
            val shape = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(r))) }
            clipPath(shape) {
                drawRect(cssLinear(160f, size.width, size.height, 0f, 0f, 0f to Color.White.copy(alpha = .14f), .45f to Color.White.copy(alpha = .04f), 1f to rgba(255, 120, 200, .07f)))
                val x = if (t < 2f) 1f else ((t - 2f) % 7f) / 7f
                val pos = if (x < .6f) 160f - 220f * EaseIO.transform(x / .6f) else -60f
                val iw = size.width * 2.6f; val off = (size.width - iw) * pos / 100f
                drawRect(cssLinear(115f, iw, size.height, off, 0f, 0f to Color.Transparent, .35f to Color.Transparent, .5f to Color.White.copy(alpha = .16f), .65f to Color.Transparent, 1f to Color.Transparent))
                drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .4f), Color.Transparent), 0f, size.height * .12f), Offset.Zero, size, CornerRadius(r), style = Stroke(.7f * u))
            }
            drawRoundRect(Color.White.copy(alpha = .13f), Offset.Zero, size, CornerRadius(r), style = Stroke(.22f * u))
        })
        // headline + sub
        val h1 = fitPx(c, "Millions Of Songs", 64.1f, Poppins, FontWeight.SemiBold)
        Column(Modifier.at(c, 13.5f, 131.8f).wrapContentSize(Alignment.TopStart, unbounded = true).upIn(c, tt, 1f)) {
            val st = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = c.spPx(h1), lineHeight = c.sp(10.1f), color = Color.White)
            Text("Millions Of Songs", style = st, softWrap = false); Text("Waiting For You.", style = st, softWrap = false)
        }
        val sb = fitPx(c, "Discover New Artists, Trending Tracks,", 62.7f, Poppins, FontWeight.Normal)
        Column(Modifier.at(c, 13.3f, 156.2f).wrapContentSize(Alignment.TopStart, unbounded = true).upIn(c, tt, 1.2f)) {
            val st = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = c.spPx(sb), lineHeight = c.sp(6.2f), color = Color.White.copy(alpha = .8f))
            Text("Discover New Artists, Trending Tracks,", style = st, softWrap = false); Text("And Playlists Made For You.", style = st, softWrap = false)
        }
        // CTA
        val ct = fitPx(c, "Get Started", 19.6f, Poppins, FontWeight.SemiBold)
        var pressed by remember { mutableStateOf(false) }
        val press by animateFloatAsState(if (pressed) .95f else 1f, tween(150), label = "pr")
        var rip by remember { mutableStateOf<Triple<Float, Float, Float>?>(null) }
        Box(Modifier.at(c, 13.3f, 178.6f).sz(c, 48.2f, 12.9f).graphicsLayer {
            val p = prog(tt(), 1.5f, .7f, PopE); alpha = p.coerceIn(0f, 1f); translationY = (1f - p) * 6f * u
            val s = (.85f + .15f * p) * press; scaleX = s; scaleY = s
        }.drawBehind {
            val g = if (tt() > 2.4f) ping(tt(), 2.4f, 2.8f) else 0f
            softShadow(Rect(0f, 0f, size.width, size.height), 7f * u, (6f + 3f * g) * u, rgba(229, 17, 95, .3f + .3f * g), 2f * u)
        }.pointerInput(Unit) {
            detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { o ->
                rip = Triple(o.x, o.y, tt()); scope.launch { delay(250); go() }
            })
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val t = tt(); val r = 7f * u
                val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
                clipPath(shape) {
                    drawRect(vGrad(0f to rgba(255, 45, 125, .66f), 1f to rgba(200, 10, 95, .74f)))
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .34f), Color.Transparent), 0f, size.height * .52f))
                    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .6f), Color.Transparent), 0f, size.height * .14f), Offset.Zero, size, CornerRadius(r), style = Stroke(.7f * u))
                    // shine
                    if (t >= 2.6f) {
                        val x = ((t - 2.6f) % 3.6f) / 3.6f
                        val left = (if (x < .45f) -20f + 90f * EaseIO.transform(x / .45f) else 70f) * u
                        val bw = 14f * u; val sk = size.height / 2f * .364f
                        val p = Path().apply { moveTo(left + sk, 0f); lineTo(left + bw + sk, 0f); lineTo(left + bw - sk, size.height); lineTo(left - sk, size.height); close() }
                        drawPath(p, Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .35f), Color.Transparent), left - sk, left + bw + sk))
                    }
                    rip?.let { (rx, ry, st) ->
                        val q = ((t - st) / .6f).coerceIn(0f, 1f)
                        val dd = max(size.width, size.height) / 3f
                        if (q < 1f) drawCircle(Color.White.copy(alpha = .4f * (1f - EaseOut.transform(q))), dd / 2f * 4f * EaseOut.transform(q), Offset(rx, ry))
                    }
                }
                drawRoundRect(Color.White.copy(alpha = .25f), Offset.Zero, size, CornerRadius(r), style = Stroke(.25f * u))
                // arrow
                val nx = 1.6f * u * ping(t, 2.4f, 1.6f)
                val ax = size.width - 8.2f * u - 4.4f * u + nx; val ay = size.height / 2f - 2.2f * u
                translate(ax, ay) { scale(4.4f * u / 24f, Offset.Zero) { drawPath(arrow, Color.White, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)) } }
            }
            Text("Get Started", Modifier.align(Alignment.CenterStart).padding(start = c.dp(8.4f)), softWrap = false,
                style = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = c.spPx(ct), color = Color.White))
        }
    }
}
private val arrow: Path = path("M4 5l7 7-7 7M12 5l7 7-7 7")

/* ============================== LOGIN ============================== */
@Composable
fun LoginUi(busy: Boolean, toast: Pair<Int, String>?, onLogin: (String, String, Boolean) -> Unit, onSignup: (String, String, Boolean) -> Unit, onGoogle: () -> Unit, onForgot: (String) -> Unit) {
    var em by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }; var rem by remember { mutableStateOf(true) }
    var bad1 by remember { mutableStateOf(0) }; var bad2 by remember { mutableStateOf(0) }
    var badOn1 by remember { mutableStateOf(false) }; var badOn2 by remember { mutableStateOf(false) }
    var tmsg by remember { mutableStateOf<String?>(null) }; var tnonce by remember { mutableStateOf(0) }
    fun showToast(m: String) { tmsg = m; tnonce++ }
    LaunchedEffect(toast) { toast?.let { showToast(it.second) } }
    val emailOk = Regex("^\\S+@\\S+\\.\\S+$")
    fun validate(): Boolean {
        val e = em.trim()
        if (!emailOk.matches(e)) { badOn1 = true; bad1++; showToast("Enter a valid email address"); return false }
        if (pw.length < 6) { badOn2 = true; bad2++; showToast("Password must be at least 6 characters"); return false }
        return true
    }
    val scope = rememberCoroutineScope()
    CqStage(1080f / 2400f) { c, tt ->
        val u = c.u
        BgImage(c, tt)
        Canvas(Modifier.fillMaxSize()) {
            val t = tt()
            streak(c, t, 190f, 0f, 8f, 2f, .8f, .4f, .55f)
            streak(c, t, 120f, 2.6f, 10f, 1.4f, .8f, .4f, .55f)
            streak(c, t, 150f, 5f, 8f, 2f, .8f, .4f, .55f)
        }
        Particles(c, tt)
        // icon
        val wPath = remember { path("M21 32C23 56 27 72 36 72 45 72 47 52 50 44 53 52 55 72 64 72 73 72 77 56 79 32") }
        Canvas(Modifier.at(c, 39.8f, 22.2f).sz(c, 20.5f, 20.5f).graphicsLayer {
            val t = tt(); val p = prog(t, .1f, .9f, PopE)
            alpha = p.coerceIn(0f, 1f); val s = .5f + .5f * p; scaleX = s; scaleY = s; rotationZ = -12f * (1f - p)
            translationY = -1.2f * u * ping(t, 1f, 4f)
        }) {
            val t = tt(); val r = 6f * u
            drawCircle(Brush.radialGradient(listOf(rgba(255, 50, 130, .4f), Color.Transparent), center, 10f * u), 10f * u, center)
            val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
            clipPath(shape) {
                drawRect(cssLinear(150f, size.width, size.height, 0f, 0f, 0f to Color(0xFFFF6AA8), .55f to Color(0xFFE0186F), 1f to Color(0xFF8D1A86)))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .45f), Color.White.copy(alpha = .02f)), 0f, size.height / 2f), Offset.Zero, Size(size.width, size.height / 2f))
                val x = if (t < 1.5f) 1f else ((t - 1.5f) % 5f) / 5f
                val pos = if (x < .6f) 160f - 220f * EaseIO.transform(x / .6f) else -60f
                val iw = size.width * 2.6f; val off = (size.width - iw) * pos / 100f
                drawRect(cssLinear(115f, iw, size.height, off, 0f, 0f to Color.Transparent, .38f to Color.Transparent, .5f to Color.White.copy(alpha = .4f), .62f to Color.Transparent, 1f to Color.Transparent))
            }
            drawRoundRect(Color.White.copy(alpha = .2f), Offset.Zero, size, CornerRadius(r), style = Stroke(.25f * u))
            scale(size.width / 100f, Offset.Zero) {
                drawPath(wPath, Brush.verticalGradient(listOf(Color.White, Color(0xFFFFD0E3)), 0f, 100f), style = Stroke(10.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        // word
        val wf = fitPx(c, "wavy", 37.7f, Poppins, FontWeight.Bold, ls = -.4f)
        Row(Modifier.at(c, 0f, 52.3f).fillMaxWidth().upIn(c, tt, .3f), horizontalArrangement = Arrangement.Center) {
            "wavy".forEachIndexed { i, ch ->
                Text(ch.toString(), Modifier.graphicsLayer { translationY = -.9f * u * ping(tt(), i * .15f, 2.4f) }, softWrap = false,
                    style = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = c.spPx(wf), lineHeight = c.sp(14f), letterSpacing = c.sp(-.4f), color = Color.White))
            }
        }
        Text("Your music. Your vibe.", Modifier.at(c, 0f, 69f).fillMaxWidth().upIn(c, tt, .45f), textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Poppins, fontSize = c.sp(3.9f), color = Color.White.copy(alpha = .62f)))
        // fields
        LField(c, tt, 84.6f, .55f, "Email", em, { em = it; badOn1 = false }, false, false, badOn1, bad1, KeyboardType.Email, ImeAction.Next, null, null) { }
        LField(c, tt, 105.3f, .65f, "Password", pw, { pw = it; badOn2 = false }, true, show, badOn2, bad2, KeyboardType.Password, ImeAction.Done, { show = !show }, null) { if (validate()) onLogin(em.trim(), pw, rem) }
        // remember
        val tick by animateFloatAsState(if (rem) 1f else 0f, tween(250, 50), label = "tk")
        val rowH = 6f
        Row(Modifier.at(c, 9.1f, 124.8f).height(c.dp(rowH)).upIn(c, tt, .8f).pointerInput(Unit) { detectTapGestures { rem = !rem } }, verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.sz(c, 6f, 6f)) {
                val r = 1.5f * u
                if (rem) drawRoundRect(rgba(255, 45, 120, .35f), Offset(-1f * u, -1f * u), Size(size.width + 2 * u, size.height + 2 * u), CornerRadius(r + u))
                if (rem) drawRoundRect(vGrad(0f to Color(0xFFFF4F97), 1f to Color(0xFFFF2D78)), Offset.Zero, size, CornerRadius(r)) else drawRoundRect(Color.White.copy(alpha = .06f), Offset.Zero, size, CornerRadius(r))
                drawRoundRect(if (rem) Color(0xFFFF9CC5) else Color.White.copy(alpha = .4f), Offset.Zero, size, CornerRadius(r), style = Stroke(.25f * u))
                val ck = checkPath
                val pm = androidx.compose.ui.graphics.PathMeasure(); pm.setPath(ck, false)
                val seg = Path(); pm.getSegment(0f, pm.length * tick, seg, true)
                translate((size.width - 4.2f * u) / 2f, (size.height - 4.2f * u) / 2f) { scale(4.2f * u / 24f, Offset.Zero) { drawPath(seg, Color.White, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round)) } }
            }
            Spacer(Modifier.width(c.dp(3f)))
            Text("Remember me", softWrap = false, style = TextStyle(fontFamily = Poppins, fontSize = c.sp(4f), color = Color.White.copy(alpha = .88f)))
        }
        Box(Modifier.at(c, 0f, 124.8f).fillMaxWidth().height(c.dp(6f)).upIn(c, tt, .8f), contentAlignment = Alignment.CenterEnd) {
            Text("Forgot password?", Modifier.padding(end = c.dp(9.1f)).pointerInput(Unit) { detectTapGestures { onForgot(em.trim()) } }, softWrap = false,
                style = TextStyle(fontFamily = Poppins, fontSize = c.sp(4f), color = Color.White.copy(alpha = .88f)))
        }
        // sign in
        var rip by remember { mutableStateOf<Triple<Float, Float, Float>?>(null) }
        var pressed by remember { mutableStateOf(false) }
        val press by animateFloatAsState(if (pressed) .97f else 1f, tween(150), label = "pr")
        Box(Modifier.at(c, 7.4f, 138.8f).sz(c, 85.2f, 16f).upIn(c, tt, .95f).graphicsLayer { scaleX = press; scaleY = press }.drawBehind {
            softShadow(Rect(0f, 0f, size.width, size.height), 8f * u, 7f * u, rgba(255, 45, 120, .4f), 2.4f * u)
        }.pointerInput(busy) {
            detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { o ->
                rip = Triple(o.x, o.y, tt()); if (!busy && validate()) onLogin(em.trim(), pw, rem)
            })
        }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val t = tt(); val r = 8f * u
                val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
                clipPath(shape) {
                    drawRect(vGrad(0f to Color(0xFFFF3F87), 1f to Color(0xFFFF2470)))
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .3f), Color.Transparent), 0f, size.height * .52f))
                    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .55f), Color.Transparent), 0f, size.height * .12f), Offset.Zero, size, CornerRadius(r), style = Stroke(.7f * u))
                    if (t >= 2.6f) {
                        val x = ((t - 2.6f) % 4f) / 4f
                        val left = (if (x < .45f) -24f + 124f * EaseIO.transform(x / .45f) else 100f) * u
                        val bw = 16f * u; val sk = size.height / 2f * .364f
                        val p = Path().apply { moveTo(left + sk, 0f); lineTo(left + bw + sk, 0f); lineTo(left + bw - sk, size.height); lineTo(left - sk, size.height); close() }
                        drawPath(p, Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .4f), Color.Transparent), left - sk, left + bw + sk))
                    }
                    rip?.let { (rx, ry, st) ->
                        val q = ((t - st) / .6f).coerceIn(0f, 1f); val dd = max(size.width, size.height) / 3f
                        if (q < 1f) drawCircle(Color.White.copy(alpha = .4f * (1f - EaseOut.transform(q))), dd / 2f * 4f * EaseOut.transform(q), Offset(rx, ry))
                    }
                }
                drawRoundRect(Color.White.copy(alpha = .22f), Offset.Zero, size, CornerRadius(r), style = Stroke(.22f * u))
                if (busy) {
                    val rr = 3f * u; val a = (t / .7f) * 360f
                    drawCircle(Color.White.copy(alpha = .4f), rr, center, style = Stroke(.7f * u))
                    drawArc(Color.White, a - 90f, 90f, false, Offset(center.x - rr, center.y - rr), Size(rr * 2, rr * 2), style = Stroke(.7f * u))
                }
            }
            if (!busy) Text("Sign in", softWrap = false, style = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = c.sp(5f), color = Color.White))
        }
        // or continue with
        Row(Modifier.at(c, 7.4f, 163f).sz(c, 85.2f, 5.6f).upIn(c, tt, 1.1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(c.dp(.2f)).background(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .4f)))))
            Text("or continue with", Modifier.padding(horizontal = c.dp(3.4f)), softWrap = false, style = TextStyle(fontFamily = Poppins, fontSize = c.sp(4.2f), color = Color.White.copy(alpha = .92f)))
            Box(Modifier.weight(1f).height(c.dp(.2f)).background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = .4f), Color.Transparent))))
        }
        // google
        val gp = remember { listOf(
            path("M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z") to Color(0xFFEA4335),
            path("M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z") to Color(0xFF4285F4),
            path("M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z") to Color(0xFFFBBC05),
            path("M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z") to Color(0xFF34A853)) }
        var gPress by remember { mutableStateOf(false) }
        val gs by animateFloatAsState(if (gPress) .92f else 1f, tween(150), label = "gs")
        Canvas(Modifier.at(c, 42.3f, 173.4f).sz(c, 15.5f, 15.5f).upIn(c, tt, 1.2f).graphicsLayer { scaleX = gs; scaleY = gs }.pointerInput(Unit) {
            detectTapGestures(onPress = { gPress = true; tryAwaitRelease(); gPress = false }, onTap = { onGoogle() })
        }) {
            val r = size.width / 2f
            drawCircle(Color.Black.copy(alpha = .12f), r + 2f * u, center.copy(y = center.y + 2f * u))
            drawCircle(cssLinear(160f, size.width, size.height, 0f, 0f, 0f to Color.White.copy(alpha = .14f), 1f to Color.White.copy(alpha = .04f)), r, center)
            drawCircle(Color.White.copy(alpha = .38f), r, center, style = Stroke(.22f * u))
            val s = 7.2f * u / 48f
            translate(center.x - 3.6f * u, center.y - 3.6f * u) { scale(s, Offset.Zero) { gp.forEach { (p, col) -> drawPath(p, col) } } }
        }
        // new to wavy
        Row(Modifier.at(c, 0f, 206.6f).fillMaxWidth().upIn(c, tt, 1.35f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("New to Wavy? ", softWrap = false, style = TextStyle(fontFamily = Poppins, fontSize = c.sp(4.2f), color = Color.White))
            Text("SIGN UP", Modifier.pointerInput(busy) { detectTapGestures { if (!busy) {
                val e = em.trim()
                if (!emailOk.matches(e)) { badOn1 = true; bad1++; showToast("Type your email and a password above, then tap Sign up") }
                else if (pw.length < 6) { badOn2 = true; bad2++; showToast("Password must be at least 6 characters") }
                else onSignup(e, pw, rem) } } }, softWrap = false,
                style = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = c.sp(4.2f), color = PINK, letterSpacing = c.sp(.05f)))
        }
        // home indicator (part of his design)
        Box(Modifier.at(c, 40f, 220f).sz(c, 20f, .9f).background(Color.White.copy(alpha = .75f), androidx.compose.foundation.shape.RoundedCornerShape(c.dp(1f))))
        // toast
        val ty = remember { Animatable(-25f) }
        LaunchedEffect(tnonce) {
            if (tnonce > 0) { ty.animateTo(0f, tween(450, easing = CubicBezierEasing(.2f, 1.2f, .4f, 1f))); delay(2200); ty.animateTo(-25f, tween(450, easing = CubicBezierEasing(.2f, 1.2f, .4f, 1f))) }
        }
        Box(Modifier.fillMaxWidth().offset { IntOffset(0, ((7f + ty.value) * u).roundToInt()) }.zIndex(30f), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.drawBehind {
                val rr = 6f * u
                drawRoundRect(Color.Black.copy(alpha = .15f), Offset(0f, 3f * u), size, CornerRadius(rr))
                drawRoundRect(rgba(40, 10, 40, .55f), Offset.Zero, size, CornerRadius(rr))
                drawRoundRect(Color.White.copy(alpha = .25f), Offset.Zero, size, CornerRadius(rr), style = Stroke(.22f * u))
            }.padding(horizontal = c.dp(6f), vertical = c.dp(3f))) {
                Text(tmsg ?: "", softWrap = false, style = TextStyle(fontFamily = Poppins, fontSize = c.sp(3.8f), color = Color.White))
            }
        }
    }
}
private val checkPath: Path = path("M5 12.5l4.5 4.5L19 7.5")

@Composable
private fun LField(c: Cq, tt: () -> Float, top: Float, delay: Float, label: String, value: String, onChange: (String) -> Unit, password: Boolean, shown: Boolean,
                   bad: Boolean, badTick: Int, kb: KeyboardType, ime: ImeAction, onEye: (() -> Unit)?, unused: Any?, onGo: () -> Unit) {
    val u = c.u
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val f by animateFloatAsState(if (focused) 1f else 0f, tween(250), label = "ff")
    val fl by animateFloatAsState(if (focused || value.isNotEmpty()) 1f else 0f, tween(220), label = "fl")
    val shake = remember { Animatable(0f) }
    LaunchedEffect(badTick) { if (badTick > 0) { shake.snapTo(0f); shake.animateTo(1f, tween(450, easing = androidx.compose.animation.core.LinearEasing)) } }
    val lead = lerp(Color.White.copy(alpha = .7f), Color(0xFFFF7FAE), f)
    val mailP = remember { path("M4 8l8 6 8-6") }
    val lockP = remember { path("M8 10.5V8a4 4 0 0 1 8 0v2.5") }
    val eyeP = remember { path("M1.5 12S5.5 4.5 12 4.5 22.5 12 22.5 12 18.5 19.5 12 19.5 1.5 12 1.5 12z") }
    val slash = remember { path("M4 4l16 16") }
    Box(Modifier.at(c, 7.4f, top).sz(c, 85.2f, 16f).upIn(c, tt, delay).graphicsLayer {
        val p = shake.value
        val k = when { p < .2f -> -1.6f * p / .2f; p < .4f -> -1.6f + 3.2f * (p - .2f) / .2f; p < .6f -> 1.6f - 3.2f * (p - .4f) / .2f; p < .8f -> -1.6f + 3.2f * (p - .6f) / .2f; else -> 1.6f * (1f - (p - .8f) / .2f) }
        translationX = k * u
    }.drawBehind {
        val r = 8f * u; val rect = Rect(0f, 0f, size.width, size.height)
        softShadow(rect, r, 6f * u, Color.Black.copy(alpha = .25f), 2f * u)
        if (bad) softShadow(rect, r, 5f * u, rgba(255, 60, 90, .35f))
        else if (f > 0f) { softShadow(rect, r, 5f * u, rgba(255, 45, 120, .3f * f)); drawRoundRect(rgba(255, 45, 120, .2f * f), Offset(-.5f * u, -.5f * u), Size(size.width + u, size.height + u), CornerRadius(r + .5f * u), style = Stroke(.5f * u)) }
        val shape = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(r))) }
        clipPath(shape) {
            drawRect(cssLinear(160f, size.width, size.height, 0f, 0f, 0f to Color.White.copy(alpha = .1f), 1f to Color.White.copy(alpha = .03f)))
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .18f + .02f * f), Color.Transparent), 0f, size.height * .1f), Offset.Zero, size, CornerRadius(r), style = Stroke(.7f * u))
        }
        val bc = if (bad) Color(0xFFFF4D6D) else lerp(Color.White.copy(alpha = .3f), rgba(255, 70, 150, .9f), f)
        drawRoundRect(bc, Offset(.11f * u, .11f * u), Size(size.width - .22f * u, size.height - .22f * u), CornerRadius(r), style = Stroke(.22f * u))
    }) {
        // lead icon
        Canvas(Modifier.at(c, 6.4f, 5.4f).sz(c, 5.2f, 5.2f)) {
            scale(5.2f * u / 24f, Offset.Zero) {
                val st = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                if (!password) {
                    drawRoundRect(lead, Offset(3f, 5f), Size(18f, 14f), CornerRadius(3f), style = st); drawPath(mailP, lead, style = st)
                } else {
                    drawRoundRect(lead, Offset(5f, 10.5f), Size(14f, 10f), CornerRadius(3f), style = st); drawPath(lockP, lead, style = st)
                }
            }
        }
        Box(Modifier.fillMaxSize().padding(start = c.dp(15.6f), top = c.dp(3.2f), end = c.dp(16f)), contentAlignment = Alignment.CenterStart) {
            BasicTextField(value, onChange, singleLine = true, interactionSource = src,
                textStyle = TextStyle(fontFamily = Poppins, fontSize = c.sp(4f), color = Color.White), cursorBrush = SolidColor(PINK),
                keyboardOptions = KeyboardOptions(keyboardType = kb, imeAction = ime), keyboardActions = KeyboardActions(onDone = { onGo() }),
                visualTransformation = if (password && !shown) PasswordVisualTransformation() else VisualTransformation.None, modifier = Modifier.fillMaxWidth())
        }
        // floating label
        val ly = (5f + (4f - 5f) * fl); val fs = 4f + (2.8f - 4f) * fl
        Text(label, Modifier.at(c, 15.6f, ly + (if (fl > .5f) 0f else 0f)), softWrap = false,
            style = TextStyle(fontFamily = Poppins, fontSize = c.sp(fs), color = lerp(Color.White.copy(alpha = .72f), Color(0xFFFF7FAE), fl)))
        if (password && onEye != null) {
            Canvas(Modifier.at(c, 85.2f - 4.6f - 9f, 3.5f).sz(c, 9f, 9f).pointerInput(Unit) { detectTapGestures { onEye() } }) {
                translate((size.width - 5.6f * u) / 2f, (size.height - 5.6f * u) / 2f) { scale(5.6f * u / 24f, Offset.Zero) {
                    val col = Color.White.copy(alpha = .85f); val st = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawPath(eyeP, col, style = st)
                    if (shown) drawPath(slash, col, style = st) else drawCircle(col, 3.2f, Offset(12f, 12f))
                } }
            }
        }
    }
}
