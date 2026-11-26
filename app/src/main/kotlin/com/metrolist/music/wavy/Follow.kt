package com.metrolist.music.wavy

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private var followCache: Pair<Set<String>, List<Track>>? = null

/** Artist ids the user follows (stored locally as follow_<id>, and in user_artists when signed in). */
fun followedArtistIds(): Set<String> = S.keysWithPrefix("follow_").filter { S.b(it) }.map { it.removePrefix("follow_") }.toSet()

/** Real songs of followed artists from their YT Music artist pages (up to 6 artists, 6 songs each). Cached per follow set. */
suspend fun followedSongs(): List<Track> = withContext(Dispatchers.IO) {
    val ids = followedArtistIds()
    if (ids.isEmpty()) return@withContext emptyList()
    followCache?.let { if (it.first == ids) return@withContext it.second }
    val out = ids.take(6).flatMap { id ->
        try { YouTube.artist(id).getOrNull()?.sections?.flatMap { it.items }?.filterIsInstance<SongItem>()?.take(6)?.map { s -> Track(s.id, s.title, s.artists.joinToString { it.name }, s.thumbnail, s.duration ?: 0, s.artists.firstOrNull()?.id, s.album?.name) } ?: emptyList() } catch (e: Exception) { emptyList() }
    }.shuffled()
    followCache = ids to out
    out
}

/** One followed-artist song after every two normal ones, so followed artists are about a third of the feed at most. */
fun mixFollowed(base: List<Track>, fol: List<Track>, used: MutableSet<String> = HashSet()): List<Track> {
    if (fol.isEmpty()) return base
    val pool = fol.filter { f -> base.none { it.id == f.id } }.iterator()
    val out = ArrayList<Track>()
    base.forEachIndexed { i, t ->
        out.add(t)
        if (i % 2 == 1) while (pool.hasNext()) { val f = pool.next(); if (used.add(f.id)) { out.add(f); break } }
    }
    return out
}
