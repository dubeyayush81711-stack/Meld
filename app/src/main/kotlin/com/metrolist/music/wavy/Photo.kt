package com.metrolist.music.wavy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import java.io.File

/** Profile photo kept on this phone: centre-cropped to a square, shown in a circle everywhere the avatar appears. */
object AvatarStore {
    val ver = mutableIntStateOf(0)
    private fun file(c: Context) = File(c.filesDir, "avatar_" + Api.uid + ".jpg")
    fun load(c: Context): Bitmap? = try { file(c).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } } catch (t: Throwable) { null }
    /** Decodes a down-sampled copy (never the full camera photo), applies the EXIF rotation, then crops to a square. Call off the main thread. */
    fun save(c: Context, uri: Uri): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1024 && bounds.outHeight / (sample * 2) >= 1024) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var src = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
        val rot = try { c.contentResolver.openInputStream(uri)?.use { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) } ?: 1 } catch (t: Throwable) { 1 }
        val deg = when (rot) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
        if (deg != 0f) { val m = android.graphics.Matrix().apply { postRotate(deg) }; src = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true) }
        val s = minOf(src.width, src.height)
        val sq = Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
        val out = Bitmap.createScaledBitmap(sq, 512, 512, true)
        file(c).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ver.intValue++; true
    } catch (t: Throwable) { false }
    fun remove(c: Context) { try { file(c).delete() } catch (e: Exception) { }; ver.intValue++ }
}

@Composable
fun rememberAvatar(c: Context): Bitmap? { val v = AvatarStore.ver.intValue; return remember(v) { AvatarStore.load(c) } }
