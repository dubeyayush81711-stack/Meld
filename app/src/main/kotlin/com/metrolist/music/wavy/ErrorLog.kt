package com.metrolist.music.wavy

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Free, own error capture. Sends only: app build, screen, error text, Android/device model. No personal data. */
object ErrorLog {
    private var dir: File? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seen = HashSet<String>()
    private var sent = 0
    @Volatile var screen = "start"

    private fun enabled() = S.b("send_errors", true)

    fun init(ctx: Context) {
        dir = ctx.applicationContext.filesDir
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { if (enabled()) File(dir, "wavy_crash.json").writeText(build("crash", "Uncaught in " + t.name, e).toString()) } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
        scope.launch { flushCrash() }
    }

    private fun fp(level: String, msg: String, e: Throwable?): String {
        val top = e?.stackTrace?.take(3)?.joinToString("|") { it.className + "." + it.methodName } ?: ""
        val raw = level + "|" + msg.replace(Regex("[0-9]+"), "#").take(120) + "|" + (e?.javaClass?.name ?: "") + "|" + top
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
    }

    private fun build(level: String, msg: String, e: Throwable?): JSONObject = JSONObject()
        .put("task", "err").put("level", level).put("fp", fp(level, msg, e)).put("msg", msg.take(300))
        .put("stack", (e?.let { it.javaClass.name + ": " + (it.message ?: "") + "\n" + it.stackTrace.take(12).joinToString("\n") } ?: "").take(1800))
        .put("screen", screen).put("build", WAVY_BUILD).put("device", Build.MODEL + " Android " + Build.VERSION.RELEASE)

    private suspend fun flushCrash() {
        val f = File(dir ?: return, "wavy_crash.json")
        if (!f.exists()) return
        val body = try { JSONObject(f.readText()) } catch (_: Exception) { f.delete(); return }
        if (!enabled()) { f.delete(); return }
        try { if (Api.signedIn) { Api.fn("wavy-ai", body); f.delete() } } catch (_: Exception) {}
    }

    /** level: error | playback | network | crash. Deduped per session, max 25 per session. */
    fun report(level: String, msg: String, e: Throwable? = null) {
        if (!enabled() || dir == null) return
        val b = build(level, msg, e)
        val k = b.getString("fp")
        synchronized(seen) { if (!seen.add(k) || sent >= 25) return; sent++ }
        scope.launch { try { if (Api.signedIn) Api.fn("wavy-ai", b) } catch (_: Exception) {} }
    }
}
