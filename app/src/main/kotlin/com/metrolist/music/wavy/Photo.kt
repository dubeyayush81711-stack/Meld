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
    fun load(c: Context): Bitmap? = try { file(c).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } } catch (e: Exception) { null }
    fun save(c: Context, uri: Uri): Boolean = try {
        val src = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return false
        val s = minOf(src.width, src.height)
        val sq = Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
        val out = Bitmap.createScaledBitmap(sq, 512, 512, true)
        file(c).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ver.intValue++; true
    } catch (e: Exception) { false }
    fun remove(c: Context) { try { file(c).delete() } catch (e: Exception) { }; ver.intValue++ }
}

@Composable
fun rememberAvatar(c: Context): Bitmap? { val v = AvatarStore.ver.intValue; return remember(v) { AvatarStore.load(c) } }
