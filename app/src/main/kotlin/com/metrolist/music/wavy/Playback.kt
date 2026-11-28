package com.metrolist.music.wavy

import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.Queue
import androidx.media3.common.MediaItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Track.toMeta() = MediaMetadata(
    id = id, title = title,
    artists = listOf(MediaMetadata.Artist(artistId, artist)),
    duration = dur, thumbnailUrl = cover,
    album = album?.let { MediaMetadata.Album(id = "wavy:" + it.hashCode(), title = it) },
)

fun playTracks(pc: PlayerConnection?, list: List<Track>, index: Int, title: String?) {
    if (pc == null || list.isEmpty()) return
    val i = index.coerceIn(0, list.size - 1)
    pc.playQueue(RadioTailQueue(title, list.map { it.toMeta().toMediaItem() }, i, list[i].id))
}

/** The list the user tapped, then endless songs: YouTube Music's radio for the tapped song (popular songs of the same style), added when the list is nearly done. The player's own filters (language, heard, junk) still apply. Stops after 4 pages. */
class RadioTailQueue(val title: String?, val items: List<MediaItem>, val startIndex: Int, val seedId: String) : Queue {
    override val preloadItem: MediaMetadata? = null
    private var ep = WatchEndpoint(videoId = seedId, playlistId = "RDAMVM$seedId")
    private var cont: String? = null
    private var pages = 0
    private val have = items.map { it.mediaId }.toHashSet()
    override suspend fun getInitialStatus() = Queue.Status(title, items, startIndex)
    override fun hasNextPage() = pages < 4
    override suspend fun nextPage(): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val r = YouTube.next(ep, cont).getOrThrow()
            ep = r.endpoint; cont = r.continuation; pages++
            if (cont == null) pages = 99
            r.items.map { it.toMediaItem() }.filter { have.add(it.mediaId) }
        } catch (e: Exception) { pages += 2; emptyList() }
    }
}
