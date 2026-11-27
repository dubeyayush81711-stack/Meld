package com.metrolist.music.wavy

import kotlinx.coroutines.async
import org.json.JSONArray
import org.json.JSONObject

private var tileCache: List<Pair<String, String>>? = null

/** (language code, title) pairs from the backend. */
suspend fun languageTiles(): List<Pair<String, String>> {
    tileCache?.let { return it }
    val arr = try { Api.rpc("language_tiles") as? JSONArray } catch (e: Exception) { null } ?: return emptyList()
    val l = (0 until arr.length()).map { arr.getJSONObject(it) }.map { it.getString("lang") to it.getString("title") }
    tileCache = l
    return l
}

/** One page of the Music tab feed for the chosen languages (titles). Empty selection = the mixed For You feed. Ranked by the backend's chart rank, interleaved across languages. */
suspend fun feedPage(titles: List<String>, offset: Int, seed: String): List<Track> {
    // never show a song the user already heard: keep reading deeper pages until enough fresh ones are found
    val out = ArrayList<Track>(); val seen = HashSet<String>()
    var off = offset; var tries = 0
    while (tries < 5 && out.size < 12) {
        val raw = try { rawPage(titles, off, seed) } catch (e: Exception) { if (tries == 0) throw e else emptyList() }
        if (raw.isEmpty()) break
        for (t in Played.filter(raw)) if (seen.add(t.id)) out.add(t)
        off += raw.size; tries++
    }
    return out
}

private suspend fun rawPage(titles: List<String>, offset: Int, seed: String): List<Track> {
    if (titles.isEmpty()) return (Api.rpc("player_feed_ig", JSONObject().put("p_limit", 30).put("p_offset", offset).put("p_region", "IN").put("p_seed", seed)) as? JSONArray).tracks()
    val tiles = languageTiles()
    val codes = titles.mapNotNull { t -> tiles.firstOrNull { it.second.equals(t, true) || it.first.equals(t, true) }?.first }
    if (codes.isEmpty()) return emptyList()
    val per = 24
    val lists = kotlinx.coroutines.coroutineScope {
        codes.map { c -> async { try { kotlinx.coroutines.withTimeoutOrNull(8000) { (Api.rpc("language_top", JSONObject().put("p_lang", c).put("p_limit", per).put("p_offset", offset / codes.size)) as? JSONArray).tracks() } ?: emptyList() } catch (e: Exception) { emptyList() } } }.map { it.await() }
    }
    val out = ArrayList<Track>()
    for (i in 0 until per) for (l in lists) l.getOrNull(i)?.let { if (LangGuard.quality(it)) out.add(it) }
    return out.chunked(6).flatMap { g -> g.sortedBy { Math.abs((seed + it.id).hashCode()) % 100 } }
}

private val NK_JUNK = Regex("\\(.*?\\)|\\[.*?\\]|official|video|audio|lyrical|lyrics|\\bhd\\b|[^a-z0-9\u0900-\u0DFF ]")
private val NK_WS = Regex("\\s+")
private val NK_SPLIT = Regex("[,&]| x | feat\\.?")
fun normKey(t: Track) = (t.title.lowercase().replace(NK_JUNK, " ").replace(NK_WS, " ").trim() + "|" + t.artist.lowercase().split(NK_SPLIT).first().trim())

/** Songs the user has already seen in the language feed; kept across refreshes so repeats do not come back. */
object Played {
    private var mem: LinkedHashSet<String>? = null
    @Synchronized private fun load(): LinkedHashSet<String> = mem ?: LinkedHashSet(S.s("played_hist").split("\n").filter { it.isNotBlank() }).also { mem = it }
    @Synchronized fun mark(t: Track) { val h = load(); h.remove(t.id); h.add(t.id); h.remove("k:" + normKey(t)); h.add("k:" + normKey(t)); while (h.size > 4000) h.remove(h.first()); val snap = h.joinToString("\n"); Thread { S.put("played_hist", snap) }.start() }
    fun filter(list: List<Track>): List<Track> {
        val h = load(); val seen = HashSet<String>(); val out = ArrayList<Track>()
        for (t in list) { val k = "k:" + normKey(t); if (t.id in h || k in h) continue; if (!seen.add(k) || !seen.add(t.id)) continue; out.add(t) }
        return out
    }
}

/** Every song that was opened, even if it stopped a moment later. Newest first, one entry per song, last 100. */
object Recent {
    var list by androidx.compose.runtime.mutableStateOf<List<Track>>(emptyList())
    private var loaded = false
    private fun key() = "recent_tracks_" + (Api.uid ?: "")
    @Synchronized fun load() {
        if (loaded) return; loaded = true
        list = try { val a = org.json.JSONArray(S.s(key(), "[]")); (0 until a.length()).map { val o = a.getJSONObject(it); Track(o.getString("i"), o.getString("t"), o.getString("a"), o.optString("c").ifBlank { null }, o.optInt("d"), o.optString("x").ifBlank { null }, o.optString("l").ifBlank { null }) } } catch (e: Exception) { emptyList() }
    }
    @Synchronized fun add(t: Track) {
        load(); val n = (listOf(t) + list.filter { it.id != t.id }).take(100); list = n
        val snap = org.json.JSONArray().also { a -> n.forEach { a.put(org.json.JSONObject().put("i", it.id).put("t", it.title).put("a", it.artist).put("c", it.cover ?: "").put("d", it.dur).put("x", it.artistId ?: "").put("l", it.album ?: "")) } }.toString()
        Thread { S.put(key(), snap) }.start()
    }
}
