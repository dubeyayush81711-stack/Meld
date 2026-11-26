package com.metrolist.music.wavy

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
    return if (offset == 0 && out.size >= 3) (kotlinx.coroutines.withTimeoutOrNull(4000) { Ai.reorder("", titles, out) } ?: out) else out
}

private suspend fun rawPage(titles: List<String>, offset: Int, seed: String): List<Track> {
    if (titles.isEmpty()) return (Api.rpc("player_feed_ig", JSONObject().put("p_limit", 30).put("p_offset", offset).put("p_region", "IN").put("p_seed", seed)) as? JSONArray).tracks()
    val tiles = languageTiles()
    val codes = titles.mapNotNull { t -> tiles.firstOrNull { it.second.equals(t, true) || it.first.equals(t, true) }?.first }
    if (codes.isEmpty()) return emptyList()
    val per = 24
    val lists = codes.map { c -> try { (Api.rpc("language_top", JSONObject().put("p_lang", c).put("p_limit", per).put("p_offset", offset / codes.size)) as? JSONArray).tracks() } catch (e: Exception) { emptyList() } }
    val out = ArrayList<Track>()
    for (i in 0 until per) for (l in lists) l.getOrNull(i)?.let { out.add(it) }
    return out.chunked(6).flatMap { g -> g.sortedBy { Math.abs((seed + it.id).hashCode()) % 100 } }
}

fun normKey(t: Track) = (t.title.lowercase().replace(Regex("\\(.*?\\)|\\[.*?\\]|official|video|audio|lyrical|lyrics|\\bhd\\b|[^a-z0-9\u0900-\u0DFF ]"), " ").replace(Regex("\\s+"), " ").trim() + "|" + t.artist.lowercase().split(Regex("[,&]| x | feat\\.?")).first().trim())

/** Songs the user has already seen in the language feed; kept across refreshes so repeats do not come back. */
object Played {
    private fun load(): LinkedHashSet<String> = LinkedHashSet(S.s("played_hist").split("\n").filter { it.isNotBlank() })
    fun mark(t: Track) { val h = load(); h.remove(t.id); h.add(t.id); h.remove("k:" + normKey(t)); h.add("k:" + normKey(t)); S.put("played_hist", h.toList().takeLast(4000).joinToString("\n")) }
    fun filter(list: List<Track>): List<Track> {
        val h = load(); val seen = HashSet<String>(); val out = ArrayList<Track>()
        for (t in list) { val k = "k:" + normKey(t); if (t.id in h || k in h) continue; if (!seen.add(k) || !seen.add(t.id)) continue; out.add(t) }
        return out
    }
}
