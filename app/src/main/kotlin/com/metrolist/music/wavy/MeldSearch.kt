package com.metrolist.music.wavy

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.AlbumItem as YAlbum
import com.metrolist.innertube.models.ArtistItem as YArtist
import com.metrolist.innertube.models.PlaylistItem as YPlaylist
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.YouTubeQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Search the way Meld does it: one sectioned summary search for "All", and a typed search (with paging) for each chip. */
data class SSection(val title: String, val items: List<YTItem>)
data class SPage(val items: List<YTItem>, val next: String?)

private val sumCache = object : LinkedHashMap<String, List<SSection>>(16, 0.75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, List<SSection>>?) = size > 30 }
private val typeCache = object : LinkedHashMap<String, SPage>(32, 0.75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, SPage>?) = size > 60 }

suspend fun meldSummary(q: String): List<SSection> = withContext(Dispatchers.IO) {
    sumCache[q.lowercase()]?.let { return@withContext it }
    val r = promoteBest(q, YouTube.searchSummary(q).getOrNull()?.summaries?.map { SSection(it.title, it.items) } ?: emptyList())
    if (r.isNotEmpty()) sumCache[q.lowercase()] = r
    r
}

fun filterOf(name: String): YouTube.SearchFilter? = when (name) {
    "Songs" -> YouTube.SearchFilter.FILTER_SONG
    "Videos" -> YouTube.SearchFilter.FILTER_VIDEO
    "Albums" -> YouTube.SearchFilter.FILTER_ALBUM
    "Artists" -> YouTube.SearchFilter.FILTER_ARTIST
    "Playlists" -> YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
    else -> null
}

suspend fun meldTyped(q: String, name: String): SPage = withContext(Dispatchers.IO) {
    val key = name + "|" + q.lowercase()
    typeCache[key]?.let { return@withContext it }
    val f = filterOf(name) ?: return@withContext SPage(emptyList(), null)
    val r = YouTube.search(q, f).getOrNull() ?: return@withContext SPage(emptyList(), null)
    val p = SPage(rankTyped(q, r.items.distinctBy { it.id }), r.continuation)
    if (p.items.isNotEmpty()) typeCache[key] = p
    p
}

suspend fun meldMore(cont: String): SPage = withContext(Dispatchers.IO) {
    val r = YouTube.searchContinuation(cont).getOrNull() ?: return@withContext SPage(emptyList(), null)
    SPage(r.items, r.continuation)
}

fun YTItem.asTrack(): Track? = (this as? SongItem)?.let { s -> Track(s.id, s.title, s.artists.joinToString { it.name }, s.thumbnail, s.duration ?: 0, s.artists.firstOrNull()?.id, s.album?.name) }

fun YTItem.sub(): String = when (this) {
    is YAlbum -> listOfNotNull("Album", artists?.joinToString { it.name }, year?.toString()).joinToString(" \u00B7 ")
    is YArtist -> "Artist"
    is YPlaylist -> listOfNotNull("Playlist", author?.name, songCountText).joinToString(" \u00B7 ")
    is SongItem -> artists.joinToString { it.name }
    else -> ""
}

/** Albums and playlists start playing; an artist opens that artist's songs. */
fun playItem(pc: PlayerConnection?, it: YTItem) {
    if (pc == null) return
    when (it) {
        is YAlbum -> pc.playQueue(YouTubeQueue(WatchEndpoint(playlistId = it.playlistId)))
        is YPlaylist -> pc.playQueue(YouTubeQueue(it.playEndpoint ?: WatchEndpoint(playlistId = it.id)))
        else -> {}
    }
}

private fun fold(x: String) = java.text.Normalizer.normalize(x.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim()

/** Same idea Spotifeur uses: how closely the title matches what was typed (65%), plus the rank YouTube Music gave it (35%). */
fun rankTyped(q: String, items: List<YTItem>): List<YTItem> {
    val want = fold(q); if (want.isEmpty()) return items
    val words = want.split(' ')
    fun rel(x: YTItem): Double {
        val t = fold(x.title)
        val all = t + " " + fold(x.sub())
        return when {
            t == want -> 1.0
            t.startsWith(want) -> 0.9
            words.all { t.contains(it) } -> 0.8
            words.all { all.contains(it) } -> 0.75
            else -> 0.4 * words.count { all.contains(it) } / words.size
        }
    }
    return items.withIndex().sortedByDescending { (i, x) -> rel(x) * 0.65 + 0.35 / (1 + i * 0.15) }.map { it.value }
}

private fun phon(x: String) = fold(x).replace(Regex("[^a-z0-9 ]"), "").replace(Regex("(.)\\1+"), "$1").replace(Regex("h(?=\\b|[aeiou])"), "").replace(Regex("[aeiouy]"), "")

private fun titleScore(q: String, x: YTItem): Double {
    val want = fold(q); if (want.isEmpty()) return 0.0
    val t = fold(x.title)
    if (t == want) return 1.0
    val ws = want.split(' ').filter { it.length > 1 }; if (ws.isEmpty()) return 0.0
    val hit = ws.count { t.contains(it) } / ws.size.toDouble()
    val pw = phon(want); val pt = phon(x.title)
    val ph = if (pw.length >= 4 && (pt.contains(pw) || pw.contains(pt) && pt.length >= 4)) 0.9 else 0.0
    return maxOf(if (t.startsWith(want)) 0.95 else 0.0, hit * 0.9, ph)
}

/** An exact or near-exact title match goes first as the Top result; artist matches only lead when nothing matches by title. */
fun promoteBest(q: String, secs: List<SSection>): List<SSection> {
    if (secs.isEmpty()) return secs
    val ranked = secs.map { SSection(it.title, if (it.items.any { i -> i is SongItem }) rankTyped(q, it.items) else it.items) }
    var best: YTItem? = null; var bs = 0.0
    ranked.forEach { sec -> sec.items.filter { it is SongItem || it is YAlbum }.forEachIndexed { i, x -> val sc = titleScore(q, x) - i * 0.001; if (sc > bs) { bs = sc; best = x } } }
    val b = best ?: return ranked
    if (bs < 0.8) return ranked
    val top = ranked.indexOfFirst { it.title.contains("Top", true) }
    if (top < 0) return listOf(SSection("Top result", listOf(b))) + ranked.map { SSection(it.title, it.items.filterNot { i -> i.id == b.id }) }.filter { it.items.isNotEmpty() }
    return ranked.mapIndexed { i, sec -> if (i == top) SSection(sec.title, listOf(b) + sec.items.filterNot { it.id == b.id }) else SSection(sec.title, sec.items.filterNot { it.id == b.id }) }.filter { it.items.isNotEmpty() }
}
