package com.metrolist.music.wavy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VCand(val url: String, val w: Int, val h: Int, val client: String, val ua: String, val hls: Boolean = false)

private class VClient(val name: String, val id: String, val ua: String, val ctx: JSONObject, val embed: Boolean = false)

private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()

private fun ctx(name: String, ver: String, extra: JSONObject.() -> Unit = {}) =
    JSONObject().put("clientName", name).put("clientVersion", ver).put("hl", "en").put("gl", "IN").apply(extra)

private val VCLIENTS: List<VClient> by lazy {
    val iosUa = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
    val vrUa = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Oculus Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)"
    val safariUa = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15"
    val mwebUa = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
    listOf(
        VClient("IOS", "5", iosUa, ctx("IOS", "20.10.4") { put("deviceMake", "Apple"); put("deviceModel", "iPhone16,2"); put("osName", "iPhone"); put("osVersion", "18.3.2.22D82") }),
        VClient("WEB_SAFARI", "1", safariUa, ctx("WEB", "2.20250925.01.00") { put("userAgent", safariUa); put("browserName", "Safari"); put("browserVersion", "15.5") }),
        VClient("TV_EMBED", "85", "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15", ctx("TVHTML5_SIMPLY_EMBEDDED_PLAYER", "2.0"), embed = true),
        VClient("VR", "28", vrUa, ctx("ANDROID_VR", "1.61.48") { put("deviceMake", "Oculus"); put("deviceModel", "Quest 3"); put("osName", "Android"); put("osVersion", "12"); put("androidSdkVersion", 32) }),
        VClient("MWEB", "2", mwebUa, ctx("MWEB", "2.20250925.01.00") { put("userAgent", mwebUa) }),
    )
}

/** Looks for a playable video stream for a YouTube video id. Returns candidates (best first) and a short reason line when none. */
suspend fun fetchVideoCandidates(videoId: String): Pair<List<VCand>, String> = withContext(Dispatchers.IO) {
    val prog = ArrayList<VCand>(); val hls = ArrayList<VCand>(); val muxed = ArrayList<VCand>(); val notes = ArrayList<String>()
    for (c in VCLIENTS) {
        try {
            val clientCtx = JSONObject().put("client", c.ctx)
            if (c.embed) clientCtx.put("thirdParty", JSONObject().put("embedUrl", "https://www.youtube.com/watch?v=$videoId"))
            val body = JSONObject().put("context", clientCtx).put("videoId", videoId).put("contentCheckOk", true).put("racyCheckOk", true)
            val req = Request.Builder().url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .header("User-Agent", c.ua).header("X-YouTube-Client-Name", c.id).header("X-YouTube-Client-Version", c.ctx.getString("clientVersion"))
                .header("Origin", "https://www.youtube.com").post(body.toString().toRequestBody("application/json".toMediaType())).build()
            val j = http.newCall(req).execute().use { JSONObject(it.body?.string() ?: "{}") }
            val status = j.optJSONObject("playabilityStatus")?.optString("status") ?: "?"
            val sd = j.optJSONObject("streamingData")
            if (sd == null) { notes.add("${c.name}:$status"); continue }
            val ad = sd.optJSONArray("adaptiveFormats")
            val fm = sd.optJSONArray("formats")
            var nv = 0; var nUrl = 0
            val found = ArrayList<VCand>()
            if (ad != null) for (i in 0 until ad.length()) {
                val f = ad.getJSONObject(i)
                val mime = f.optString("mimeType")
                if (!mime.startsWith("video/mp4")) continue
                nv++
                val u = f.optString("url", "")
                if (u.isEmpty()) continue
                nUrl++
                found.add(VCand(u, f.optInt("width", 16), f.optInt("height", 9), c.name, c.ua))
            }
            found.sortedBy { when (it.h) { 720 -> 0; 1080 -> 1; in 481..719 -> 2; in 360..480 -> 3; else -> 4 } }.filter { it.h in 360..1080 }.take(2).forEach { prog.add(it) }
            val hu = sd.optString("hlsManifestUrl", "")
            if (hu.isNotEmpty()) hls.add(VCand(hu, 16, 9, c.name, c.ua, hls = true))
            if (fm != null) for (i in 0 until fm.length()) {
                val f = fm.getJSONObject(i); val u = f.optString("url", "")
                if (u.isNotEmpty() && f.has("width")) muxed.add(VCand(u, f.optInt("width"), f.optInt("height", 360), c.name, c.ua))
            }
            if (found.isEmpty() && hu.isEmpty()) notes.add("${c.name}:v$nv/url$nUrl")
        } catch (e: Exception) { notes.add("${c.name}:${e.javaClass.simpleName}") }
        if (prog.size >= 2 && hls.isNotEmpty()) break
    }
    val all = prog + hls + muxed.sortedByDescending { it.h }.take(2)
    all to (if (all.isEmpty()) "no stream (" + notes.joinToString(" ") + ")" else "")
}
