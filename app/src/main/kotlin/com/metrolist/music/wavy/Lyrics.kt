package com.metrolist.music.wavy

import android.content.Context
import com.metrolist.music.lyrics.LyricsProvider
import com.metrolist.music.lyrics.LyricsProviderRegistry
import com.metrolist.music.lyrics.LyricsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private val lyricCache = HashMap<String, List<LyricLine>>()

/** Strips marketing words, feat. credits and brackets so providers match on the real song name. */
fun cleanSongTitle(t: String): String {
    var s = LyricsUtils.cleanTitleForSearch(t)
    s = s.replace(Regex("(?i)\\b(official|music video|video song|lyrical|lyric video|full video|full song|audio|hd|4k|video)\\b"), " ")
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
    val ct = cleanSongTitle(title); val ca = artist.substringBefore(",").substringBefore("&").trim()
    var plain: List<LyricLine> = emptyList()
    for (n in names) {
        val p: LyricsProvider = LyricsProviderRegistry.getProviderByName(n) ?: continue
        val raw = try { if (!p.isEnabled(ctx)) null else withTimeoutOrNull(6000) { p.getLyrics(ctx, id, ct, ca, durSec, album).getOrNull() } } catch (e: Exception) { null } ?: continue
        val lines = try { parseLyrics(LyricsUtils.filterLyricsCreditLines(raw)) } catch (e: Exception) { emptyList() }
        if (lines.isEmpty()) continue
        if (lines.any { it.t >= 0 }) { val s = lines.filter { it.t >= 0 }; lyricCache[id] = s; return@withContext s }
        if (plain.isEmpty()) plain = lines
    }
    lyricCache[id] = plain
    plain
}
