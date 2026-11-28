package com.metrolist.music.wavy

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device taste profile. Nothing leaves the phone.
 * Signals: completed plays (+2), partial plays (+0.7), skips (-1.5), likes (+4), searches (+1 to a matching artist).
 * Every weight halves after 10 days, so recent listening counts most. Time of day is tracked per artist (4 buckets).
 * Recommendations come from YouTube Music's own radio for songs the user finished or liked, re-scored by this profile.
 */
object TasteProfile {
    private const val KEY = "taste_v1"
    private const val HALF_LIFE_MS = 10L * 24 * 3600 * 1000
    private class A(var n: String, var id: String?, var w: Double, var t: Long, val b: IntArray = IntArray(4))
    private val artists = LinkedHashMap<String, A>()
    private val good = ArrayList<Track>()
    private val terms = ArrayList<Pair<String, Long>>()
    private val seen = HashMap<String, Track>()
    private var loaded = false
    private var cache: Pair<Long, List<Track>>? = null

    private val KSPLIT = Regex("[,&]| x | feat\\.?")
    fun key(artist: String) = artist.lowercase().split(KSPLIT).first().trim()
    private fun bucket(): Int { val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY); return when { h < 6 -> 0; h < 12 -> 1; h < 18 -> 2; else -> 3 } }
    private fun eff(a: A, now: Long = System.currentTimeMillis()) = a.w * Math.pow(0.5, (now - a.t).toDouble() / HALF_LIFE_MS)

    @Synchronized private fun load() {
        if (loaded) return; loaded = true
        try {
            val j = JSONObject(S.s(KEY, "{}"))
            j.optJSONArray("a")?.let { arr -> for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val b = o.optJSONArray("b"); val a = A(o.getString("n"), o.optString("i").ifBlank { null }, o.optDouble("w"), o.optLong("t"), IntArray(4) { k -> b?.optInt(k) ?: 0 }); artists[key(a.n)] = a } }
            j.optJSONArray("g")?.let { arr -> for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); good.add(Track(o.getString("i"), o.optString("t"), o.optString("a"), o.optString("c").ifBlank { null }, o.optInt("d"), o.optString("x").ifBlank { null }, null)) } }
            j.optJSONArray("s")?.let { arr -> for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); terms.add(o.getString("q") to o.optLong("t")) } }
        } catch (e: Exception) { }
    }

    @Synchronized private fun save() {
        try {
            val now = System.currentTimeMillis()
            if (artists.size > 150) { val keep = artists.entries.sortedByDescending { Math.abs(eff(it.value, now)) }.take(120).map { it.key }.toSet(); artists.keys.retainAll(keep) }
            val a = JSONArray(); artists.values.forEach { a.put(JSONObject().put("n", it.n).put("i", it.id ?: "").put("w", it.w).put("t", it.t).put("b", JSONArray(it.b.toList()))) }
            val g = JSONArray(); good.take(40).forEach { g.put(JSONObject().put("i", it.id).put("t", it.title).put("a", it.artist).put("c", it.cover ?: "").put("d", it.dur).put("x", it.artistId ?: "")) }
            val s = JSONArray(); terms.take(40).forEach { s.put(JSONObject().put("q", it.first).put("t", it.second)) }
            S.put(KEY, JSONObject().put("a", a).put("g", g).put("s", s).toString())
        } catch (e: Exception) { }
    }

    private fun bump(t: Track, delta: Double, positive: Boolean) {
        val k = key(t.artist); if (k.isBlank()) return
        val now = System.currentTimeMillis()
        val a = artists.getOrPut(k) { A(t.artist.split(Regex("[,&]")).first().trim(), t.artistId, 0.0, now) }
        a.w = eff(a, now) + delta; a.t = now; if (a.id == null) a.id = t.artistId
        if (positive) { a.b[bucket()]++; good.removeAll { it.id == t.id }; good.add(0, t); while (good.size > 40) good.removeAt(good.size - 1) }
        cache = null
    }

    /** Remember what is playing so a later like can be matched to its artist. */
    fun see(t: Track) { load(); seen[t.id] = t; if (seen.size > 60) seen.remove(seen.keys.first()) }

    /** Called when a song stops being the current one. listenedMs is the last position reached. */
    fun played(t: Track, listenedMs: Long, durMs: Long) {
        if (t.id.startsWith("local:") || t.id.isBlank()) return
        load()
        val frac = if (durMs > 0) listenedMs.toDouble() / durMs else if (listenedMs > 60000) 1.0 else 0.5
        when {
            listenedMs < 20000 || frac < 0.3 -> bump(t, -1.5, false)
            frac >= 0.8 -> bump(t, 2.0, true)
            else -> bump(t, 0.7, true)
        }
        save()
    }

    fun liked(id: String) { load(); val t = seen[id] ?: good.firstOrNull { it.id == id } ?: return; bump(t, 4.0, true); save() }

    fun searched(q: String) {
        load(); val s = q.trim(); if (s.length < 2) return
        terms.removeAll { it.first.equals(s, true) }; terms.add(0, s to System.currentTimeMillis()); while (terms.size > 40) terms.removeAt(terms.size - 1)
        val ql = s.lowercase(); val k = artists.keys.firstOrNull { it.length >= 3 && (ql == it || ql.contains(it)) }
        if (k != null) artists[k]?.let { it.w = eff(it) + 1.0; it.t = System.currentTimeMillis() }
        cache = null; save()
    }

    /** Affinity of an artist: positive = likes, negative = skips. */
    fun affinity(artist: String): Double {
        load(); val a = artists[key(artist)] ?: return 0.0
        var e = eff(a); val sum = a.b.sum()
        if (e > 0 && sum >= 3 && a.b[bucket()].toDouble() / sum > 0.5) e *= 1.25
        return e
    }

    fun hasProfile(): Boolean { load(); return good.isNotEmpty() }
    fun topArtists(n: Int): List<String> { load(); val now = System.currentTimeMillis(); return artists.values.filter { eff(it, now) > 1.0 }.sortedByDescending { eff(it, now) }.take(n).map { it.n } }
    fun recentSearches(n: Int): List<String> { load(); return terms.take(n).map { it.first } }

    fun invalidate() { cache = null }

    /** New account on this phone: forget everything the previous user taught the profile. */
    @Synchronized fun reset() { artists.clear(); good.clear(); terms.clear(); seen.clear(); loaded = false; cache = null }
    fun hasHistory(): Boolean { load(); return good.isNotEmpty() }

    /** First-run picks: the top songs of the artists chosen in the picker (YouTube Music artist page, popular songs), 4 per artist, interleaved. Empty if none were chosen. */
    suspend fun pickedPicks(): Pair<List<String>, List<Track>> = withContext(Dispatchers.IO) {
        val rows = try { Api.get("user_artists?select=artists(id,name)&order=created_at.asc") as? JSONArray } catch (e: Exception) { null } ?: return@withContext emptyList<String>() to emptyList()
        val ar = (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.optJSONObject("artists") }.map { it.optString("id") to it.optString("name") }.filter { it.first.isNotBlank() }.take(6)
        val lists = coroutineScope { ar.map { (id, _) -> async { try { kotlinx.coroutines.withTimeoutOrNull(8000) {
            val pg = YouTube.artist(id).getOrNull() ?: return@withTimeoutOrNull emptyList<Track>()
            val sec = pg.sections.firstOrNull { sc -> sc.items.any { it is com.metrolist.innertube.models.SongItem } && sc.items.none { it is com.metrolist.innertube.models.AlbumItem } }
            sec?.items?.filterIsInstance<com.metrolist.innertube.models.SongItem>()?.map { it.toTrack() }?.take(4) ?: emptyList()
        } ?: emptyList() } catch (e: Exception) { emptyList() } } }.map { it.await() } }
        val out = ArrayList<Track>(); for (i in 0 until 4) lists.forEach { l -> l.getOrNull(i)?.let { out.add(it) } }
        ar.map { it.second } to Played.filter(out)
    }
    fun cached(): List<Track> = cache?.takeIf { System.currentTimeMillis() - it.first < 20 * 60_000L }?.second ?: emptyList()

    /** Picks for this user. Seeds = recently finished or liked songs from different artists; candidates = YouTube Music radio of each seed. */
    suspend fun recommend(n: Int = 30): List<Track> = withContext(Dispatchers.IO) {
        load()
        val hit = cached(); if (hit.isNotEmpty()) return@withContext hit
        val seeds = ArrayList<Track>(); val sk = HashSet<String>()
        // Meld quick-picks idea: seeds are the latest plays plus older favourites ("forgotten favourites"), so one binge does not take over
        for (t in good) if (LangGuard.ok(t) && sk.add(key(t.artist))) { seeds.add(t); if (seeds.size >= 5) break }
        for (t in good.drop(12).reversed()) if (LangGuard.ok(t) && sk.add(key(t.artist))) { seeds.add(t); if (seeds.size >= 8) break }
        if (seeds.isEmpty()) return@withContext emptyList()
        val lists = coroutineScope { seeds.map { s -> async { try { withTimeoutOrNull(8000) { YouTube.next(WatchEndpoint(videoId = s.id, playlistId = "RDAMVM${s.id}")).getOrNull()?.items?.map { it.toTrack() } } ?: emptyList() } catch (e: Exception) { emptyList<Track>() } } }.map { it.await() } }
        val score = HashMap<String, Double>(); val by = HashMap<String, Track>(); val referred = HashMap<String, Int>()
        val seedIds = seeds.map { it.id }.toSet(); val seedKeys = seeds.map { normKey(it) }.toSet()
        lists.forEachIndexed { si, l ->
            val sw = 1.0 - si * 0.12
            l.forEachIndexed { pos, t ->
                if (t.id in seedIds || normKey(t) in seedKeys) return@forEachIndexed
                val aff = affinity(t.artist)
                if (aff < -2.5) return@forEachIndexed
                val sc = sw * (1.0 - 0.5 * pos / Math.max(1, l.size)) + aff.coerceIn(-4.0, 8.0) * 0.1
                val k = normKey(t)
                // Meld ranks related songs by how many seeds point at them (referredCount); same here
                val rc = (referred[k] ?: 0) + 1; referred[k] = rc
                val total = sc + 0.6 * (rc - 1)
                if (total > (score[k] ?: -9.0) || rc > 1) { score[k] = Math.max(total, score[k] ?: -9.0); if (by[k] == null) by[k] = t }
            }
        }
        val cand = Played.filter(LangGuard.filter(score.entries.sortedByDescending { it.value }.mapNotNull { by[it.key] }))
        // Fame gate: songs by artists the user already plays always stay; new artists must be well known (about 1M+ monthly listeners on YouTube Music).
        // Unknown fame (lookup failed) is kept. Proxy, not real play counts.
        try { Fame.warm(cand.take(40).map { it.artistId }) } catch (e: Exception) { }
        val famed = cand.filter { t -> affinity(t.artist) > 0.5 || Fame.score(listOf(t.artistId)).let { f -> f == 0f && !Fame.known(t.artistId) || f >= 0.5f } }
        // Explore / exploit: about 2 familiar-artist songs for every new-artist song, so picks are not only what he already plays.
        val known = famed.filter { affinity(it.artist) > 0.5 }; val fresh0 = famed.filter { affinity(it.artist) <= 0.5 }
        val mixed = ArrayList<Track>(); val ki = known.iterator(); val ni = fresh0.iterator()
        while (ki.hasNext() || ni.hasNext()) { repeat(2) { if (ki.hasNext()) mixed.add(ki.next()) }; if (ni.hasNext()) mixed.add(ni.next()) }
        val fresh = if (mixed.size >= 6) mixed else famed.ifEmpty { cand }
        // variety: no artist twice in a row within 3 picks, at most 3 songs per artist
        val out = ArrayList<Track>(); val cnt = HashMap<String, Int>(); val pool = ArrayList(fresh)
        while (out.size < n && pool.isNotEmpty()) {
            val last = out.takeLast(3).map { key(it.artist) }
            val pick = pool.firstOrNull { key(it.artist) !in last && (cnt[key(it.artist)] ?: 0) < 3 } ?: pool.firstOrNull { (cnt[key(it.artist)] ?: 0) < 3 } ?: break
            pool.remove(pick); out.add(pick); cnt[key(pick.artist)] = (cnt[key(pick.artist)] ?: 0) + 1
        }
        val checked = AiGate.verify(out)
        if (checked.isNotEmpty()) cache = System.currentTimeMillis() to checked
        checked
    }

    suspend fun recommendQuick(): List<Track> = withTimeoutOrNull(3500) { recommend() } ?: cached()

    /** Base feed with songs by artists the user keeps skipping removed, mild re-ranking inside blocks of 8, and one personal pick after every two feed songs. */
    fun blend(base: List<Track>, pers: List<Track>, used: Set<String> = emptySet()): List<Track> {
        load()
        val kept = LangGuard.filter(base).filter { affinity(it.artist) >= -2.5 }
        val ranked = kept.chunked(8).flatMap { c -> c.sortedByDescending { affinity(it.artist).coerceIn(0.0, 6.0) } }
        val have = ranked.map { it.id }.toHashSet(); have.addAll(used)
        val it2 = LangGuard.filter(pers).filter { it.id !in have }.iterator()
        if (!it2.hasNext()) return ranked
        val out = ArrayList<Track>()
        ranked.forEachIndexed { i, t -> out.add(t); if (i % 2 == 1 && it2.hasNext()) out.add(it2.next()) }
        return out
    }

    /** Search suggestions: recent searches first, then top artists. */
    fun forYouSuggestions(n: Int): List<String> = (recentSearches(3) + topArtists(5)).distinctBy { it.lowercase() }.take(n)

    /** Typed suggestions: ones that name an artist the user plays, or share a word with a recent search, move up. */
    fun rankSuggestions(list: List<String>): List<String> {
        load(); val tops = topArtists(8).map { it.lowercase() }; val words = terms.take(10).flatMap { it.first.lowercase().split(" ") }.filter { it.length > 3 }.toSet()
        return list.sortedByDescending { s -> val l = s.lowercase(); (if (tops.any { l.contains(it) }) 2 else 0) + (if (words.any { l.contains(it) }) 1 else 0) }
    }
}

@Composable
fun ForYouShelf(pc: PlayerConnection?) {
    var list by remember { mutableStateOf<List<Track>>(LangGuard.filter(TasteProfile.cached())) }
    var pickedNames by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(HomeRefresh.n) {
        try { list = TasteProfile.recommend(20) } catch (e: Exception) { }
        if (list.isEmpty() && !TasteProfile.hasHistory()) try { val (n, l) = TasteProfile.pickedPicks(); if (l.isNotEmpty()) { pickedNames = n; list = l } } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { }
    }
    if (list.isEmpty()) return
    Column {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Text(if (pickedNames.isNotEmpty()) "Because you picked " + pickedNames.take(2).joinToString(" and ") else TasteProfile.topArtists(2).let { a -> if (a.isEmpty()) "Based on what you play, like and skip" else "Because you play " + a.joinToString(" and ") }, style = ts(12, color = Color(0x94FFFFFF)))
            Text("Picked for you", style = ts(20, androidx.compose.ui.text.font.FontWeight.SemiBold))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(list) { i, t -> MiniCard(t.cover, t.title, t.artist) { playTracks(pc, list, i, "Picked for you") } }
        }
        val quick = list.drop(10)
        if (quick.size >= 3) { Spacer(Modifier.height(24.dp)); PagedTracks("Fast picks", quick, pc, null) }
        Spacer(Modifier.height(28.dp))
    }
}
