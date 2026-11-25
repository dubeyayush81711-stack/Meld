package com.metrolist.music.wavy

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val lyricCache = HashMap<String, List<LyricLine>>()

/** Synced lyrics from LRCLIB through the engine's own provider (free, no key). Empty list when nothing matches, so the screen stays clean. */
suspend fun fetchLyrics(ctx: Context, id: String, title: String, artist: String, durSec: Int, album: String?): List<LyricLine> = withContext(Dispatchers.IO) {
    lyricCache[id]?.let { return@withContext it }
    val r = try {
        com.metrolist.music.lyrics.LrcLibLyricsProvider.getLyrics(ctx, id, title, artist.substringBefore(","), durSec, album).getOrNull()?.let { parseLyrics(it) }.orEmpty().filter { it.t >= 0 }
    } catch (e: Exception) { emptyList() }
    lyricCache[id] = r
    r
}
