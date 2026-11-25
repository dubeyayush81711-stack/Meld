package com.metrolist.music.wavy

import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.offline.Download
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection

/** In-app audio effects (Android AudioEffect API). Honest limit: vendor effects like vivo DeepField cannot be driven by an app. */
object WEq {
    var eq: Equalizer? = null; var bb: BassBoost? = null; var vz: Virtualizer? = null
    var sid = -1
    var bands = FloatArray(0)
    fun bind(pc: PlayerConnection): String? {
        val id = pc.player.audioSessionId
        if (id <= 0) return "Play a song first."
        if (sid != id || eq == null) {
            try { eq?.release(); bb?.release(); vz?.release() } catch (_: Exception) {}
            eq = null; bb = null; vz = null; sid = id
            try { eq = Equalizer(0, id).also { it.enabled = true } } catch (_: Exception) {}
            try { bb = BassBoost(0, id).also { it.enabled = true } } catch (_: Exception) {}
            try { vz = Virtualizer(0, id).also { it.enabled = true } } catch (_: Exception) {}
            bands = FloatArray(eq?.numberOfBands?.toInt() ?: 0) { i -> try { eq!!.getBandLevel(i.toShort()).toFloat() } catch (_: Exception) { 0f } }
        }
        return if (eq == null) "This phone does not allow the in-app equalizer." else null
    }
}

@Composable
private fun OptRow(icon: Int, title: String, sub: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).glass(RoundedCornerShape(18.dp), 0.8f).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = W.Pink)
        Column(Modifier.padding(start = 18.dp)) {
            Text(title, style = ts(17, FontWeight.SemiBold))
            if (sub != null) Text(sub, style = ts(13, color = Color(0xFFC9C1D3)))
        }
    }
}

@Composable
fun PlayerOptions(pc: PlayerConnection, onBoost: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val meta by pc.mediaMetadata.collectAsState()
    val m = meta ?: return
    val t = Track(m.id, m.title, m.artists.joinToString { it.name }, m.thumbnailUrl, m.duration, m.artists.firstOrNull()?.id, null)
    val dls by (WDl.util?.downloads ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())).collectAsState()
    val dst = dls[t.id]?.state
    var sub by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.78f).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(GlassSheet).clickable(enabled = false) {}.padding(top = 10.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 44.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF)))
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(t.cover, 56.dp, 10)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(t.title, style = ts(18, FontWeight.SemiBold), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(t.artist, style = ts(14, color = Color(0xFFC9C1D3)), maxLines = 1)
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
                val done = dst == Download.STATE_COMPLETED
                val busy = dst != null && dst != Download.STATE_FAILED && !done
                OptRow(R.drawable.download, if (busy) "Downloading..." else if (done) "Downloaded" else "Download", "Saved in app storage, plays offline") {
                    if (done || busy) Toast.makeText(ctx, if (done) "Already downloaded" else "Still downloading", Toast.LENGTH_SHORT).show()
                    else { WDl.start(t); Toast.makeText(ctx, "Downloading...", Toast.LENGTH_SHORT).show() }
                }
                OptRow(R.drawable.offline, "Remove download", if (done) "Delete the offline copy" else "Not downloaded") {
                    if (dst != null) { WDl.remove(ctx, t.id); Toast.makeText(ctx, "Removed from Downloads", Toast.LENGTH_SHORT).show() }
                    else Toast.makeText(ctx, "This song is not downloaded", Toast.LENGTH_SHORT).show()
                }
                var three by remember { mutableStateOf(false) }
                OptRow(R.drawable.graphic_eq, if (three) "3D music: on" else "3D music", "Surround effect, best with headphones") {
                    val e = WEq.bind(pc)
                    if (e != null && WEq.vz == null) Toast.makeText(ctx, "3D effect is not supported on this phone", Toast.LENGTH_SHORT).show()
                    else { three = !three; try { WEq.vz?.setStrength((if (three) 1000 else 0).toShort()) } catch (_: Exception) {} }
                }
                OptRow(R.drawable.radio, "Start radio", "Similar songs, same style") { playRadio(pc, t); onClose() }
                OptRow(R.drawable.link, "Copy link") {
                    (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("link", "https://music.youtube.com/watch?v=${t.id}"))
                    Toast.makeText(ctx, "Link copied", Toast.LENGTH_SHORT).show()
                }
                OptRow(R.drawable.equalizer, "Equalizer", "Bands, bass, surround, clear voice") { sub = "eq" }
                OptRow(R.drawable.graphic_eq, "System equalizer", "Open Android's built-in equalizer") {
                    try {
                        ctx.startActivity(Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, pc.player.audioSessionId).putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC))
                    } catch (_: Exception) { Toast.makeText(ctx, "No system equalizer on this phone", Toast.LENGTH_SHORT).show() }
                }
                OptRow(R.drawable.speed, "Tempo and pitch", "Speed and key of the song") { sub = "tempo" }
                OptRow(R.drawable.tune, "Volume booster and effects", "Booster, flashlight, vibrate, strobe") { onBoost() }
            }
        }
        if (sub == "tempo") TempoDialog(pc) { sub = "" }
        if (sub == "eq") EqSheet(pc) { sub = "" }
    }
}

@Composable
private fun TempoDialog(pc: PlayerConnection, onClose: () -> Unit) {
    var speed by remember { mutableFloatStateOf(pc.player.playbackParameters.speed) }
    var pitch by remember { mutableFloatStateOf(pc.player.playbackParameters.pitch) }
    fun go() { pc.player.playbackParameters = PlaybackParameters(speed, pitch) }
    Box(Modifier.fillMaxSize().background(Color(0x88000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(GlassSheet).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(26.dp)).clickable(enabled = false) {}.padding(22.dp)) {
            Text("Tempo and pitch", style = ts(22, FontWeight.SemiBold))
            Spacer(Modifier.height(18.dp))
            Text("Speed  x${"%.2f".format(speed)}", style = ts(15, color = Color(0xFFD6CCD3)))
            Slider(speed, { speed = it; go() }, valueRange = 0.5f..2f, colors = SliderDefaults.colors(thumbColor = W.Pink, activeTrackColor = W.Pink))
            Text("Pitch  x${"%.2f".format(pitch)}", style = ts(15, color = Color(0xFFD6CCD3)))
            Slider(pitch, { pitch = it; go() }, valueRange = 0.5f..2f, colors = SliderDefaults.colors(thumbColor = W.Pink, activeTrackColor = W.Pink))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                Text("Reset", Modifier.clip(RoundedCornerShape(50)).clickable { speed = 1f; pitch = 1f; go() }.padding(16.dp, 10.dp), style = ts(15, FontWeight.Medium, W.Pink))
                Text("OK", Modifier.clip(RoundedCornerShape(50)).background(W.Pink).clickable(onClick = onClose).padding(22.dp, 10.dp), style = ts(15, FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun EqSheet(pc: PlayerConnection, onClose: () -> Unit) {
    val err = remember { WEq.bind(pc) }
    var bands by remember { mutableStateOf(WEq.bands.copyOf()) }
    var bass by remember { mutableFloatStateOf(0f) }
    var surround by remember { mutableFloatStateOf(0f) }
    var boost by floatPref("vol_boost", 0f)
    val eq = WEq.eq
    val range = try { eq!!.bandLevelRange.let { it[0].toFloat()..it[1].toFloat() } } catch (_: Exception) { -1500f..1500f }
    fun setBand(i: Int, v: Float) { bands = bands.copyOf().also { it[i] = v }; try { eq?.setBandLevel(i.toShort(), v.toInt().toShort()) } catch (_: Exception) {} }
    fun preset(name: String) {
        when (name) {
            "Flat" -> { bass = 0f; surround = 0f; bands.indices.forEach { setBand(it, 0f) } }
            "Mega bass" -> { bass = 0.9f; bands.indices.forEach { setBand(it, if (it < bands.size / 2) range.endInclusive * 0.6f else 0f) } }
            "Panoramic surround" -> { surround = 0.9f }
            "Clear voice" -> { bands.indices.forEach { i -> val mid = i in (bands.size / 3)..(bands.size * 2 / 3); setBand(i, if (mid) range.endInclusive * 0.5f else if (i == 0) range.start * 0.4f else 0f) } }
        }
        try { WEq.bb?.setStrength((bass * 1000).toInt().toShort()) } catch (_: Exception) {}
        try { WEq.vz?.setStrength((surround * 1000).toInt().toShort()) } catch (_: Exception) {}
    }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onClose)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.85f).clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(GlassSheet).clickable(enabled = false) {}.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(22.dp)) {
            Text("Equalizer", style = ts(24, FontWeight.SemiBold))
            if (err != null) Text(err, Modifier.padding(top = 10.dp), style = ts(14, color = Color(0xFFFFB3D1)))
            Text("Effects", Modifier.padding(top = 16.dp, bottom = 8.dp), style = ts(15, color = Color(0xFFC9C1D3)))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Flat", "Mega bass", "Panoramic surround", "Clear voice").forEach { n ->
                    Text(n, Modifier.height(40.dp).glass(RoundedCornerShape(50)).clickable { preset(n) }.padding(horizontal = 18.dp).wrapContentHeight(), style = ts(14, FontWeight.Medium), maxLines = 1, softWrap = false)
                }
            }
            Text("Bass boost", Modifier.padding(top = 16.dp), style = ts(15))
            Slider(bass, { bass = it; try { WEq.bb?.setStrength((it * 1000).toInt().toShort()) } catch (_: Exception) {} }, colors = SliderDefaults.colors(thumbColor = W.Pink, activeTrackColor = W.Pink))
            Text("Surround (virtualizer)", style = ts(15))
            Slider(surround, { surround = it; try { WEq.vz?.setStrength((it * 1000).toInt().toShort()) } catch (_: Exception) {} }, colors = SliderDefaults.colors(thumbColor = W.Pink, activeTrackColor = W.Pink))
            Text("Loudness", style = ts(15))
            Slider(boost, { boost = it; Booster.apply(pc, it) }, colors = SliderDefaults.colors(thumbColor = W.Pink, activeTrackColor = W.Pink))
            Text("Bands", Modifier.padding(top = 8.dp), style = ts(15, color = Color(0xFFC9C1D3)))
            bands.indices.forEach { i ->
                val hz = try { eq!!.getCenterFreq(i.toShort()) / 1000 } catch (_: Exception) { 0 }
                Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", style = ts(13, color = Color(0xFFC9C1D3)))
                Slider(bands[i], { setBand(i, it) }, valueRange = range, colors = SliderDefaults.colors(thumbColor = W.Pink, activeTrackColor = W.Pink))
            }
            Text("Vendor effects such as vivo DeepField are built into the phone and cannot be controlled by an app. Use the System equalizer entry for those.", Modifier.padding(top = 10.dp), style = ts(12, color = Color(0xFF9A93A6)))
        }
    }
}
