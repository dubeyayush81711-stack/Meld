package com.metrolist.music.wavy

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.MusicService
import com.metrolist.music.playback.MusicService.MusicBinder
import com.metrolist.music.playback.PlayerConnection
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WavyActivity : ComponentActivity() {
    @Inject lateinit var database: MusicDatabase
    @Inject lateinit var downloadUtil: com.metrolist.music.playback.DownloadUtil

    private var connection: PlayerConnection? = null
    private var pc by mutableStateOf<PlayerConnection?>(null)
    private var bound = false

    private val sc = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is MusicBinder) {
                connection = PlayerConnection(this@WavyActivity, service, database, lifecycleScope)
                pc = connection
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) { connection?.dispose() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Api.init(this); S.init(this); ErrorLog.init(this); handleLink(intent)
        WDl.util = downloadUtil; WDl.db = database
        setContent { val d = androidx.compose.ui.platform.LocalDensity.current; androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, minOf(d.fontScale, 1f))) { androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(primary = Color(0xFFFF2D87), surface = Color(0xFF14111A), background = Color(0xFF0B0A10), surfaceVariant = Color(0xFF1E1A26), surfaceContainer = Color(0xFF1A1620), surfaceContainerHigh = Color(0xFF1E1A26), surfaceContainerHighest = Color(0xFF241F2D), surfaceContainerLow = Color(0xFF14111A), surfaceContainerLowest = Color(0xFF0B0A10), onSurface = Color.White, onSurfaceVariant = Color(0xFFB9B2C4), onBackground = Color.White, outline = Color(0x33FFFFFF), outlineVariant = Color(0x22FFFFFF)), shapes = androidx.compose.material3.Shapes(extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp))) { WavyApp(pc) } } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleLink(intent) }

    private fun handleLink(i: Intent?) {
        val d = i?.data ?: return
        if (d.scheme != "com.wavy.app" || d.host != "login") return
        val raw = d.encodedFragment ?: d.encodedQuery ?: ""
        val m = raw.split("&").filter { it.contains("=") }.associate { it.substringBefore("=") to java.net.URLDecoder.decode(it.substringAfter("="), "UTF-8") }
        GoogleAuth.result.value = if (m["access_token"] != null && m["refresh_token"] != null) Triple(m["access_token"]!!, m["refresh_token"]!!, null) else Triple("", "", m["error_description"] ?: "Google sign-in did not finish.")
    }

    override fun onStart() {
        super.onStart()
        val askedPrefs = getSharedPreferences("wavy_flags", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedPrefs.getBoolean("asked_notif", false) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) { askedPrefs.edit().putBoolean("asked_notif", true).apply(); ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1000) }
        if (!MusicService.isRunning) {
            try { ContextCompat.startForegroundService(this, Intent(this, MusicService::class.java)) } catch (e: Exception) { }
        }
        if (!bound) { bindService(Intent(this, MusicService::class.java), sc, BIND_AUTO_CREATE); bound = true }
    }

    override fun onDestroy() {
        if (bound) { try { unbindService(sc) } catch (e: Exception) { }; bound = false }
        connection?.dispose()
        super.onDestroy()
    }
}
