package com.metrolist.music.wavy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Listening stats from real data on this phone: the songs you played (Recent) and how many times (PlayCount). */
@Composable
fun StatsTab(pc: PlayerConnection?) {
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Recent.load() } }
    val rl = Recent.list
    val played = rl.filter { PlayCount.of(it.id) > 0 }
    if (played.isEmpty()) {
        Text("Play some songs and your stats show up here.", Modifier.padding(24.dp), style = ts(15, color = W.Dim))
        return
    }
    val plays = played.sumOf { PlayCount.of(it.id) }
    val mins = played.sumOf { PlayCount.of(it.id).toLong() * it.dur } / 60
    val topSongs = played.sortedByDescending { PlayCount.of(it.id) }.take(5)
    val artistPlays = HashMap<String, Int>()
    played.forEach { t -> val a = t.artist.split(",", "&").firstOrNull()?.trim().orEmpty(); if (a.isNotEmpty()) artistPlays[a] = (artistPlays[a] ?: 0) + PlayCount.of(t.id) }
    val topArtists = artistPlays.entries.sortedByDescending { it.value }.take(5)
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("Plays", plays.toString(), Modifier.weight(1f))
            StatBox("Songs", played.size.toString(), Modifier.weight(1f))
            StatBox("Minutes", "~$mins", Modifier.weight(1f))
        }
        Text("Minutes counts every play as the full song length, so it is approximate. Based on songs played on this phone.", Modifier.padding(top = 8.dp), style = ts(12, color = W.Dim))
        Text("Top artists", Modifier.padding(top = 24.dp, bottom = 8.dp), style = ts(22, FontWeight.Bold))
        topArtists.forEachIndexed { i, e -> StatLine((i + 1).toString(), e.key, "${e.value} plays") }
    }
    Text("Top songs", Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp), style = ts(22, FontWeight.Bold))
    topSongs.forEachIndexed { i, t -> TrackRow(t, pc) { playTracks(pc, topSongs, i, "Top songs") } }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x1AFFFFFF)).padding(14.dp)) {
        Text(value, style = ts(24, FontWeight.Bold))
        Text(label, style = ts(13, color = W.Dim))
    }
}

@Composable
private fun StatLine(rank: String, name: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rank, Modifier.size(28.dp), style = ts(16, FontWeight.Bold, W.Pink))
        Text(name, Modifier.weight(1f), style = ts(16, FontWeight.SemiBold), maxLines = 1)
        Text(sub, style = ts(13, color = W.Dim))
    }
}
