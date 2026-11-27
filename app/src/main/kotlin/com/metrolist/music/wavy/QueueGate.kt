package com.metrolist.music.wavy

import androidx.media3.common.MediaItem
import com.metrolist.music.extensions.metadata

/**
 * Quality gate for songs the player adds on its own (radio pages, autoplay).
 * Same rules as the shelves: language, junk titles and length, songs already heard, artists the user keeps skipping,
 * and no artist twice in a row. If the gate would remove everything it returns the input, so autoplay never stops.
 */
object QueueGate {
    private fun track(m: MediaItem): Track? {
        val md = m.metadata ?: return null
        return Track(md.id, md.title, md.artists.joinToString { it.name }, md.thumbnailUrl, md.duration, md.artists.firstOrNull()?.id, null)
    }

    fun filter(items: List<MediaItem>, heard: Set<String> = emptySet()): List<MediaItem> {
        if (items.isEmpty()) return items
        return try {
            val seen = HashSet<String>(heard)
            val good = ArrayList<MediaItem>()
            for (m in items) {
                val t = track(m) ?: continue
                if (!seen.add(t.id)) continue
                if (!LangGuard.ok(t)) continue
                if (TasteProfile.affinity(t.artist) < -2.5) continue
                if (Played.filter(listOf(t)).isEmpty()) continue
                good.add(m)
            }
            if (good.isEmpty()) return items
            val out = ArrayList<MediaItem>(); val pool = ArrayList(good)
            while (pool.isNotEmpty()) {
                val last = out.lastOrNull()?.let { track(it)?.artist }?.let { TasteProfile.key(it) }
                val pick = pool.firstOrNull { m -> track(m)?.artist?.let { TasteProfile.key(it) } != last } ?: pool.first()
                pool.remove(pick); out.add(pick)
            }
            out
        } catch (e: Exception) { items }
    }
}
