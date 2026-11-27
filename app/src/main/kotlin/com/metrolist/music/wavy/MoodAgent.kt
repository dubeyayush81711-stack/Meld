package com.metrolist.music.wavy

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The only things the mood helper may do. It never touches settings, accounts or data. */
private val MOODS = listOf(
    "Happy" to listOf("happy", "khush", "good", "mast", "cheer", "excited"),
    "Sad" to listOf("sad", "dukhi", "heartbreak", "breakup", "cry", "alone", "udaas"),
    "Gym" to listOf("gym", "workout", "run", "energy", "pump", "exercise"),
    "Focus" to listOf("focus", "study", "work", "padhai", "concentrate", "calm"),
    "Party" to listOf("party", "dance", "club", "celebrate", "wedding"),
    "Sleep" to listOf("sleep", "night", "relax", "chill", "rest", "neend"),
    "Romantic" to listOf("love", "romantic", "romance", "pyaar", "date"),
)

private fun moodQuery(text: String): Pair<String, String>? {
    val t = text.lowercase()
    val hit = MOODS.firstOrNull { (_, keys) -> keys.any { t.contains(it) } } ?: return null
    return hit.first to (hit.first.lowercase() + " songs " + (S.s("feed_langs").split(",").firstOrNull { it.isNotBlank() } ?: "hindi"))
}

@Composable
fun MoodAgentTab(pc: PlayerConnection?) {
    var text by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf<String?>(null) }
    var list by remember { mutableStateOf<List<Track>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun run(input: String) {
        val m = moodQuery(input)
        if (m == null) { note = "I did not get that mood. Try one of the chips, or words like sad, gym, focus, party, sleep, love."; return }
        busy = true; note = null; mood = m.first
        scope.launch {
            list = try { withContext(Dispatchers.IO) { com.metrolist.innertube.YouTube.search(m.second, com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.mapNotNull { it.asTrack() }?.take(25) ?: emptyList() } } catch (e: Exception) { emptyList() }
            if (list.isEmpty()) note = "Could not load songs. Check your connection."
            busy = false
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("How are you feeling?", style = ts(22, FontWeight.Bold))
        Text("Pick a mood or type it. I find real songs for it. I only build a mix. I never change your settings.", Modifier.padding(top = 4.dp, bottom = 12.dp), style = ts(13, color = W.Dim))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MOODS.forEach { (n, _) -> Text(n, Modifier.clip(RoundedCornerShape(50)).background(if (mood == n) W.Pink else Color(0x22FFFFFF)).clickable { text = n; run(n) }.padding(16.dp, 9.dp), style = ts(14, FontWeight.Medium)) }
        }
        Row(Modifier.padding(top = 14.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF)).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) Text("Type a mood, like \"gym mode\"", style = ts(15, color = Color(0xAAFFFFFF)))
                BasicTextField(text, { text = it }, singleLine = true, textStyle = ts(15), cursorBrush = SolidColor(W.Pink), modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { run(text) }))
            }
            Text("Go", Modifier.clickable { run(text) }.padding(start = 10.dp), style = ts(15, FontWeight.SemiBold, W.Pink))
        }
        note?.let { Text(it, Modifier.padding(top = 12.dp), style = ts(14, color = W.Dim)) }
        if (busy) Text("Finding songs...", Modifier.padding(top = 12.dp), style = ts(14, color = W.Dim))
        if (list.isNotEmpty()) {
            Row(Modifier.padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${mood ?: ""} mix", Modifier.weight(1f), style = ts(18, FontWeight.SemiBold))
                Text("Undo", Modifier.clickable { list = emptyList(); mood = null; text = "" }.padding(10.dp), style = ts(14, FontWeight.Medium, W.Dim))
                Text("Play", Modifier.clip(RoundedCornerShape(50)).background(W.Pink).clickable { playTracks(pc, list, 0, "${mood ?: ""} mix") }.padding(20.dp, 9.dp), style = ts(14, FontWeight.SemiBold))
            }
            list.forEachIndexed { i, t -> TrackRow(t, pc) { playTracks(pc, list, i, "${mood ?: ""} mix") } }
        }
    }
}
