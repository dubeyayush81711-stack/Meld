package com.metrolist.music.wavy

import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue

fun Track.toMeta() = MediaMetadata(
    id = id, title = title,
    artists = listOf(MediaMetadata.Artist(artistId, artist)),
    duration = dur, thumbnailUrl = cover,
    album = album?.let { MediaMetadata.Album(id = "wavy:" + it.hashCode(), title = it) },
)

fun playTracks(pc: PlayerConnection?, list: List<Track>, index: Int, title: String?) {
    if (pc == null || list.isEmpty()) return
    pc.playQueue(ListQueue(title = title, items = list.map { it.toMeta().toMediaItem() }, startIndex = index.coerceIn(0, list.size - 1)))
}
