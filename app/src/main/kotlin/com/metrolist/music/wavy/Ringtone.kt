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
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import com.metrolist.music.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Icon
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

/** Keeps letters and digits from any language (Hindi too), and adds a short time tag so two cuts of one song never overwrite each other. */
fun toneName(title: String): String {
    val clean = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifBlank { "Wavy clip" }.take(60)
    return clean + " (Wavy cut " + java.text.SimpleDateFormat("HHmmss", java.util.Locale.US).format(java.util.Date()) + ")"
}

fun saveToRingtones(ctx: Context, f: File, name: String, ext: String, mime: String): Uri? {
    if (Build.VERSION.SDK_INT < 29) {
        val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_RINGTONES); dir.mkdirs()
        val d = File(dir, "$name.$ext"); f.copyTo(d, true); return Uri.fromFile(d)
    }
    val v = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.$ext"); put(MediaStore.Audio.Media.TITLE, name); put(MediaStore.Audio.Media.MIME_TYPE, mime)
        put(MediaStore.Audio.Media.RELATIVE_PATH, "Ringtones/Wavy"); put(MediaStore.Audio.Media.IS_RINGTONE, true); put(MediaStore.Audio.Media.IS_NOTIFICATION, true); put(MediaStore.Audio.Media.IS_ALARM, true); put(MediaStore.Audio.Media.IS_MUSIC, false)
    }
    val u = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v) ?: return null
    ctx.contentResolver.openOutputStream(u)?.use { o -> f.inputStream().use { it.copyTo(o) } }
    return u
}

/** Rough loudness shape of the song for the timeline. Reads the size of each compressed audio frame (bigger frames mean busier or louder audio), no decoding. */
private suspend fun waveform(f: File, buckets: Int): FloatArray = withContext(Dispatchers.IO) {
    val out = FloatArray(buckets)
    try {
        val ex = MediaExtractor(); ex.setDataSource(f.absolutePath)
        var ti = -1; var dur = 0L
        for (i in 0 until ex.trackCount) { val fm = ex.getTrackFormat(i); if (fm.getString(MediaFormat.KEY_MIME)?.startsWith("audio") == true) { ti = i; if (fm.containsKey(MediaFormat.KEY_DURATION)) dur = fm.getLong(MediaFormat.KEY_DURATION); break } }
        if (ti < 0 || dur <= 0) { ex.release(); return@withContext FloatArray(buckets) { 0.5f } }
        ex.selectTrack(ti)
        val sums = FloatArray(buckets); val cnt = IntArray(buckets)
        while (true) {
            val sz = ex.sampleSize; if (sz < 0) break
            val b = ((ex.sampleTime.coerceAtLeast(0) * buckets) / dur).toInt().coerceIn(0, buckets - 1)
            sums[b] += sz.toFloat(); cnt[b]++
            if (!ex.advance()) break
        }
        ex.release()
        var mx = 0f
        for (i in 0 until buckets) { out[i] = if (cnt[i] > 0) sums[i] / cnt[i] else 0f; if (out[i] > mx) mx = out[i] }
        if (mx > 0) for (i in 0 until buckets) out[i] = (out[i] / mx).coerceIn(0.08f, 1f) else for (i in 0 until buckets) out[i] = 0.5f
    } catch (e: Exception) { for (i in 0 until buckets) out[i] = 0.5f }
    out
}

fun saveToDownloads(ctx: Context, f: File, name: String, ext: String, mime: String): Uri? {
    if (Build.VERSION.SDK_INT < 29) {
        val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Wavy"); dir.mkdirs()
        val d = File(dir, "$name.$ext"); f.copyTo(d, true); return Uri.fromFile(d)
    }
    val v = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "$name.$ext"); put(MediaStore.Downloads.MIME_TYPE, mime); put(MediaStore.Downloads.RELATIVE_PATH, "Download/Wavy")
    }
    val u = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return null
    ctx.contentResolver.openOutputStream(u)?.use { o -> f.inputStream().use { it.copyTo(o) } }
    return u
}

fun setToneAs(ctx: Context, u: Uri, type: Int, label: String) {
    try {
        if (Settings.System.canWrite(ctx)) { RingtoneManager.setActualDefaultRingtoneUri(ctx, type, u); Toast.makeText(ctx, "$label set", Toast.LENGTH_SHORT).show() }
        else { Toast.makeText(ctx, "Allow Wavy to change settings, then tap again", Toast.LENGTH_LONG).show(); ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    } catch (e: Exception) { Toast.makeText(ctx, "Could not set it. Pick it in Settings > Sound.", Toast.LENGTH_LONG).show() }
}

fun shareTone(ctx: Context, u: Uri) {
    try { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share clip").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
}

@Composable
private fun CutBtn(label: String, modifier: Modifier = Modifier, accent: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(if (accent) W.Pink.copy(alpha = 0.55f) else Color(0x1FFFFFFF)).border(1.dp, if (accent) W.Pink else Color(0x33FFFFFF), RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 13.dp, horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, style = ts(14, FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
fun RingtoneCutter(t: Track, pc: PlayerConnection?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pct by remember { mutableIntStateOf(0) }
    var err by remember { mutableStateOf<String?>(null) }
    var cut by remember { mutableStateOf<Cut?>(null) }
    var wave by remember { mutableStateOf<FloatArray?>(null) }
    var s0 by remember { mutableFloatStateOf(0f) }
    var e0 by remember { mutableFloatStateOf(1f) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Uri?>(null) }
    var dl by remember { mutableStateOf(false) }
    var mp by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var playF by remember { mutableFloatStateOf(0f) }
    var attempt by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { try { mp?.release() } catch (_: Exception) { }; try { File(ctx.cacheDir, "cut_${t.id}.bin").delete(); File(ctx.cacheDir, "cut_out.tmp").delete() } catch (_: Exception) { } } }
    // Android 8 and 9 need the storage permission to write into the public Ringtones and Downloads folders.
    val storagePerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT < 29 && androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) storagePerm.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) }
    LaunchedEffect(t.id, attempt) {
        err = null; cut = null; wave = null
        if (pc == null) { err = "Player is not ready yet. Play a song first."; return@LaunchedEffect }
        try { val f = fetchAudio(ctx, pc, t.id) { pct = it }; val c = probe(f); cut = c
            val total = c.durUs / 1e6f; s0 = 0f; e0 = minOf(30f, total) / total
            wave = waveform(f, 140)
        } catch (e: Exception) { err = "Could not get this song's audio (${e.message ?: "error"}). Check your connection and try again." }
    }
    fun fmt(us: Float): String { val s = (us / 1e6f).toInt(); return "%d:%02d".format(s / 60, s % 60) }
    fun stopPreview() { try { mp?.pause() } catch (_: Exception) { }; playing = false }
    Dialog(onDismissRequest = { stopPreview(); onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF14101A), W.Bg))).clickable(enabled = false) {}) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.arrow_back), null, Modifier.size(28.dp).clickable { stopPreview(); onClose() }, tint = Color.White)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("Ringtone cutter", style = ts(20, FontWeight.Bold))
                        Text(t.title + " - " + t.artist, style = ts(13, color = W.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(28.dp))
                val c = cut
                if (c == null) {
                    if (err != null) {
                        Text(err!!, style = ts(14, color = W.Dim))
                        CutBtn("Try again", Modifier.padding(top = 14.dp), accent = true) { attempt++ }
                    } else Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(26.dp), color = W.Pink, strokeWidth = 3.dp)
                        Text("  Getting the full song... $pct%", style = ts(14, color = W.Dim))
                    }
                } else {
                    val total = c.durUs.toFloat()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Start " + fmt(s0 * total), style = ts(14, FontWeight.SemiBold))
                        Text("Length " + fmt((e0 - s0) * total), style = ts(14, color = W.Dim))
                        Text("End " + fmt(e0 * total), style = ts(14, FontWeight.SemiBold))
                    }
                    Spacer(Modifier.height(12.dp))
                    // waveform timeline with two draggable handles and a playhead
                    Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x14FFFFFF)).border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(18.dp))
                        .pointerInput(total) {
                            var which = 0
                            detectDragGestures(
                                onDragStart = { o -> val x = (o.x / size.width).coerceIn(0f, 1f); which = if (kotlin.math.abs(x - s0) <= kotlin.math.abs(x - e0)) 0 else 1; stopPreview() }
                            ) { ch, _ ->
                                val x = (ch.position.x / size.width).coerceIn(0f, 1f); val minLen = (1_000_000f / total).coerceAtMost(0.5f)
                                if (which == 0) s0 = x.coerceAtMost(e0 - minLen).coerceAtLeast(0f) else e0 = x.coerceAtLeast(s0 + minLen).coerceAtMost(1f)
                            }
                        }) {
                        Canvas(Modifier.fillMaxSize()) {
                            val w = size.width; val h = size.height
                            val wv = wave ?: FloatArray(140) { 0.4f }
                            val bw = w / wv.size
                            wv.forEachIndexed { i, a ->
                                val xf = (i + 0.5f) / wv.size
                                val inSel = xf in s0..e0
                                val bh = h * 0.78f * a
                                drawRoundRect(if (inSel) W.Pink else Color(0x55FFFFFF), Offset(i * bw + bw * 0.2f, (h - bh) / 2f), Size(bw * 0.6f, bh), androidx.compose.ui.geometry.CornerRadius(bw * 0.3f))
                            }
                            drawRect(Color(0x66000000), Offset(0f, 0f), Size(s0 * w, h))
                            drawRect(Color(0x66000000), Offset(e0 * w, 0f), Size(w - e0 * w, h))
                            drawRect(Color.White, Offset(s0 * w - 2.dp.toPx(), 0f), Size(4.dp.toPx(), h))
                            drawRect(Color.White, Offset(e0 * w - 2.dp.toPx(), 0f), Size(4.dp.toPx(), h))
                            drawCircle(Color.White, 9.dp.toPx(), Offset(s0 * w, h / 2f)); drawCircle(Color.White, 9.dp.toPx(), Offset(e0 * w, h / 2f))
                            if (playing) drawRect(Color(0xFFFFE08A), Offset(playF * w - 1.5.dp.toPx(), 0f), Size(3.dp.toPx(), h))
                        }
                    }
                    Text("Full song is ${fmt(total)}. Drag the two white handles to pick the part. The bars show how busy the song is.", Modifier.padding(top = 8.dp), style = ts(12, color = W.Dim))
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CutBtn(if (playing) "Stop" else "Preview", Modifier.weight(1f)) {
                            if (playing) stopPreview() else scope.launch {
                                try {
                                    pc?.player?.pause()
                                    mp?.release(); val m = MediaPlayer(); m.setDataSource(c.file.absolutePath); m.prepare()
                                    m.seekTo((s0 * total / 1000).toInt()); m.start(); mp = m; playing = true
                                    val stopAt = (e0 * total / 1000).toInt()
                                    while (mp === m && m.isPlaying && m.currentPosition < stopAt) { playF = (m.currentPosition * 1000f / total).coerceIn(0f, 1f); delay(60) }
                                    if (mp === m) { m.pause(); playing = false }
                                } catch (e: Exception) { playing = false; Toast.makeText(ctx, "Preview is not available for this file", Toast.LENGTH_SHORT).show() }
                            }
                        }
                        CutBtn(if (busy) "Cutting..." else if (saved != null) "Cut again" else "Done", Modifier.weight(1f), accent = true, enabled = !busy) {
                            busy = true; stopPreview(); saved = null; dl = false
                            scope.launch {
                                try {
                                    val tmp = File(ctx.cacheDir, "cut_out.tmp")
                                    val (ext, mt) = trim(c.file, (s0 * total).toLong(), (e0 * total).toLong(), tmp)
                                    val nm = toneName(t.title)
                                    saved = withContext(Dispatchers.IO) { saveToRingtones(ctx, tmp, nm, ext, mt) }
                                    if (saved == null) err = "Could not save the clip" else err = null
                                } catch (e: Exception) { err = "Could not cut this song: ${e.message ?: "error"}" }
                                busy = false
                            }
                        }
                    }
                    if (err != null) Text(err!!, Modifier.padding(top = 12.dp), style = ts(13, color = W.Dim))
                    val u = saved
                    if (u != null) {
                        Spacer(Modifier.height(22.dp))
                        Text("Saved to your phone (Ringtones/Wavy folder).", style = ts(14, FontWeight.SemiBold))
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CutBtn("Ringtone", Modifier.weight(1f)) { setToneAs(ctx, u, RingtoneManager.TYPE_RINGTONE, "Ringtone") }
                            CutBtn("Notification", Modifier.weight(1f)) { setToneAs(ctx, u, RingtoneManager.TYPE_NOTIFICATION, "Notification sound") }
                            CutBtn("Alarm", Modifier.weight(1f)) { setToneAs(ctx, u, RingtoneManager.TYPE_ALARM, "Alarm sound") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CutBtn(if (dl) "Downloaded" else "Download", Modifier.weight(1f), enabled = !dl) {
                                scope.launch {
                                    try {
                                        val tmp = File(ctx.cacheDir, "cut_out.tmp")
                                        val ext = ctx.contentResolver.getType(u)?.let { if (it.contains("ogg")) "ogg" else if (it.contains("webm")) "webm" else "m4a" } ?: "m4a"
                                        val mt = ctx.contentResolver.getType(u) ?: "audio/mp4"
                                        val nm = toneName(t.title)
                                        val r = withContext(Dispatchers.IO) { if (tmp.exists()) saveToDownloads(ctx, tmp, nm, ext, mt) else null }
                                        if (r != null) { dl = true; Toast.makeText(ctx, "Saved in Downloads/Wavy", Toast.LENGTH_SHORT).show() } else Toast.makeText(ctx, "Could not download", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) { Toast.makeText(ctx, "Could not download", Toast.LENGTH_SHORT).show() }
                                }
                            }
                            CutBtn("Share", Modifier.weight(1f)) { shareTone(ctx, u) }
                        }
                    }
                }
            }
        }
    }
}
