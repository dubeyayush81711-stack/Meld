package com.metrolist.music.wavy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val relatedCache = HashMap<String, List<Track>>()
private val bestCache = HashMap<String, List<Track>>()

/** Real data: songs YouTube Music's own radio returns for the song you played last. */
@Composable
fun RelatedShelf(pc: PlayerConnection?) {
    val last = Recent.list.firstOrNull() ?: return
    var list by remember(last.id) { mutableStateOf(relatedCache[last.id] ?: emptyList<Track>()) }
    LaunchedEffect(last.id) {
        if (list.isNotEmpty()) return@LaunchedEffect
        val got = withContext(Dispatchers.IO) {
            try {
                val r = YouTube.next(WatchEndpoint(videoId = last.id, playlistId = "RDAMVM" + last.id)).getOrNull()
                LangGuard.filter((r?.items ?: emptyList()).mapNotNull { it.asTrack() }.filter { it.id != last.id }).take(15)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emptyList() }
        }
        if (got.isNotEmpty()) { relatedCache[last.id] = got; list = got }
    }
    if (list.size < 4) return
    Column {
        HomeHead("More like " + last.title.take(28))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(list) { i, t -> MiniCard(t.cover, t.title, t.artist) { playTracks(pc, list, i, "More like " + last.title) } }
        }
        Spacer(Modifier.height(26.dp))
    }
}

/** Real data: YouTube Music search for the songs of the artist you play most, kept only if that artist is on the song. */
@Composable
fun BestOfShelf(pc: PlayerConnection?) {
    val a = remember { TasteProfile.topArtists(1).firstOrNull() } ?: return
    var list by remember(a) { mutableStateOf(bestCache[a] ?: emptyList<Track>()) }
    LaunchedEffect(a) {
        if (list.isNotEmpty()) return@LaunchedEffect
        val got = try {
            LangGuard.filter(liveSongs("$a top songs").tracks()).filter { it.artist.contains(a, ignoreCase = true) }.take(15)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { emptyList() }
        if (got.isNotEmpty()) { bestCache[a] = got; list = got }
    }
    if (list.size < 4) return
    Column {
        HomeHead("Best of $a")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(list) { i, t -> MiniCard(t.cover, t.title, t.artist) { playTracks(pc, list, i, "Best of $a") } }
        }
        Spacer(Modifier.height(26.dp))
    }
}
