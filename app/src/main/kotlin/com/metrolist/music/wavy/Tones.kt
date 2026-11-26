package com.metrolist.music.wavy

import android.content.ContentUris
import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ToneItem(val uri: Uri, val name: String)

/** Tones cut in Wavy: files in Ringtones/Wavy, read from the media store. Nothing is read on the main thread. */
private fun loadTones(ctx: Context): List<ToneItem> {
    val out = ArrayList<ToneItem>()
    try {
        val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TITLE)
        val sel: String; val args: Array<String>
        if (Build.VERSION.SDK_INT >= 29) { sel = MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ?"; args = arrayOf("Ringtones/Wavy%") }
        else { sel = MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ?"; args = arrayOf("%(Wavy cut %") }
        ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, sel, args, MediaStore.Audio.Media.DATE_ADDED + " DESC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0); val t = c.getString(2) ?: c.getString(1) ?: "Tone"
                out.add(ToneItem(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), t))
            }
        }
    } catch (e: Exception) { }
    return out
}

@Composable
fun TonesTab() {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val tones by produceState(emptyList<ToneItem>(), refresh) { value = withContext(Dispatchers.IO) { loadTones(ctx) } }
    var mp by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingUri by remember { mutableStateOf<Uri?>(null) }
    DisposableEffect(Unit) { onDispose { try { mp?.release() } catch (_: Exception) { } } }
    fun preview(u: Uri) {
        try { mp?.release() } catch (_: Exception) { }
        if (playingUri == u) { mp = null; playingUri = null; return }
        try {
            val m = MediaPlayer(); m.setDataSource(ctx, u); m.prepare(); m.setOnCompletionListener { playingUri = null }; m.start(); mp = m; playingUri = u
        } catch (e: Exception) { mp = null; playingUri = null; android.widget.Toast.makeText(ctx, "Could not play this tone", android.widget.Toast.LENGTH_SHORT).show() }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Make a tone: open a song in the player, tap the tune icon and choose Ringtone. Pick the part you want, then save it. Your tones show up here.", Modifier.padding(vertical = 10.dp), style = ts(14, color = W.Dim))
        if (tones.isEmpty()) Text("No tones yet.", Modifier.padding(vertical = 16.dp), style = ts(15, color = W.Dim))
        tones.forEach { t ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x1AFFFFFF)).padding(14.dp)) {
                Text(t.name, style = ts(16, FontWeight.SemiBold), maxLines = 2)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        (if (playingUri == t.uri) "Stop" else "Play") to { preview(t.uri) },
                        "Ringtone" to { setToneAs(ctx, t.uri, RingtoneManager.TYPE_RINGTONE, "Ringtone") },
                        "Notification" to { setToneAs(ctx, t.uri, RingtoneManager.TYPE_NOTIFICATION, "Notification sound") },
                    ).forEach { (l, a) -> Text(l, Modifier.clip(RoundedCornerShape(50)).background(W.Pink).clickable { a() }.padding(horizontal = 12.dp, vertical = 7.dp), style = ts(13, FontWeight.SemiBold)) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Pair<String, () -> Unit>>(
                        "Alarm" to { setToneAs(ctx, t.uri, RingtoneManager.TYPE_ALARM, "Alarm sound") },
                        "Share" to { shareTone(ctx, t.uri) },
                        "Delete" to {
                            try { if (playingUri == t.uri) preview(t.uri); ctx.contentResolver.delete(t.uri, null, null); refresh++ }
                            catch (e: Exception) { android.widget.Toast.makeText(ctx, "Could not delete this tone. Remove it from your Files app.", android.widget.Toast.LENGTH_LONG).show() }
                        },
                    ).forEach { (l, a) -> Text(l, Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF2A2A2A)).clickable { a() }.padding(horizontal = 12.dp, vertical = 7.dp), style = ts(13, FontWeight.SemiBold)) }
                }
            }
        }
    }
}
