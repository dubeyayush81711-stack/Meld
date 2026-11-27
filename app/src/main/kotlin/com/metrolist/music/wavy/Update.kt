package com.metrolist.music.wavy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateInfo(val build: Int, val tag: String, val notes: String, val url: String, val size: Long, val sha: String = "")

/** Checks the public GitHub releases of the Wavy repo (free, no login) and downloads the newer APK inside the app. */
object Updater {
    private const val API = "https://api.github.com/repos/dubeyayush81711-stack/Meld/releases?per_page=12"
    var info by mutableStateOf<UpdateInfo?>(null)
    var checking by mutableStateOf(false)
    var checkedOnce by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var progress by mutableFloatStateOf(-1f)
    var busy by mutableStateOf(false)
    var gotBytes by mutableLongStateOf(0L)
    var totalBytes by mutableLongStateOf(0L)
    fun mb(b: Long) = String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)
    var error by mutableStateOf<String?>(null)
    var dismissedThisRun by mutableStateOf(false)

    val current: Int get() = Regex("(\\d+)").find(WAVY_BUILD)?.value?.toIntOrNull() ?: 0

    suspend fun check() {
        if (checking) return
        checking = true; failed = false
        try {
            val found = withContext(Dispatchers.IO) {
                val c = URL(API).openConnection() as HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 15000; c.setRequestProperty("Accept", "application/vnd.github+json")
                if (c.responseCode != 200) throw java.io.IOException("HTTP " + c.responseCode)
                val txt = c.inputStream.bufferedReader().use { it.readText() }
                val a = JSONArray(txt); var best: UpdateInfo? = null
                for (i in 0 until a.length()) {
                    val r = a.getJSONObject(i); if (r.optBoolean("draft")) continue
                    val tag = r.optString("tag_name"); val m = Regex("^wavy-test-(\\d+)$").find(tag) ?: continue
                    val n = m.groupValues[1].toInt()
                    val assets = r.optJSONArray("assets") ?: continue
                    var url = ""; var size = 0L; var sha = ""
                    for (k in 0 until assets.length()) { val s = assets.getJSONObject(k); if (url.isEmpty() && s.optString("name").endsWith(".apk")) { url = s.optString("browser_download_url"); size = s.optLong("size"); sha = s.optString("digest").removePrefix("sha256:").lowercase() } }
                    if (url.isEmpty()) continue
                    if (best == null || n > best.build) best = UpdateInfo(n, tag, r.optString("body"), url, size, sha)
                }
                best
            }
            info = found?.takeIf { it.build > current }
            checkedOnce = true
        } catch (e: Exception) { failed = true } finally { checking = false }
    }

    suspend fun download(ctx: Context, u: UpdateInfo): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.cacheDir, "updates"); dir.mkdirs(); dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, "Wavy-update.apk")
            val c = URL(u.url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = true
            if (c.responseCode != 200) { error = "Download failed (server said " + c.responseCode + "). Try again later."; return@withContext null }
            val total = if (c.contentLengthLong > 0) c.contentLengthLong else u.size
            val md = java.security.MessageDigest.getInstance("SHA-256"); var got = 0L
            c.inputStream.use { ins -> f.outputStream().use { out ->
                val buf = ByteArray(64 * 1024); var last = 0L
                while (true) { val n = ins.read(buf); if (n < 0) break; out.write(buf, 0, n); md.update(buf, 0, n); got += n
                    val now = System.currentTimeMillis(); if (now - last > 120 && total > 0) { last = now; progress = (got.toFloat() / total).coerceIn(0f, 1f); gotBytes = got; totalBytes = total } }
            } }
            if ((total > 0 && got != total) || got < 1_000_000L) { f.delete(); error = "The download was cut off. Check your connection and try again."; return@withContext null }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (u.sha.isNotEmpty() && u.sha != hex) { f.delete(); error = "The downloaded file did not match the published checksum, so it was not installed."; return@withContext null }
            if (!sameSigner(ctx, f)) { f.delete(); error = "The downloaded file is not signed by the same key as this app, so it was not installed."; return@withContext null }
            progress = 1f; f
        } catch (e: Exception) { null }
    }

    @Suppress("DEPRECATION")
    private fun sameSigner(ctx: Context, f: File): Boolean = try {
        val pm = ctx.packageManager
        fun sigs(i: android.content.pm.PackageInfo?): Set<String> {
            val s = i?.signingInfo ?: return emptySet()
            val arr = if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
            return arr.map { java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
        }
        val mine = sigs(pm.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES))
        val theirs = sigs(pm.getPackageArchiveInfo(f.path, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES))
        // If the phone cannot read the package at all, let Android's own installer decide (it enforces the same signature rule).
        theirs.isEmpty() || mine.isEmpty() || mine.any { it in theirs }
    } catch (e: Exception) { true }

    fun install(ctx: Context, f: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".FileProvider", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}

/** Full-screen update page: current vs new build, release notes, Later and Update. Update downloads here and opens the installer. */
@Composable
fun UpdatePage(u: UpdateInfo, onLater: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<File?>(null) }
    var needPerm by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(W.Bg).clickable(enabled = true, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
            Text("Update available", Modifier.padding(top = 36.dp), style = ts(32, FontWeight.Bold))
            Text("Wavy test ${u.build}  ·  you have $WAVY_BUILD", Modifier.padding(top = 8.dp), style = ts(15, color = W.Dim))
            if (u.size > 0) Text("Update size: " + Updater.mb(u.size), Modifier.padding(top = 4.dp), style = ts(14, color = W.Dim))
            Column(Modifier.weight(1f).padding(vertical = 20.dp).clip(RoundedCornerShape(26.dp)).background(Color(0x1FFFFFFF)).verticalScroll(rememberScrollState()).padding(20.dp)) {
                val notes = u.notes.ifBlank { "Fixes and improvements." }.replace(Regex("(?m)^#+\\s*"), "").replace("**", "")
                Text(notes, style = ts(15, color = Color(0xFFE6E1EC)))
            }
            if (Updater.error != null) Text(Updater.error!!, Modifier.padding(bottom = 10.dp), style = ts(13, color = Color(0xFFFF6B8A)))
            if (needPerm) Text("Allow Wavy to install updates in the screen that just opened, then come back and tap Update again.", Modifier.padding(bottom = 10.dp), style = ts(13, color = W.Dim))
            if (Updater.busy && Updater.progress >= 0f && Updater.progress < 1f) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) { Box(Modifier.fillMaxHeight().fillMaxWidth(Updater.progress).background(W.Pink)) }
                Text((Updater.progress * 100).toInt().toString() + "%  ·  " + Updater.mb(Updater.gotBytes) + " of " + Updater.mb(if (Updater.totalBytes > 0) Updater.totalBytes else u.size), Modifier.padding(top = 6.dp, bottom = 10.dp), style = ts(13, color = W.Dim))
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF)).clickable(enabled = !Updater.busy) { onLater() }, contentAlignment = Alignment.Center) { Text("Later", style = ts(16, FontWeight.SemiBold)) }
                Box(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(50)).background(W.Pink).clickable(enabled = !Updater.busy) {
                    Updater.error = null; needPerm = false
                    scope.launch {
                        Updater.busy = true; Updater.progress = 0f
                        val f = file ?: Updater.download(ctx, u)
                        Updater.busy = false
                        if (f == null) { if (Updater.error == null) Updater.error = "Download failed. Check your connection and try again." }
                        else { file = f; if (!Updater.install(ctx, f)) needPerm = true }
                    }
                }, contentAlignment = Alignment.Center) { Text(if (Updater.busy) "Downloading..." else "Update", style = ts(16, FontWeight.SemiBold)) }
            }
        }
    }
}

/** Shows the update page once per app start when a newer build exists. Later hides it until the next build or app start. */
@Composable
fun UpdateGate() {
    LaunchedEffect(Unit) { Updater.check() }
    val u = Updater.info ?: return
    if (Updater.dismissedThisRun || S.s("upd_skip", "") == u.build.toString()) return
    UpdatePage(u) { S.put("upd_skip", u.build.toString()); Updater.dismissedThisRun = true }
}

@Composable
fun UpdateSettingsRows() {
    val scope = rememberCoroutineScope(); var show by remember { mutableStateOf(false) }
    val u = Updater.info
    if (u != null) SNav("Update available", "Wavy test ${u.build} is out. You have $WAVY_BUILD.") { show = true }
    SNav("Check for updates", if (Updater.checking) "Checking..." else if (Updater.failed) "Could not check. Tap to try again." else if (Updater.checkedOnce && u == null) "You are on the newest build." else "See if a newer Wavy build is out.") { scope.launch { Updater.check() } }
    if (show && u != null) androidx.compose.ui.window.Dialog(onDismissRequest = { show = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) { UpdatePage(u) { show = false } }
}
