package com.metrolist.music.wavy

import android.os.StatFs
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
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
import com.metrolist.music.R
import com.metrolist.music.constants.*
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference

@Composable
fun SPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
        Box(Modifier.fillMaxWidth().background(Color(0xFF1A1A1D))) { BackBar(title, onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 190.dp), content = content)
    }
}

@Composable fun SSection(t: String) = Text(t, Modifier.padding(start = 20.dp, top = 26.dp, bottom = 6.dp), style = ts(24, FontWeight.Bold))
@Composable fun SNote(t: String) = Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) { Text("\u24D8  ", style = ts(13, color = W.Dim)); Text(t, style = ts(13, color = W.Dim)) }
@Composable fun SDivider() = Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Color(0x22FFFFFF)))

@Composable
fun SToggle(title: String, desc: String, state: MutableState<Boolean>) {
    Row(Modifier.fillMaxWidth().clickable { state.value = !state.value }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) { Text(title, style = ts(17, FontWeight.Medium)); Text(desc, style = ts(13, color = Color(0xFFB9B2C4))) }
        Switch2(state.value)
    }
}

@Composable
fun Switch2(on: Boolean) {
    val x by androidx.compose.animation.core.animateDpAsState(if (on) 22.dp else 2.dp, label = "sw")
    Box(Modifier.width(54.dp).height(32.dp).clip(CircleShape).background(if (on) W.Grad else androidx.compose.ui.graphics.SolidColor(Color(0xFF444448)), CircleShape)) {
        Box(Modifier.offset(x = x, y = 2.dp).size(28.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun SRadio(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = ts(17, FontWeight.Medium)); if (desc.isNotEmpty()) Text(desc, style = ts(13, color = Color(0xFFB9B2C4))) }
        Box(Modifier.size(28.dp).border(2.dp, if (selected) W.Pink else Color(0xFF8A8A90), CircleShape), contentAlignment = Alignment.Center) { if (selected) Box(Modifier.size(14.dp).clip(CircleShape).background(W.Pink)) }
    }
}

@Composable
fun SNav(title: String, desc: String = "", value: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = ts(17, FontWeight.Medium)); if (desc.isNotEmpty()) Text(desc, style = ts(13, color = Color(0xFFB9B2C4))) }
        if (value != null) Text(value, style = ts(14, FontWeight.Medium, W.Pink))
    }
}

@Composable
fun SSlider(title: String, v: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(title, style = ts(15, FontWeight.Medium))
        Slider(v, onChange, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = W.Pink, inactiveTrackColor = Color(0xFF444448)))
    }
}

@Composable
fun SettingsHub(onBack: () -> Unit, go: (String) -> Unit) {
    SPage("Settings", onBack) {
        Spacer(Modifier.height(10.dp))
        SNav("Content and display", "Videos, languages, explicit content and display") { go("set:content") }
        SNav("Playback", "Audio quality, gapless playback, autoplay") { go("set:playback") }
        SNav("Data-saving and offline", "Data saver, storage and cache") { go("set:data") }
        SNav("Sound profiles", "Earbuds, speaker and car detection") { go("set:sound") }
        SNav("Pro audio", "Equalizer, spatial audio, beat effects") { go("set:pro") }
        SNav("Advanced options", "Sleep timer, speed, pocket mode, smart controls") { go("set:adv") }
        SNav("Apps and devices", "Local files and car") { go("set:apps") }
        SNav("Notifications", "Choose what Wavy tells you") { go("set:notif") }
        SNav("About and support", "Version and licenses") { go("set:about") }
    }
}

@Composable
fun ContentSettings(onBack: () -> Unit) {
    var explicit by rememberPreference(HideExplicitKey, false)
    SPage("Content and display", onBack) {
        SSection("Videos and Canvas")
        SNote("It may take some time for your experience to update.")
        SToggle("Canvas", "Short, looping visuals when a song is playing.", boolPref("canvas", true))
        SToggle("Song videos", "Video mode in the player and video loops on the home feed.", boolPref("song_videos", true))
        SNote("When off, Music tab plays audio only.")
        SDivider()
        SSection("Content preferences")
        SNav("Languages for music", "Set your preferred languages for music recommendations.") { }
        SToggle("Allow explicit content", "Explicit content (labeled with the E tag) is playable. When off, explicit songs are skipped.", object : MutableState<Boolean> {
            override var value: Boolean get() = !explicit; set(v) { explicit = !v }
            override fun component1() = value; override fun component2(): (Boolean) -> Unit = { value = it } })
        SNote("It may take some time for your experience to update.")
        SToggle("Include kids and family music in taste profile", "Turning this off will exclude kids and family music from your taste profile, which influences your recommendations.", boolPref("kids", true))
        SToggle("Show unplayable songs", "Songs that aren't available (e.g., due to artist removal or region) are still visible.", boolPref("unplayable", false))
        SDivider()
        SSection("Display preferences")
        SToggle("Reduce animations", "Disables various autoplaying animations that can be distracting.", boolPref("reduce_anim", false))
    }
}

@Composable
fun PlaybackSettings(onBack: () -> Unit) {
    var q by rememberEnumPreference(AudioQualityKey, AudioQuality.AUTO)
    var auto by rememberPreference(AutoLoadMoreKey, true)
    var gap by rememberPreference(CrossfadeGaplessKey, true)
    SPage("Playback", onBack) {
        SSection("Audio quality")
        SRadio("Automatic", "Adjusts to your connection and data saver.", q == AudioQuality.AUTO) { q = AudioQuality.AUTO }
        SRadio("Low", "Saves the most data.", q == AudioQuality.LOW) { q = AudioQuality.LOW }
        SRadio("High", "Best quality the song has.", q == AudioQuality.HIGH) { q = AudioQuality.HIGH }
        SDivider()
        SSection("Track transitions")
        SToggle("Gapless playback", "Removes any gaps or pauses that may occur in between tracks.", object : MutableState<Boolean> {
            override var value: Boolean get() = gap; set(v) { gap = v }; override fun component1() = value; override fun component2(): (Boolean) -> Unit = { value = it } })
        SDivider()
        SSection("Listening controls")
        SToggle("Autoplay", "Similar songs will play when what you're listening to ends.", object : MutableState<Boolean> {
            override var value: Boolean get() = auto; set(v) { auto = v }; override fun component1() = value; override fun component2(): (Boolean) -> Unit = { value = it } })
        SToggle("Mono audio", "Left and right speakers play the same audio.", boolPref("mono", false))
        SDivider()
        SSection("Video controls")
        SToggle("Picture in picture", "Shrink video in a mini player when you leave Wavy, so you can continue watching while using apps.", boolPref("pip", false))
    }
}

@Composable
fun DataSettings(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(S.s("datasaver", "auto")) }
    var cacheMb by rememberPreference(MaxSongCacheSizeKey, 1024)
    var cleared by remember { mutableIntStateOf(0) }
    val cacheBytes = remember(cleared) { ctx.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } + (ctx.externalCacheDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L) }
    val st = remember { StatFs(android.os.Environment.getDataDirectory().path) }
    val total = st.totalBytes; val free = st.availableBytes
    fun gb(b: Long) = if (b > 1_000_000_000) "%.1f GB".format(b / 1e9) else "%.1f MB".format(b / 1e6)
    SPage("Data-saving and offline", onBack) {
        SSection("Data saver mode")
        SNote("\"On\" lowers streaming quality and disables other features that use a lot of data, like video previews.")
        listOf("on" to "Always on", "auto" to "Automatic", "off" to "Always off").forEach { (k, n) ->
            SRadio(n, if (k == "auto") "Adjusts based on Android's Data Saver setting or when your connection slows." else "", mode == k) { mode = k; S.put("datasaver", k) } }
        SDivider()
        SSection("Downloads and streaming")
        SToggle("Downloads over cellular", "Downloads start or continue when you're not connected to Wi-Fi.", boolPref("dl_cell", false))
        SDivider()
        SSection("Storage")
        Text("Storage breakdown", Modifier.padding(horizontal = 20.dp), style = ts(17, FontWeight.Medium))
        Spacer(Modifier.height(10.dp))
        val used = (total - free - cacheBytes).coerceAtLeast(0)
        Row(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF444448))) {
            Box(Modifier.weight((used.toFloat() / total).coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFF3F9CF0)))
            Box(Modifier.weight((cacheBytes.toFloat() / total).coerceAtLeast(0.005f)).fillMaxHeight().background(W.Pink))
            Box(Modifier.weight((free.toFloat() / total).coerceAtLeast(0.01f)).fillMaxHeight())
        }
        Spacer(Modifier.height(12.dp))
        listOf("Other apps" to gb(used), "Wavy cache" to gb(cacheBytes), "Free" to gb(free)).forEach { (n, v) ->
            Row(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) { Text(n, Modifier.weight(1f), style = ts(15)); Text(v, style = ts(15, color = Color(0xFFB9B2C4))) } }
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Clear cache", style = ts(17, FontWeight.Medium)); Text("Removes cached images and temporary files to free up space.", style = ts(13, color = Color(0xFFB9B2C4))) }
            Text("Clear", Modifier.border(1.dp, Color.White, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).clickable {
                ctx.cacheDir.listFiles()?.forEach { if (it.name != "exoplayer") it.deleteRecursively() }; cleared++ }.padding(horizontal = 20.dp, vertical = 8.dp), style = ts(14, FontWeight.SemiBold))
        }
        Text("Cache limit", Modifier.padding(horizontal = 20.dp), style = ts(17, FontWeight.Medium))
        Text("Manage how much space Wavy uses for cached songs: ${cacheMb} MB", Modifier.padding(horizontal = 20.dp), style = ts(13, color = Color(0xFFB9B2C4)))
        Slider(cacheMb / 5000f, { cacheMb = (it * 5000).toInt().coerceIn(100, 5000) }, Modifier.padding(horizontal = 20.dp), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = W.Pink, inactiveTrackColor = Color(0xFF444448)))
        SNote("A new cache limit applies the next time you open Wavy.")
    }
}

@Composable
fun NotifSettings(onBack: () -> Unit) {
    SPage("Notifications", onBack) {
        Text("Choose which push notifications you'd like to get.", Modifier.padding(20.dp), style = ts(15, color = Color(0xFFB9B2C4)))
        SToggle("Music and artists", "Push", boolPref("n_music", true))
        SToggle("Friends and social", "Push", boolPref("n_social", true))
        SToggle("New songs from followed artists", "Push", boolPref("n_new", true))
    }
}

@Composable
fun AboutSettings(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "1.0.0" } }
    SPage("About and support", onBack) {
        Row(Modifier.padding(20.dp, 28.dp)) { Text("Version", Modifier.weight(1f), style = ts(18, FontWeight.Medium)); Text(ver ?: "", style = ts(15, color = Color(0xFFB9B2C4))) }
        SDivider()
        SNav("Third-party licenses", "Wavy is built on open-source software (GPL-3.0).") { }
        SNav("Support", "Ask the person who invited you.") { }
    }
}

@Composable
fun AppsSettings(onBack: () -> Unit) {
    SPage("Apps and devices", onBack) {
        SSection("Device management")
        SToggle("Local audio files", "Lets you add tracks from this device to Your Library.", boolPref("local_files", false))
        SDivider()
        SSection("In the car")
        SNote("Always pay full attention to the road and abide by all traffic regulations.")
        SToggle("Keep Wavy open", "Prevents your phone from going to sleep whenever you're controlling Wavy in the car.", boolPref("keep_open", true))
    }
}

@Composable
fun AdvancedSettings(pc: PlayerConnection?, onBack: () -> Unit) {
    var xf by rememberPreference(CrossfadeEnabledKey, false)
    var skip by rememberPreference(SkipSilenceKey, false)
    var speed by floatPref("speed", 1f)
    var sleepOpen by remember { mutableStateOf(false) }
    var sleepLabel by remember { mutableStateOf("Off") }
    SPage("Advanced options", onBack) {
        SSection("Listening")
        SNav("Sleep timer", "Music stops automatically.", sleepLabel) { sleepOpen = !sleepOpen }
        if (sleepOpen) Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 15, 30, 45, 60).forEach { m ->
                Text(if (m == 0) "Off" else "$m min", Modifier.clip(RoundedCornerShape(50)).background(if (sleepLabel == (if (m == 0) "Off" else "$m min")) W.Pink else Color(0xFF2A2A2E)).clickable {
                    sleepLabel = if (m == 0) "Off" else "$m min"
                    try { if (m == 0) pc?.service?.sleepTimer?.clear() else pc?.service?.sleepTimer?.start(m) } catch (e: Exception) { }
                }.padding(horizontal = 14.dp, vertical = 8.dp), style = ts(13, FontWeight.Medium)) }
        }
        SNav("Playback speed", "From 0.5x to 2x.", "%.1fx".format(speed)) { }
        SSlider("Speed", (speed - 0.5f) / 1.5f) { speed = (0.5f + it * 1.5f).let { s -> (s * 10).toInt() / 10f } }
        SToggle("Crossfade", "Songs blend smoothly into each other.", object : MutableState<Boolean> {
            override var value: Boolean get() = xf; set(v) { xf = v }; override fun component1() = value; override fun component2(): (Boolean) -> Unit = { value = it } })
        SToggle("Skip silence", "Skips silent parts in songs.", object : MutableState<Boolean> {
            override var value: Boolean get() = skip; set(v) { skip = v }; override fun component1() = value; override fun component2(): (Boolean) -> Unit = { value = it } })
        SDivider()
        SSection("Sound")
        SToggle("Bass boost", "Adds extra power to the bass.", boolPref("fx_bass_boost", false))
        SToggle("Pocket Mode", "Auto boosts bass and volume when in your pocket.", boolPref("pocket", true))
        SDivider()
        SSection("Smart controls")
        SToggle("Shake to skip", "Shake your phone for the next song.", boolPref("shake", false))
        SToggle("Auto-resume on headphones", "Music resumes when headphones connect.", boolPref("auto_resume", true))
        SToggle("Pause when headphones unplug", "Pauses when headphones are unplugged.", boolPref("pause_unplug", true))
        SToggle("Stop after current song", "Stops after the current song ends.", boolPref("stop_after_song", false))
    }
}

@Composable
fun ProSettings(onBack: () -> Unit, askMic: () -> Unit) {
    val knobs = boolPref("fx_knobs", false)
    var eqOpen by remember { mutableStateOf(false) }
    SPage("Pro audio", onBack) {
        SSection("Sound lab")
        SNav("10-band Equalizer", "Adjust every frequency yourself.") { eqOpen = !eqOpen }
        if (eqOpen) {
            val bands = remember { WavyFx.eqBands() }
            if (bands.isEmpty()) SNote("Start a song first so the equalizer can attach.")
            bands.forEach { (i, label) -> var v by floatPref("eq_$i", 0.5f); SSlider("$label Hz", v) { v = it } }
        }
        SToggle("Spatial / 3D audio", "Sound feels like it comes from all around.", boolPref("fx_spatial", false))
        SToggle("Reverb", "Hall preset.", boolPref("fx_reverb", false))
        SToggle("Bass + Treble knobs", "Separate sliders.", knobs)
        if (knobs.value) { var tr by floatPref("treble", 0.5f); var bs by floatPref("prof_bass_manual", 0.3f); SSlider("Treble", tr) { tr = it }
            SSlider("Bass", S.f("bassknob", 0.3f)) { S.put("bassknob", it); S.put("fx_bass_boost", it > 0.5f) } }
        SToggle("Night mode", "Evens out loud and quiet parts. For late night.", boolPref("fx_night", false))
        SToggle("Voice boost", "Clearer, louder vocals.", boolPref("fx_voice", false))
        SDivider()
        SSection("Beat effects")
        SNote("Beat effects listen to the song's own sound. Android asks for the microphone permission for that, nothing is recorded.")
        val flash = boolPref("fx_flash", false); val vib = boolPref("fx_vibrate", false); val strobe = boolPref("fx_strobe", false)
        SToggle("Beat-sync flashlight", "Flashlight flashes on the beat.", flash)
        SToggle("Beat-sync vibrate", "Phone vibrates on the beat.", vib)
        SToggle("Color Strobe", "Screen colors change with the beat.", strobe)
        SToggle("Live visualizer", "Waves and bars in the player.", boolPref("fx_visualizer", true))
        if (flash.value || vib.value || strobe.value) askMic()
        SDivider()
        SSection("Smart modes")
        SToggle("Gym mode", "Loud, bass heavy, screen stays on.", boolPref("gym_mode", false))
    }
}

@Composable
fun SoundProfiles(onBack: () -> Unit) {
    val (kind, dev) = remember { WavyFx.currentDevice() }
    var edit by remember { mutableStateOf<String?>(null) }
    val profiles = listOf("earbuds" to ("Earphones / Earbuds" to "Clear vocals, safe volume limit, spatial feel"), "wired" to ("Wired / AUX" to "Flat studio sound, no delay"), "phone" to ("Phone speaker" to "Loudness + voice boost"))
    SPage("Sound profiles", onBack) {
        Text("When earbuds, headphones or speakers connect, Wavy detects them and adjusts the sound automatically.", Modifier.padding(20.dp), style = ts(14, color = Color(0xFFB9B2C4)))
        SSection("Now connected")
        Row(Modifier.padding(20.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(dev, style = ts(17, FontWeight.Medium)); Text("${profiles.first { it.first == kind }.second.first} profile ON", style = ts(13, color = Color(0xFFB9B2C4))) }
            Text("Active", style = ts(14, FontWeight.Medium, W.Pink)) }
        SDivider()
        SSection("Auto sound profiles")
        profiles.forEach { (k, d) ->
            Row(Modifier.padding(20.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(d.first, style = ts(17, FontWeight.Medium)); Text(d.second, style = ts(13, color = Color(0xFFB9B2C4))) }
                Text("Edit", Modifier.clickable { edit = if (edit == k) null else k }, style = ts(14, FontWeight.Medium, W.Pink)) }
            if (edit == k) {
                var b by floatPref("prof_${k}_bass", 0.2f); var sp by floatPref("prof_${k}_space", 0f); var l by floatPref("prof_${k}_loud", 0f)
                SSlider("Bass", b) { b = it }; SSlider("Spatial", sp) { sp = it }; SSlider("Loudness", l) { l = it }
            }
        }
        SDivider()
        SSection("Smart options")
        SToggle("Auto-switch profile", "The profile switches automatically when the device changes.", boolPref("auto_switch", true))
        SToggle("Remember volume per device", "Each device keeps its own volume.", boolPref("vol_per_device", true))
        SToggle("Hearing safety alert", "Alert when earphone volume is too high.", boolPref("hearing_alert", true))
    }
}
