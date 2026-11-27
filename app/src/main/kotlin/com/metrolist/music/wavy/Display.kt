package com.metrolist.music.wavy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.asImageBitmap
import android.content.Context

data class WavyTheme(val id: String, val name: String, val accent: Color, val accent2: Color, val bg: Color, val home: String)

/** Display choices. The default ("pink") is exactly the look Wavy already had, so nothing changes until a user picks something else. */
object Theming {
    val themes = listOf(
        WavyTheme("pink", "Wavy pink (default)", Color(0xFFFF2D78), Color(0xFF8E3BEA), Color(0xFF0B141A), "midnight"),
        WavyTheme("midnight", "Midnight", Color(0xFFFF2D78), Color(0xFF8E3BEA), Color(0xFF121212), "midnight"),
        WavyTheme("aurora", "Aurora (colourful Home)", Color(0xFFFF2D78), Color(0xFF8E3BEA), Color(0xFF0B141A), "aurora"),
        WavyTheme("purple", "Purple", Color(0xFFA855F7), Color(0xFF6D28D9), Color(0xFF0B141A), "midnight"),
        WavyTheme("blue", "Blue", Color(0xFF3B82F6), Color(0xFF1D4ED8), Color(0xFF0B141A), "midnight"),
        WavyTheme("green", "Green", Color(0xFF22C55E), Color(0xFF15803D), Color(0xFF0B141A), "midnight"),
        WavyTheme("orange", "Orange", Color(0xFFFB923C), Color(0xFFEA580C), Color(0xFF0B141A), "midnight"),
        WavyTheme("red", "Red", Color(0xFFEF4444), Color(0xFFB91C1C), Color(0xFF0B141A), "midnight"),
        WavyTheme("teal", "Teal", Color(0xFF14B8A6), Color(0xFF0F766E), Color(0xFF0B141A), "midnight"),
        WavyTheme("blackorange", "Black and orange", Color(0xFFFF7A1A), Color(0xFFC2410C), Color(0xFF000000), "midnight"),
    )
    private var loaded = false
    private val themeId = mutableStateOf("pink")
    private val fontId = mutableStateOf("figtree")
    private fun load() { if (loaded) return; val sp = S.s("theme", ""); if (sp.isNotEmpty() || S.s("ui_font", "").isNotEmpty() || true) { loaded = true; themeId.value = S.s("theme", "pink"); fontId.value = S.s("ui_font", "figtree") } }
    fun cur(): WavyTheme { load(); return themes.firstOrNull { it.id == themeId.value } ?: themes[0] }
    fun fontKey(): String { load(); return fontId.value }
    fun setTheme(t: WavyTheme) { load(); themeId.value = t.id; S.put("theme", t.id); S.put("home_look", t.home) }
    fun setFont(id: String) { load(); fontId.value = id; S.put("ui_font", id) }
    fun reset() { setTheme(themes[0]); setFont("figtree") }

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    private fun variable(res: Int): FontFamily = FontFamily(*listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
        Font(res, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    }.toTypedArray())

    val fonts: List<Pair<String, String>> = listOf("figtree" to "Figtree (default)", "poppins" to "Poppins", "nunito" to "Nunito", "outfit" to "Outfit", "manrope" to "Manrope", "space_grotesk" to "Space Grotesk")
    private val families = HashMap<String, FontFamily>()
    fun family(id: String = fontKey()): FontFamily = families.getOrPut(id) {
        when (id) {
            "poppins" -> Poppins
            "nunito" -> variable(R.font.nunito)
            "outfit" -> variable(R.font.outfit)
            "manrope" -> variable(R.font.manrope)
            "space_grotesk" -> variable(R.font.space_grotesk)
            else -> Figtree
        }
    }
}

@Composable
fun DisplaySettings(onBack: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val sel = Theming.cur().id; val font = Theming.fontKey()
    SPage("Display", onBack) {
        SNote("Only changes how Wavy looks. The default is the look you already have. Songs and audio are never touched.")
        SSection("Theme")
        Theming.themes.forEach { t ->
            Row(Modifier.padding(start = 20.dp, top = 6.dp)) {
                Text("\u25CF  ", style = ts(18, color = t.accent))
            }
            SRadio(t.name, if (t.id == "aurora") "The colourful Home you had before." else if (t.id == "midnight") "Dark grey, calm and clean." else "", sel == t.id) { Theming.setTheme(t) }
        }
        SSection("Font")
        Theming.fonts.forEach { (id, name) ->
            SRadio(name, "The quick brown fox jumps over the lazy dog", font == id) { Theming.setFont(id) }
        }
        SSection("Wallpaper")
        WallpaperSettings()
        SSection("Reset")
        SNav("Reset display", "Go back to the default look and font") { confirm = true }
        Spacer(Modifier.height(24.dp))
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Reset display?") },
        text = { Text("Your theme and font go back to the default.") },
        confirmButton = { TextButton({ Theming.reset(); confirm = false }) { Text("Reset") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
    )
}


/** Optional Home wallpaper from the user's own photo. Saved on this phone only, resized, with a dark layer so text stays readable. */
object Wallpaper {
    var version by mutableIntStateOf(0)
    private fun file(c: Context) = java.io.File(c.filesDir, "wallpaper.jpg")
    fun has(c: Context) = file(c).exists()
    fun load(c: Context): android.graphics.Bitmap? = try { file(c).takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.path) } } catch (t: Throwable) { null }
    fun import(c: Context, uri: android.net.Uri): Boolean = try {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
        var sample = 1; while (maxOf(o.outWidth, o.outHeight) / sample > 1600) sample *= 2
        val bmp = c.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) }
        if (bmp == null) false else { file(c).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, it) }; version++; true }
    } catch (t: Throwable) { false }
    fun clear(c: Context) { file(c).delete(); version++ }
    fun places(@Suppress("UNUSED_PARAMETER") v: Int): Set<String> = S.s("wp_places", "home").split(',').filter { it.isNotBlank() }.toSet()
    fun toggle(p: String) { val s = places(0).toMutableSet(); if (!s.add(p)) s.remove(p); S.put("wp_places", s.joinToString(",")); version++ }
}

@Composable
fun WallpaperLayer(place: String = "home") {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val v = Wallpaper.version
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, v) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Wallpaper.load(ctx)?.asImageBitmap() } }
    val b = bmp ?: return
    if (place !in Wallpaper.places(v)) return
    androidx.compose.foundation.Image(b, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color(0xB3000000)))
}

@Composable
fun WallpaperSettings() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val v = Wallpaper.version
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { val ok = Wallpaper.import(ctx, uri); android.widget.Toast.makeText(ctx, if (ok) "Wallpaper set on Home" else "Could not use that photo.", android.widget.Toast.LENGTH_SHORT).show() }
    }
    SNav("Home wallpaper", if (Wallpaper.has(ctx)) "Your photo is behind Home. Tap to change." else "Pick a photo from your gallery for Home.") { pick.launch("image/*") }
    if (v >= 0 && Wallpaper.has(ctx)) {
        val on = Wallpaper.places(v)
        listOf("home" to "Home", "library" to "Your Library", "search" to "Search", "party" to "Party room").forEach { (k, l) ->
            SNav("Show on $l", if (k in on) "On. Tap to turn off." else "Off. Tap to turn on.") { Wallpaper.toggle(k) }
        }
        SNav("Remove wallpaper", "Go back to the plain background") { Wallpaper.clear(ctx) }
    }
}
