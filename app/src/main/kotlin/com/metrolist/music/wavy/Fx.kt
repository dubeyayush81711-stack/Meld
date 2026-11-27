package com.metrolist.music.wavy

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState as _MS
import androidx.media3.common.Player
import com.metrolist.music.playback.PlayerConnection
import kotlin.math.sqrt

/** Reduce effects: no blur, no drifting backdrop, no moving wave. On by default for a smooth feel. */
object Reduce {
    var on by mutableStateOf(true)
    val state: MutableState<Boolean> = object : MutableState<Boolean> {
        override var value: Boolean get() = on; set(v) { on = v; S.put("reduce_fx", v) }
        override fun component1() = value
        override fun component2(): (Boolean) -> Unit = { value = it }
    }
}

/** Simple persisted settings. Keys are plain strings. */
object S {
    private var sp: android.content.SharedPreferences? = null
    fun init(ctx: Context) { if (sp == null) { sp = ctx.applicationContext.getSharedPreferences("wavy_settings", Context.MODE_PRIVATE); if (!sp!!.getBoolean("fx_v110", false)) sp!!.edit().putBoolean("reduce_fx", true).putBoolean("fx_v110", true).apply(); Reduce.on = sp!!.getBoolean("reduce_fx", true) } }
    fun keysWithPrefix(p: String): List<String> = sp?.all?.keys?.filter { it.startsWith(p) } ?: emptyList()
    fun b(k: String, d: Boolean = false) = sp?.getBoolean(k, d) ?: d
    fun f(k: String, d: Float = 0f) = sp?.getFloat(k, d) ?: d
    fun s(k: String, d: String = "") = sp?.getString(k, d) ?: d
    fun put(k: String, v: Any) { val e = sp?.edit() ?: return; when (v) { is Boolean -> e.putBoolean(k, v); is Float -> e.putFloat(k, v); is String -> e.putString(k, v) }; e.apply(); if (QUIET.none { k.startsWith(it) }) WavyFx.refresh() }
    // data caches and UI-only prefs: writing them must not reconfigure the audio chain (that was a source of stutter, and it ran on whatever thread saved)
    private val QUIET = listOf("played_hist", "feed_cache_", "home_feed_cache_", "taste_v1", "liked_", "hide_", "queue_locked", "feed_langs", "fname_", "fthumb_", "follow_", "fame_cache", "my_langs_", "mood_", "home_look", "lyric_style", "reel_vq", "party_theme_", "ui_font", "theme")
}

@Composable
fun boolPref(key: String, def: Boolean = false): MutableState<Boolean> {
    val st = remember(key) { mutableStateOf(S.b(key, def)) }
    return remember(key) { object : MutableState<Boolean> {
        override var value: Boolean get() = st.value; set(v) { st.value = v; S.put(key, v) }
        override fun component1() = value
        override fun component2(): (Boolean) -> Unit = { value = it }
    } }
}

@Composable
fun floatPref(key: String, def: Float = 0f): MutableState<Float> {
    val st = remember(key) { mutableStateOf(S.f(key, def)) }
    return remember(key) { object : MutableState<Float> {
        override var value: Float get() = st.value; set(v) { st.value = v; S.put(key, v) }
        override fun component1() = value
        override fun component2(): (Float) -> Unit = { value = it }
    } }
}

/** Audio effects, beat effects and smart controls. Everything is best effort: a device without an effect simply skips it. */
object WavyFx {
    private var ctx: Context? = null
    private var pc: PlayerConnection? = null
    private var session = 0
    private var eq: Equalizer? = null; private var bass: BassBoost? = null; private var virt: Virtualizer? = null
    private var rev: PresetReverb? = null; private var loud: LoudnessEnhancer? = null; private var vis: Visualizer? = null
    private var sensors: SensorManager? = null
    private var lastBeat = 0L; private var avg = 0f
    private var lastShake = 0L
    val beat = mutableStateOf(0L)
    val level = mutableStateOf(FloatArray(24))
    var torchOn = false

    fun attach(c: Context, p: PlayerConnection) {
        S.init(c); ctx = c.applicationContext
        if (pc === p) return
        pc = p
        p.player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) { rebuild() }
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) refresh() }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && S.b("stop_after_song")) { pc?.player?.pause(); S.put("stop_after_song", false) }
            }
        })
        val am = ctx!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                if (S.b("auto_resume", true) && added.any { it.isSink && (it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) } && S.b("paused_by_unplug"))
                    { S.put("paused_by_unplug", false); pc?.player?.play() }
                updateDevice(); refresh()
            }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { if (pc?.player?.isPlaying == true) S.put("paused_by_unplug", true); updateDevice(); refresh() }
        }, null)
        try {
            ctx!!.registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context?, i: android.content.Intent?) { updateDevice(); refresh() }
            }, android.content.IntentFilter().apply {
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED); addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED); addAction(android.media.AudioManager.ACTION_HEADSET_PLUG) })
        } catch (e: Exception) { }
        updateDevice()
        rebuild()
    }

    /** Device kind now in use: earbuds, bt_speaker, dj, wired, phone, car */
    /** Live output device (kind, name); updated by audio-device and Bluetooth connection events, so the UI reacts at once. */
    val device = mutableStateOf("phone" to "Phone speaker")
    fun updateDevice() { val d = currentDevice(); if (device.value != d) device.value = d }

    /** The connected headphone's own name. productName can be the phone's name on some devices, so the name comes from the device address. */
    private fun btName(info: AudioDeviceInfo): String {
        val c = ctx ?: return "Bluetooth headphones"
        val phone = listOf(android.os.Build.MODEL, android.os.Build.DEVICE)
        var local: String? = null
        val canRead = android.os.Build.VERSION.SDK_INT < 31 || c.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        try {
            if (canRead) {
                val ad = (c.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
                local = ad?.name
                val addr = if (android.os.Build.VERSION.SDK_INT >= 28) info.address else ""
                if (ad != null && !addr.isNullOrBlank() && android.bluetooth.BluetoothAdapter.checkBluetoothAddress(addr)) {
                    val n = ad.getRemoteDevice(addr).name
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (e: Exception) { }
        val n = info.productName?.toString().orEmpty()
        if (n.isBlank() || n.equals("Bluetooth", true) || n.equals(local, true) || phone.any { it.equals(n, true) }) return "Bluetooth headphones"
        return n
    }

    fun headphones(): Boolean = currentDevice().first.let { it == "earbuds" || it == "wired" }

    fun currentDevice(): Pair<String, String> {
        val am = ctx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "phone" to "Phone speaker"
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }?.let { return "wired" to "Wired headphones" }
        outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_LINE_ANALOG }?.let { return "wired" to "Wired / AUX" }
        outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_HEARING_AID ||
            (android.os.Build.VERSION.SDK_INT >= 31 && (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER || it.type == AudioDeviceInfo.TYPE_BLE_BROADCAST)) }
            ?.let { return "earbuds" to btName(it) }
        return "phone" to "Phone speaker"
    }

    val eqE get() = eq
    val bassE get() = bass
    val virtE get() = virt
    val loudE get() = loud
    /** Other screens reuse these instances: a second instance of the same effect on one session takes control away from the first. */
    fun ensure() { if (eq == null && pc != null) rebuild() }

    private fun rebuild() {
        val p = pc ?: return
        val id = try { p.player.audioSessionId } catch (e: Exception) { 0 }
        if (id <= 0) return
        release(); session = id
        try { eq = Equalizer(0, id) } catch (e: Exception) { }
        try { bass = BassBoost(0, id) } catch (e: Exception) { }
        try { virt = Virtualizer(0, id) } catch (e: Exception) { }
        try { rev = PresetReverb(0, id) } catch (e: Exception) { }
        try { loud = LoudnessEnhancer(id) } catch (e: Exception) { }
        refresh()
    }

    private fun release() {
        listOf<android.media.audiofx.AudioEffect?>(eq, bass, virt, rev, loud).forEach { try { it?.release() } catch (e: Exception) { } }
        stopVis(); eq = null; bass = null; virt = null; rev = null; loud = null
    }

    fun refresh() {
        val c = ctx ?: return
        val p = pc ?: return
        try { p.player.setPlaybackSpeed(S.f("speed", 1f).coerceIn(0.5f, 2f)) } catch (e: Exception) { }
        try { p.player.setHandleAudioBecomingNoisy(S.b("pause_unplug", true)) } catch (e: Exception) { }
        val (kind, _) = currentDevice()
        val auto = S.b("auto_switch", true)
        // profile values (per device kind), editable in Sound profiles
        fun prof(name: String, def: Float) = if (auto) S.f("prof_${kind}_$name", def) else def
        val dBass = 0f
        val dSpace = 0f
        val dLoud = 0f
        var bassV = prof("bass", dBass); var spaceV = prof("space", dSpace); val loudV = prof("loud", dLoud)
        if (S.b("fx_bass_boost")) bassV = maxOf(bassV, 0.8f)
        if (S.b("fx_spatial")) spaceV = maxOf(spaceV, 0.8f)
        if (kind != "earbuds" && kind != "wired") spaceV = 0f   // 3D audio only with earphones
        if (S.b("gym_mode")) bassV = 1f
        if (pocket) { bassV = maxOf(bassV, 0.6f) }
        run {
            val hp = kind == "earbuds" || kind == "wired"
            WavySpatial.night = S.b("fx_night")
            WavySpatial.gain = 1f + S.f("vol_boost", 0f).coerceIn(0f, 1f)
            WavySpatial.rotationHz = 0.05f + 0.35f * S.f("fx_8d_speed", 0.3f)
            WavySpatial.mode = if (hp && S.b("fx_8d")) 2 else if (hp && S.b("fx_spatial")) 1 else 0
        }
        try { bass?.let { it.setStrength((bassV * 1000).toInt().toShort()); it.enabled = bassV > 0.01f } } catch (e: Exception) { }
        try { virt?.let {
            if (it.strengthSupported) it.setStrength((spaceV * 1000).toInt().toShort())
            if (android.os.Build.VERSION.SDK_INT >= 28 && spaceV > 0.01f) { try {
                val mode = Virtualizer.VIRTUALIZATION_MODE_BINAURAL
                if (it.canVirtualize(android.media.AudioFormat.CHANNEL_OUT_STEREO, mode)) it.forceVirtualizationMode(mode)
            } catch (e: Exception) { } }
            it.enabled = spaceV > 0.01f } } catch (e: Exception) { }
        try { loud?.let { it.setTargetGain(((loudV + (if (pocket && !S.b("fx_night")) 0.2f else 0f) + (if (S.b("gym_mode")) 0.5f else 0f)) * 1000).toInt() ); it.enabled = it.targetGain > 0 } } catch (e: Exception) { }
        try {
            rev?.let { val on = S.b("fx_reverb"); it.preset = if (on) PresetReverb.PRESET_LARGEHALL else PresetReverb.PRESET_NONE; it.enabled = on }
        } catch (e: Exception) { }
        try {
            eq?.let { e ->
                val n = e.numberOfBands.toInt(); val r = e.bandLevelRange; val lo = r[0]; val hi = r[1]
                val treble = if (S.b("fx_knobs")) S.f("treble", 0.5f) else 0.5f
                var nonFlat = false
                for (i in 0 until n) {
                    var g = S.f("eq_$i", 0.5f)                       // 0..1, 0.5 flat
                    if (S.b("fx_knobs") && i >= n / 2) g += (treble - 0.5f)
                    if (S.b("fx_voice") && i in (n / 3)..(2 * n / 3)) g += 0.15f
                    if (kotlin.math.abs(g - 0.5f) > 0.005f) nonFlat = true
                    e.setBandLevel(i.toShort(), (lo + (hi - lo) * g.coerceIn(0f, 1f)).toInt().toShort())
                }
                e.enabled = nonFlat
            }
        } catch (e: Exception) { }
        setupVis(); setupSensors(c)
        try { if (!S.b("fx_flash") && torchOn) torch(false) } catch (e: Exception) { }
    }

    fun eqBands(): List<Pair<Int, String>> {
        val e = eq ?: return emptyList()
        return (0 until e.numberOfBands.toInt()).map { i -> val hz = e.getCenterFreq(i.toShort()) / 1000; i to (if (hz >= 1000) "${hz / 1000}k" else "$hz") }
    }

    // ---- beat effects and visualizer ----
    private fun wantVis() = S.b("fx_flash") || S.b("fx_vibrate") || S.b("fx_strobe") || S.b("fx_visualizer", true)
    private fun stopVis() { try { vis?.enabled = false; vis?.release() } catch (e: Exception) { }; vis = null }
    private fun setupVis() {
        val c = ctx ?: return
        if (!wantVis()) { stopVis(); return }
        if (c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        if (vis != null || session <= 0) return
        try {
            val v = Visualizer(session); v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vz: Visualizer?, w: ByteArray?, r: Int) {}
                override fun onFftDataCapture(vz: Visualizer?, fft: ByteArray?, r: Int) { fft?.let { onFft(it) } }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            v.enabled = true; vis = v
        } catch (e: Exception) { vis = null }
    }

    private fun onFft(f: ByteArray) {
        val bands = 24; val out = FloatArray(bands); val n = f.size / 2
        for (b in 0 until bands) {
            val s = (n * (b / bands.toFloat()) * (b / bands.toFloat())).toInt().coerceAtLeast(1); val e = (n * ((b + 1) / bands.toFloat()) * ((b + 1) / bands.toFloat())).toInt().coerceAtLeast(s + 1).coerceAtMost(n - 1)
            var m = 0f; for (k in s..e) { val re = f[2 * k].toFloat(); val im = f[2 * k + 1].toFloat(); m += sqrt(re * re + im * im) }
            out[b] = (m / (e - s + 1) / 90f).coerceIn(0f, 1f)
        }
        level.value = out
        val bassE = (out[0] + out[1] + out[2]) / 3f
        avg = avg * 0.92f + bassE * 0.08f
        val now = System.currentTimeMillis()
        if (bassE > avg * 1.35f + 0.08f && now - lastBeat > 220) {
            lastBeat = now; beat.value = now
            if (S.b("fx_vibrate")) vibrate()
            if (S.b("fx_flash")) { torch(true); android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ torch(false) }, 90) }
        }
    }

    private fun vibrate() {
        try {
            val v = ctx?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") v.vibrate(40)
        } catch (e: Exception) { }
    }

    private var flashId: String? = null
    private fun torch(on: Boolean) {
        try {
            val cm = ctx?.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            // The flash camera id is looked up once, not on every beat.
            val id = flashId ?: cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }?.also { flashId = it } ?: return
            cm.setTorchMode(id, on); torchOn = on
        } catch (e: Exception) { }
    }

    // ---- sensors: pocket mode and shake to skip ----
    private var pocket = false
    private var sensorsOn = false
    private val sl = object : SensorEventListener {
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        override fun onSensorChanged(e: SensorEvent) {
            if (e.sensor.type == Sensor.TYPE_PROXIMITY) {
                val near = e.values[0] < (e.sensor.maximumRange.coerceAtMost(5f)).coerceAtLeast(1f) && e.values[0] < 3f
                if (near != pocket && S.b("pocket", false)) { pocket = near; refresh() }
            } else if (e.sensor.type == Sensor.TYPE_ACCELEROMETER && S.b("shake")) {
                val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / 9.81f
                val now = System.currentTimeMillis()
                if (g > 2.6f && now - lastShake > 1200) { lastShake = now; pc?.seekToNext() }
            }
        }
    }
    private fun setupSensors(c: Context) {
        val want = S.b("pocket", false) || S.b("shake")
        val sm = sensors ?: (c.getSystemService(Context.SENSOR_SERVICE) as SensorManager).also { sensors = it }
        if (want && !sensorsOn) {
            sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { sm.registerListener(sl, it, SensorManager.SENSOR_DELAY_NORMAL) }
            sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(sl, it, SensorManager.SENSOR_DELAY_GAME) }
            sensorsOn = true
        } else if (!want && sensorsOn) { sm.unregisterListener(sl); sensorsOn = false; pocket = false }
    }
}
