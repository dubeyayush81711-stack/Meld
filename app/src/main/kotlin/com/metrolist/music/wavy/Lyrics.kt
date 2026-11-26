package com.metrolist.music.wavy

import android.content.Context
import com.metrolist.music.lyrics.LyricsProvider
import com.metrolist.music.lyrics.LyricsProviderRegistry
import com.metrolist.music.lyrics.LyricsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

/** Small thread-safe cache of the last 60 songs. Failures (empty results) are never stored, so a network hiccup does not hide lyrics until the app restarts. */
private val lyricCache: MutableMap<String, List<LyricLine>> = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, List<LyricLine>>(64, 0.75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, List<LyricLine>>?) = size > 60 })

/** Strips marketing words, feat. credits and brackets so providers match on the real song name. */
fun cleanSongTitle(t: String): String {
    var s = LyricsUtils.cleanTitleForSearch(t)
    // Only marketing tags at the very end of the title are removed, so songs like "Video Killed the Radio Star" keep their words.
    s = s.replace(Regex("(?i)[\\s\\-|:]*\\b(official music video|official video|official audio|music video|video song|lyrical video|lyric video|full video|full song|lyrical|audio|hd|4k)\\s*$"), "")
    s = s.replace(Regex("(?i)\\s*(feat\\.?|ft\\.?|featuring)\\s+.*$"), "")
    s = s.replace(Regex("\\s*[-|:]\\s*$"), "").replace(Regex("\\s+"), " ").trim()
    return if (s.length >= 2) s else t.trim()
}

/**
 * Lyrics from real providers only, tried in order: LRCLIB, BetterLyrics, KuGou, Paxsenix, LyricsPlus, Zemer,
 * YouTube subtitles, YouTube. A time-synced hit wins at once. If nobody has synced lines, the first plain text
 * is returned with t = -1 per line. Nothing is generated. Empty list when no provider has the song.
 */
suspend fun fetchLyrics(ctx: Context, id: String, title: String, artist: String, durSec: Int, album: String?): List<LyricLine> = withContext(Dispatchers.IO) {
    lyricCache[id]?.let { return@withContext it }
    val names = listOf("LrcLib", "BetterLyrics", "KuGou", "Paxsenix", "LyricsPlus", "Zemer", "YouTubeSubtitle", "YouTube")
    val ct = cleanSongTitle(title)
    // Artist names like "Simon & Garfunkel" are tried whole first, then just the first artist.
    val artistTries = listOf(artist.substringBefore(",").trim(), artist.substringBefore(",").substringBefore("&").trim()).filter { it.isNotEmpty() }.distinct().ifEmpty { listOf(artist.trim()) }
    var plain: List<LyricLine> = emptyList()
    for (n in names) {
        val p: LyricsProvider = LyricsProviderRegistry.getProviderByName(n) ?: continue
        var raw: String? = null
        for (ca in artistTries) {
            raw = try { if (!p.isEnabled(ctx)) null else withTimeoutOrNull(6000) { p.getLyrics(ctx, id, ct, ca, durSec, album).getOrNull() } } catch (e: Exception) { null }
            if (!raw.isNullOrBlank()) break
        }
        if (raw.isNullOrBlank()) continue
        val lines = try { parseLyrics(LyricsUtils.filterLyricsCreditLines(raw)) } catch (e: Exception) { emptyList() }
        if (lines.isEmpty()) continue
        if (lines.any { it.t >= 0 }) { val s = lines.filter { it.t >= 0 }; lyricCache[id] = s; return@withContext s }
        if (plain.isEmpty()) plain = lines
    }
    if (plain.isNotEmpty()) lyricCache[id] = plain
    plain
}

/** Caption styles for the reel lyrics, like story captions. Tap the lyrics to switch. */
val LYRIC_STYLES = listOf("Bold", "Karaoke", "Neon", "Minimal", "Big word")

@androidx.compose.runtime.Composable
fun LyricsView(synced: List<LyricLine>, ci: Int, pos: Long, style: Int, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val line = synced.getOrNull(ci) ?: return
    val next = synced.getOrNull(ci + 1)
    val frac = if (next != null && next.t > line.t) ((pos - line.t).toFloat() / (next.t - line.t)).coerceIn(0f, 1f) else 0f
    val white = androidx.compose.ui.graphics.Color.White
    val dim = androidx.compose.ui.graphics.Color(0x66FFFFFF)
    androidx.compose.animation.AnimatedContent(ci, modifier, transitionSpec = {
        (androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(280)) { it / 2 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(280))) togetherWith
            (androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(220)) { -it / 3 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)))
    }, label = "lyr") { i ->
        val l = synced.getOrNull(i) ?: line
        val n = synced.getOrNull(i + 1)
        androidx.compose.foundation.layout.Column {
            when (style) {
                1 -> androidx.compose.material3.Text(l.text, style = ts(26, androidx.compose.ui.text.font.FontWeight.Bold).copy(brush = if (i == ci) androidx.compose.ui.graphics.Brush.horizontalGradient(0f to white, frac to white, (frac + 0.001f).coerceAtMost(1f) to dim, 1f to dim) else androidx.compose.ui.graphics.SolidColor(white)), maxLines = 3)
                2 -> androidx.compose.material3.Text(l.text, style = ts(26, androidx.compose.ui.text.font.FontWeight.Bold, androidx.compose.ui.graphics.Color(0xFFFFE6F2)).copy(shadow = androidx.compose.ui.graphics.Shadow(androidx.compose.ui.graphics.Color(0xFFFF2E8A), androidx.compose.ui.geometry.Offset.Zero, 22f)), maxLines = 3)
                3 -> androidx.compose.material3.Text(l.text, style = ts(20, androidx.compose.ui.text.font.FontWeight.Medium, androidx.compose.ui.graphics.Color(0xEEFFFFFF)), maxLines = 3)
                4 -> androidx.compose.material3.Text(l.text.split(" ").let { w -> w.getOrNull(((frac * w.size).toInt()).coerceIn(0, w.size - 1)) ?: l.text }, style = ts(44, androidx.compose.ui.text.font.FontWeight.ExtraBold), maxLines = 1)
                else -> androidx.compose.material3.Text(l.text, style = ts(26, androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 3)
            }
            if (style != 4 && n != null) androidx.compose.material3.Text(n.text, androidx.compose.ui.Modifier.padding(top = 8.dp), style = ts(if (style == 3) 16 else 20, androidx.compose.ui.text.font.FontWeight.Bold, androidx.compose.ui.graphics.Color(0x77FFFFFF)), maxLines = 2)
        }
    }
}
