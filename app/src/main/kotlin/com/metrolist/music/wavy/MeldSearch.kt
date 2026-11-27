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
import kotlinx.coroutines.async

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
    val tw = t.split(' '); val aw = (t + " " + fold(x.sub())).split(' ')
    val hitAll = ws.count { w -> aw.any { it.startsWith(w) } } / ws.size.toDouble()
    val hitT = ws.count { w -> tw.any { it.startsWith(w) } } / ws.size.toDouble()
    val combo = if (hitAll >= 1.0 && hitT > 0.0) 0.82 + 0.1 * hitT else 0.0
    return maxOf(if (t.startsWith(want)) 0.95 else 0.0, hit * 0.9, ph, combo)
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

// ---- Sort by views (with a recency bonus) ----
private val scoreCache = HashMap<String, Float>()

private fun parseUploadAgeYears(d: String?): Float? {
    if (d.isNullOrBlank()) return null
    val fmts = listOf("MMM d, yyyy", "d MMM yyyy", "MMMM d, yyyy", "d MMMM yyyy")
    for (f in fmts) {
        try {
            val dt = java.text.SimpleDateFormat(f, java.util.Locale.ENGLISH).parse(d.trim()) ?: continue
            return ((System.currentTimeMillis() - dt.time) / (365.25 * 24 * 3600 * 1000)).toFloat().coerceAtLeast(0f)
        } catch (e: Exception) { }
    }
    return null
}

/** Score per video id: log10(views) plus up to +1.0 for songs under 1 year old (+0.5 under 2 years). Ids with no view count are left out. */
suspend fun viewScores(ids: List<String>): Map<String, Float> = withContext(Dispatchers.IO) {
    val need = ids.filter { !scoreCache.containsKey(it) }.take(15)
    kotlinx.coroutines.coroutineScope {
        need.map { id -> async {
            val mi = try { YouTube.getMediaInfo(id).getOrNull() } catch (e: Exception) { null }
            val v = mi?.viewCount
            if (v != null && v > 0) {
                val age = parseUploadAgeYears(mi.uploadDate)
                val bonus = if (age == null) 0f else if (age < 1f) 1f else if (age < 2f) 0.5f else 0f
                synchronized(scoreCache) { scoreCache[id] = kotlin.math.log10(v.toFloat() + 1f) + bonus }
            }
        } }.forEach { it.await() }
    }
    ids.mapNotNull { id -> scoreCache[id]?.let { id to it } }.toMap()
}

/** Songs with a score take the slots of scored songs, best combined score first (match likelihood + views + recency). Everything else keeps its place. */
fun byViews(items: List<YTItem>, sc: Map<String, Float>): List<YTItem> {
    if (sc.isEmpty()) return items
    val slots = items.indices.filter { (items[it] as? SongItem)?.let { s -> sc.containsKey(s.id) } == true }
    if (slots.size < 2) return items
    // Final score = views score (log10 views + recency bonus) + 2.5 * match likelihood (1.0 for the top relevance result, down to 0.0 for the last one).
    val n = slots.size
    val sorted = slots.mapIndexed { r, pos -> items[pos] to ((sc[(items[pos] as SongItem).id] ?: 0f) + 2.5f * (1f - r.toFloat() / (n - 1)) + 1.5f * Fame.score((items[pos] as SongItem).artists.map { a -> a.id })) }.sortedByDescending { it.second }.map { it.first }
    val out = items.toMutableList()
    slots.forEachIndexed { i, pos -> out[pos] = sorted[i] }
    return out
}


/**
 * Artist fame from real public data: the subscriber count on the artist's YouTube Music page (one lookup per artist, cached 30 days on the phone).
 * fame 0.0 to 1.0 = (log10(subscribers) - 4) / 4, so 10K is 0.0 and 100M is 1.0. If the count is not available the artist gets 0.0 (no guess, no hand-picked names).
 */
object Fame {
    private val subs = java.util.concurrent.ConcurrentHashMap<String, Float>()   // artist id -> fame 0..1, -1 = looked up, no count
    private var loaded = false
    private fun load() {
        if (loaded) return; loaded = true
        try {
            val raw = S.s("fame_cache", ""); val now = System.currentTimeMillis()
            raw.split(";").forEach { e -> val p = e.split("|"); if (p.size == 3 && now - (p[2].toLongOrNull() ?: 0L) < 30L * 86_400_000L) subs[p[0]] = p[1].toFloatOrNull() ?: -1f }
        } catch (e: Exception) { }
    }
    private fun save() {
        try { val now = System.currentTimeMillis(); S.put("fame_cache", subs.entries.take(400).joinToString(";") { it.key + "|" + it.value + "|" + now }) } catch (e: Exception) { }
    }
    fun parseCount(t: String?): Double? {
        if (t.isNullOrBlank()) return null
        val m = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*([KMBkmb]?)").find(t) ?: return null
        val n = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
        return n * when (m.groupValues[2].uppercase()) { "K" -> 1e3; "M" -> 1e6; "B" -> 1e9; else -> 1.0 }
    }
    fun score(artistIds: List<String?>): Float { load(); return artistIds.filterNotNull().maxOfOrNull { (subs[it] ?: 0f).coerceAtLeast(0f) } ?: 0f }
    /** Looks up up to 8 artists not yet known, in parallel. Safe to call repeatedly, known artists cost nothing. */
    suspend fun warm(artistIds: List<String?>) = withContext(Dispatchers.IO) {
        load()
        val need = artistIds.filterNotNull().filter { it.isNotBlank() && !subs.containsKey(it) }.distinct().take(8)
        if (need.isEmpty()) return@withContext
        kotlinx.coroutines.coroutineScope {
            need.map { id -> async {
                try {
                    val pg = YouTube.artist(id).getOrNull()
                    val n = parseCount(pg?.monthlyListenerCount) ?: parseCount(pg?.subscriberCountText)
                    subs[id] = if (n != null && n > 0) ((kotlin.math.log10(n) - 4.0) / 4.0).toFloat().coerceIn(0f, 1f) else -1f
                } catch (e: Exception) { }
            } }.forEach { it.await() }
        }
        save()
    }
    /** Stable reorder of one batch: a song moves up by up to 8 places for a top-fame artist. Songs with equal fame keep their order. */
    suspend fun lift(tracks: List<Track>): List<Track> {
        warm(tracks.map { it.artistId })
        return tracks.withIndex().sortedBy { (i, t) -> i - 8f * score(listOf(t.artistId)) }.map { it.value }
    }
}
