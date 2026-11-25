package com.metrolist.music.wavy

import android.content.Context
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.DownloadUtil
import com.metrolist.music.playback.ExoDownloadService

/** Offline songs. Uses the engine's download manager (audio saved in app storage). */
object WDl {
    var util: DownloadUtil? = null
    var db: MusicDatabase? = null

    fun start(t: Track) { util?.download(t.toMeta()) }
    fun remove(ctx: Context, id: String) { DownloadService.sendRemoveDownload(ctx, ExoDownloadService::class.java, id, false) }

    fun doneIds(m: Map<String, Download>): List<String> =
        m.values.filter { it.state == Download.STATE_COMPLETED }.sortedByDescending { it.updateTimeMs }.map { it.request.id }

    fun toTrack(id: String): Track? {
        val s = db?.getSongByIdBlocking(id) ?: return null
        return Track(id, s.song.title, s.artists.joinToString { it.name }, s.song.thumbnailUrl, s.song.duration, s.artists.firstOrNull()?.id, null)
    }
}
