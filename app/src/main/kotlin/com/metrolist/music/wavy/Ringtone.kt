package com.metrolist.music.wavy

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.datasource.DataSpec
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/** Copies the song's audio (through the same engine source the player uses, so the cache is reused) into a temp file. */
private suspend fun fetchAudio(ctx: Context, pc: PlayerConnection, id: String, onPct: (Int) -> Unit): File = withContext(Dispatchers.IO) {
    val f = File(ctx.cacheDir, "cut_$id.bin")
    if (f.exists() && f.length() > 50_000) return@withContext f
    val factory = pc.service.wavyDsf ?: error("Player is not ready yet")
    val ds = factory.createDataSource()
    try {
        val len = ds.open(DataSpec.Builder().setUri(Uri.parse("https://music.youtube.com/watch?v=$id")).setKey(id).build())
        val buf = ByteArray(64 * 1024); var got = 0L
        f.outputStream().use { o ->
            while (true) {
                val n = ds.read(buf, 0, buf.size); if (n < 0) break
                o.write(buf, 0, n); got += n
                if (len > 0) onPct((got * 100 / len).toInt().coerceIn(0, 100))
            }
        }
    } finally { try { ds.close() } catch (_: Exception) { } }
    f
}

private class Cut(val file: File, val durUs: Long)

private suspend fun probe(f: File): Cut = withContext(Dispatchers.IO) {
    val ex = MediaExtractor(); ex.setDataSource(f.absolutePath)
    var d = 0L
    for (i in 0 until ex.trackCount) { val fm = ex.getTrackFormat(i); if (fm.getString(MediaFormat.KEY_MIME)?.startsWith("audio") == true && fm.containsKey(MediaFormat.KEY_DURATION)) d = fm.getLong(MediaFormat.KEY_DURATION) }
    ex.release()
    if (d <= 0) error("Could not read this audio"); Cut(f, d)
}

/** Stream-copies [startUs, endUs] into a new file without re-encoding. Returns the file and its extension/mime. */
private suspend fun trim(src: File, startUs: Long, endUs: Long, out: File): Pair<String, String> = withContext(Dispatchers.IO) {
    val ex = MediaExtractor(); ex.setDataSource(src.absolutePath)
    var ti = -1; var fmt: MediaFormat? = null
    for (i in 0 until ex.trackCount) { val f = ex.getTrackFormat(i); if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio") == true) { ti = i; fmt = f; break } }
    if (ti < 0 || fmt == null) error("No audio track")
    val mime = fmt.getString(MediaFormat.KEY_MIME)!!
    val (fmtId, ext, mt) = when {
        mime == "audio/mp4a-latm" -> Triple(MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, "m4a", "audio/mp4")
        Build.VERSION.SDK_INT >= 29 && mime == "audio/opus" -> Triple(MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG, "ogg", "audio/ogg")
        else -> Triple(MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM, "webm", "audio/webm")
    }
    ex.selectTrack(ti)
    val mx = MediaMuxer(out.absolutePath, fmtId)
    val mi = mx.addTrack(fmt); mx.start()
    ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    val buf = ByteBuffer.allocate(1 shl 20); val info = MediaCodec.BufferInfo()
    while (true) {
        buf.clear(); val n = ex.readSampleData(buf, 0); if (n < 0) break
        val t = ex.sampleTime; if (t > endUs) break
        if (t >= startUs - 50_000) { info.set(0, n, (t - startUs).coerceAtLeast(0), ex.sampleFlags); mx.writeSampleData(mi, buf, info) }
        ex.advance()
    }
    mx.stop(); mx.release(); ex.release()
    ext to mt
}

private fun saveToRingtones(ctx: Context, f: File, name: String, ext: String, mime: String): Uri? {
    if (Build.VERSION.SDK_INT < 29) {
        val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_RINGTONES); dir.mkdirs()
        val d = File(dir, "$name.$ext"); f.copyTo(d, true); return Uri.fromFile(d)
    }
    val v = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.$ext"); put(MediaStore.Audio.Media.TITLE, name); put(MediaStore.Audio.Media.MIME_TYPE, mime)
        put(MediaStore.Audio.Media.RELATIVE_PATH, "Ringtones/Wavy"); put(MediaStore.Audio.Media.IS_RINGTONE, true); put(MediaStore.Audio.Media.IS_MUSIC, false)
    }
    val u = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v) ?: return null
    ctx.contentResolver.openOutputStream(u)?.use { o -> f.inputStream().use { it.copyTo(o) } }
    return u
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtoneCutter(t: Track, pc: PlayerConnection?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pct by remember { mutableIntStateOf(0) }
    var err by remember { mutableStateOf<String?>(null) }
    var cut by remember { mutableStateOf<Cut?>(null) }
    var range by remember { mutableStateOf(0f..1f) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Uri?>(null) }
    var mp by remember { mutableStateOf<MediaPlayer?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { try { mp?.release() } catch (_: Exception) { } } }
    LaunchedEffect(t.id, attempt) {
        err = null; cut = null
        if (pc == null) { err = "Player is not ready yet. Play a song first."; return@LaunchedEffect }
        try { val f = fetchAudio(ctx, pc, t.id) { pct = it }; val c = probe(f); cut = c
            val total = c.durUs / 1e6f; range = 0f..minOf(30f, total) / total
        } catch (e: Exception) { err = "Could not get this song's audio (${e.message ?: "error"}). Check your connection and try again." }
    }
    fun fmt(us: Float): String { val s = (us / 1e6f).toInt(); return "%d:%02d".format(s / 60, s % 60) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF2101014)).clickable(enabled = false) {}) {
            Column(Modifier.align(Alignment.Center).padding(24.dp).fillMaxWidth()) {
                Text("Ringtone cutter", style = ts(22, FontWeight.Bold))
                Text(t.title + " - " + t.artist, Modifier.padding(top = 4.dp), style = ts(14, color = W.Dim), maxLines = 1)
                Spacer(Modifier.height(24.dp))
                val c = cut
                if (c == null) {
                    if (err != null) {
                        Text(err!!, style = ts(14, color = W.Dim))
                        Text("Try again", Modifier.padding(top = 14.dp).clickable { attempt++ }, style = ts(15, FontWeight.SemiBold, W.Pink))
                    } else Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(26.dp), color = W.Pink, strokeWidth = 3.dp)
                        Text("  Getting the full song... $pct%", style = ts(14, color = W.Dim))
                    }
                } else {
                    val total = c.durUs.toFloat()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Start " + fmt(range.start * total), style = ts(14, FontWeight.SemiBold))
                        Text("Length " + fmt((range.endInclusive - range.start) * total), style = ts(14, color = W.Dim))
                        Text("End " + fmt(range.endInclusive * total), style = ts(14, FontWeight.SemiBold))
                    }
                    RangeSlider(range, { r -> range = r; try { mp?.stop() } catch (_: Exception) { } }, colors = SliderDefaults.colors(thumbColor = W.Pink, activeTrackColor = W.Pink))
                    Text("Full song is ${fmt(total)}. Drag the two handles to pick the part you like.", style = ts(12, color = W.Dim))
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0x22FFFFFF)).clickable {
                            scope.launch {
                                try {
                                    pc?.player?.pause()
                                    mp?.release(); val m = MediaPlayer(); m.setDataSource(c.file.absolutePath); m.prepare()
                                    m.seekTo((range.start * total / 1000).toInt()); m.start(); mp = m
                                    val stopAt = (range.endInclusive * total / 1000).toInt()
                                    while (m.isPlaying && m.currentPosition < stopAt) delay(100)
                                    if (mp === m) m.pause()
                                } catch (e: Exception) { Toast.makeText(ctx, "Preview is not available for this file", Toast.LENGTH_SHORT).show() }
                            }
                        }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) { Text("Preview", style = ts(15, FontWeight.SemiBold)) }
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(W.Pink).clickable(enabled = !busy) {
                            busy = true; try { mp?.pause() } catch (_: Exception) { }
                            scope.launch {
                                try {
                                    val tmp = File(ctx.cacheDir, "cut_out.tmp")
                                    val (ext, mt) = trim(c.file, (range.start * total).toLong(), (range.endInclusive * total).toLong(), tmp)
                                    val nm = t.title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "Wavy clip" } + " (Wavy cut)"
                                    saved = withContext(Dispatchers.IO) { saveToRingtones(ctx, tmp, nm, ext, mt) }
                                    if (saved == null) err = "Could not save the clip"
                                } catch (e: Exception) { err = "Could not cut this song: ${e.message ?: "error"}" }
                                busy = false
                            }
                        }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) { Text(if (busy) "Cutting..." else "Done", style = ts(15, FontWeight.Bold)) }
                    }
                    if (err != null) Text(err!!, Modifier.padding(top = 12.dp), style = ts(13, color = W.Dim))
                    val u = saved
                    if (u != null) {
                        Spacer(Modifier.height(18.dp))
                        Text("Saved to your phone (Ringtones/Wavy folder).", style = ts(14, FontWeight.SemiBold))
                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Set as ringtone", Modifier.clickable {
                                try {
                                    if (Settings.System.canWrite(ctx)) { RingtoneManager.setActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE, u); Toast.makeText(ctx, "Ringtone set", Toast.LENGTH_SHORT).show() }
                                    else { Toast.makeText(ctx, "Allow Wavy to change settings, then tap again", Toast.LENGTH_LONG).show(); ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                } catch (e: Exception) { Toast.makeText(ctx, "Could not set it. Pick it in Settings > Sound.", Toast.LENGTH_LONG).show() }
                            }, style = ts(15, FontWeight.SemiBold, W.Pink))
                            Text("Share", Modifier.clickable {
                                try { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share clip").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
                            }, style = ts(15, FontWeight.SemiBold, W.Pink))
                        }
                    }
                }
                Text("Close", Modifier.padding(top = 28.dp).clickable(onClick = onClose), style = ts(15, color = W.Dim))
            }
        }
    }
}
