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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        Api.init(this); S.init(this)
        WDl.util = downloadUtil; WDl.db = database
        setContent { val d = androidx.compose.ui.platform.LocalDensity.current; androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, minOf(d.fontScale, 1f))) { WavyApp(pc) } }
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1000)
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
