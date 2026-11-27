package com.metrolist.music.wavy

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class ApiError(message: String, val code: Int = 0) : Exception(message)

/** Supabase client. Email+password auth (GoTrue) and PostgREST/RPC calls with the user's JWT. */
object Api {
    const val URL = "https://jzamdwqmvvywxcjwodwy.supabase.co"
    const val KEY = "sb_publishable_u7PeU6BAAGe-sRZfJxdKBQ_Ys2j8KAC"
    private val JSON = "application/json".toMediaType()
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private var prefs: android.content.SharedPreferences? = null

    var access: String = ""; private set
    private var refresh: String = ""
    var uid: String = ""; private set
    var email: String = ""; private set
    val signedIn get() = refresh.isNotEmpty()

    private var processStarted = false
    private val refreshLock = kotlinx.coroutines.sync.Mutex()

    private fun clearSession(p: android.content.SharedPreferences) {
        val rem = p.getBoolean("remember", true)
        p.edit().clear().putBoolean("remember", rem).apply()
    }

    fun init(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("wavy_session", Context.MODE_PRIVATE); prefs = p
        // Only a brand-new process can mean "the app was closed". An activity re-create (rotate, dark mode, language) must not log the user out.
        if (!processStarted) {
            processStarted = true
            if (!p.getBoolean("remember", true) && p.getBoolean("cold", false)) clearSession(p)
        }
        access = p.getString("a", "") ?: ""; refresh = p.getString("r", "") ?: ""
        uid = p.getString("uid", "") ?: ""; email = p.getString("email", "") ?: ""
        p.edit().putBoolean("cold", true).apply()
    }

    fun setRemember(v: Boolean) { prefs?.edit()?.putBoolean("remember", v)?.apply() }
    fun remember() = prefs?.getBoolean("remember", true) ?: true

    fun signOut() {
        access = ""; refresh = ""; uid = ""; email = ""
        prefs?.let { clearSession(it) }
    }

    private fun saveSession(j: JSONObject) {
        access = j.getString("access_token"); refresh = j.getString("refresh_token")
        val u = j.optJSONObject("user"); if (u != null) { uid = u.optString("id"); email = u.optString("email") }
        prefs?.edit()?.putString("a", access)?.putString("r", refresh)?.putString("uid", uid)?.putString("email", email)?.apply()
    }

    private fun errText(body: String, code: Int): String = try {
        val j = JSONObject(body)
        listOf("msg", "error_description", "message", "error").map { j.optString(it) }.firstOrNull { it.isNotBlank() } ?: "Error $code"
    } catch (e: Exception) { if (body.isNotBlank() && body.length < 200) body else "Error $code" }

    private suspend fun exec(b: Request.Builder, authed: Boolean): String = withContext(Dispatchers.IO) {
        var usedAccess = access
        suspend fun once(): Pair<Int, String> {
            usedAccess = access
            val rb = b.build().newBuilder().header("apikey", KEY)
                .header("Authorization", "Bearer " + (if (authed && access.isNotEmpty()) access else KEY)).build()
            http.newCall(rb).execute().use { r -> return r.code to (r.body?.string() ?: "") }
        }
        var (code, body) = try { once() } catch (e: java.io.IOException) { throw ApiError("No connection. Check your internet.") }
        if (authed && (code == 401 || (code == 400 && body.contains("JWT")))) {
            if (refreshToken(usedAccess)) { val r = once(); code = r.first; body = r.second }
        }
        if (code !in 200..299) { if (code != 401 && code != 429 && !b.build().url.encodedPath.contains("/functions/")) ErrorLog.report("network", "HTTP " + code + " " + b.build().method + " " + b.build().url.encodedPath + " " + body.take(120)); throw ApiError(errText(body, code), code) }
        body
    }

    /** One refresh at a time. If another request already refreshed while we waited for the lock, reuse its new token instead of spending the old refresh token again. */
    private suspend fun refreshToken(usedAccess: String): Boolean = refreshLock.withLock {
        if (access.isNotEmpty() && access != usedAccess) return@withLock true
        if (refresh.isEmpty()) return@withLock false
        try {
            val req = Request.Builder().url("$URL/auth/v1/token?grant_type=refresh_token")
                .post(JSONObject().put("refresh_token", refresh).toString().toRequestBody(JSON))
            saveSession(JSONObject(exec(req, false))); true
        } catch (e: ApiError) { if (e.code in 400..403) signOut(); false }
    }

    suspend fun signIn(email: String, pw: String) {
        val req = Request.Builder().url("$URL/auth/v1/token?grant_type=password")
            .post(JSONObject().put("email", email.trim()).put("password", pw).toString().toRequestBody(JSON))
        saveSession(JSONObject(exec(req, false)))
    }

    suspend fun signUp(email: String, pw: String) {
        val req = Request.Builder().url("$URL/auth/v1/signup")
            .post(JSONObject().put("email", email.trim()).put("password", pw).toString().toRequestBody(JSON))
        val j = JSONObject(exec(req, false))
        if (j.has("access_token")) saveSession(j) else signIn(email, pw)
    }

    /** Google sign-in: Supabase redirects back with the session in the link. Stores it and loads the user. */
    suspend fun oauthSession(accessTok: String, refreshTok: String) {
        saveSession(JSONObject().put("access_token", accessTok).put("refresh_token", refreshTok))
        val u = JSONObject(exec(Request.Builder().url("$URL/auth/v1/user").get(), true))
        saveSession(JSONObject().put("access_token", accessTok).put("refresh_token", refreshTok).put("user", u))
    }

    fun googleUrl(): String = "$URL/auth/v1/authorize?provider=google&redirect_to=" + java.net.URLEncoder.encode("com.wavy.app://login", "UTF-8")

    suspend fun recover(email: String) {
        val req = Request.Builder().url("$URL/auth/v1/recover")
            .post(JSONObject().put("email", email.trim()).toString().toRequestBody(JSON))
        exec(req, false)
    }

    /** Calls an RPC and returns the parsed JSON (JSONObject, JSONArray or null). */
    suspend fun rpc(fn: String, args: JSONObject = JSONObject()): Any? {
        val req = Request.Builder().url("$URL/rest/v1/rpc/$fn").post(args.toString().toRequestBody(JSON))
        return parse(exec(req, true))
    }

    /** Calls an edge function (AI) with the user's session. */
    suspend fun fn(name: String, body: JSONObject): JSONObject? {
        val req = Request.Builder().url("$URL/functions/v1/$name").post(body.toString().toRequestBody(JSON))
        return parse(exec(req, true)) as? JSONObject
    }

    /** Private storage upload/download with the user session. */
    suspend fun storagePut(bucket: String, path: String, bytes: ByteArray, mime: String) { withContext(Dispatchers.IO) {
        val enc = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
        val req = Request.Builder().url("$URL/storage/v1/object/$bucket/$enc").header("apikey", KEY).header("Authorization", "Bearer $access").header("x-upsert", "true").post(bytes.toRequestBody(mime.toMediaType())).build()
        http.newCall(req).execute().use { r -> if (!r.isSuccessful) throw ApiError("Upload failed (${r.code})", r.code) }
    } }
    /** Removes every file under a folder prefix. Best effort. */
    suspend fun storageClear(bucket: String, prefix: String) { withContext(Dispatchers.IO) {
        try {
            val lreq = Request.Builder().url("$URL/storage/v1/object/list/$bucket").header("apikey", KEY).header("Authorization", "Bearer $access")
                .post(JSONObject().put("prefix", prefix).put("limit", 500).toString().toRequestBody(JSON)).build()
            val arr = http.newCall(lreq).execute().use { r -> if (r.isSuccessful) org.json.JSONArray(r.body?.string() ?: "[]") else null } ?: return@withContext
            val names = (0 until arr.length()).map { prefix + "/" + arr.getJSONObject(it).optString("name") }
            if (names.isEmpty()) return@withContext
            val dreq = Request.Builder().url("$URL/storage/v1/object/$bucket").header("apikey", KEY).header("Authorization", "Bearer $access")
                .delete(JSONObject().put("prefixes", org.json.JSONArray(names)).toString().toRequestBody(JSON)).build()
            http.newCall(dreq).execute().close()
        } catch (e: Exception) { }
    } }
    suspend fun storageGet(bucket: String, path: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val enc = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
            val req = Request.Builder().url("$URL/storage/v1/object/authenticated/$bucket/$enc").header("apikey", KEY).header("Authorization", "Bearer $access").get().build()
            http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null }
        } catch (e: Exception) { null }
    }

    suspend fun get(path: String): Any? = parse(exec(Request.Builder().url("$URL/rest/v1/$path").get(), true))

    suspend fun post(path: String, body: Any, upsert: Boolean = false) {
        val b = Request.Builder().url("$URL/rest/v1/$path").post(body.toString().toRequestBody(JSON))
            .header("Prefer", if (upsert) "resolution=merge-duplicates,return=minimal" else "return=minimal")
        exec(b, true)
    }

    suspend fun delete(path: String) { exec(Request.Builder().url("$URL/rest/v1/$path").delete(), true) }

    private fun parse(s: String): Any? {
        val t = s.trim(); if (t.isEmpty() || t == "null") return null
        if (t == "true") return true
        if (t == "false") return false
        return if (t.startsWith("[")) JSONArray(t) else if (t.startsWith("{")) JSONObject(t) else null
    }
}

data class Track(val id: String, val title: String, val artist: String, val cover: String?, val dur: Int, val artistId: String?, val album: String?)
data class ArtistItem(val id: String, val name: String, val thumb: String?)
data class AlbumItem(val title: String, val artist: String, val cover: String?, val tracks: List<Track>)
data class Section(val id: String, val type: String, val title: String, val subtitle: String?, val cover: String?,
                   val tracks: List<Track>, val albums: List<AlbumItem>, val artists: List<ArtistItem>)

private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }

fun JSONObject.toTrack() = Track(optString("yt_video_id"), optString("title"), optString("artist"), str("cover_url") ?: str("thumbnail_url"), optInt("duration_s"), str("artist_id"), str("album"))
fun JSONArray?.tracks(): List<Track> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.toTrack() }
fun JSONArray?.artistItems(): List<ArtistItem> = if (this == null) emptyList() else (0 until length()).mapNotNull { i ->
    optJSONObject(i)?.let { ArtistItem(it.optString("id"), it.optString("name"), it.str("thumbnail_url")) } }

data class Feed(val sections: List<Section>, val favouritesId: Long?)

fun parseFeed(j: JSONObject): Feed {
    val arr = j.optJSONArray("sections") ?: JSONArray()
    val secs = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { s ->
        val albs = s.optJSONArray("albums")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
            AlbumItem(o.optString("album"), o.optString("artist"), o.str("cover_url"), o.optJSONArray("tracks").tracks()) } } ?: emptyList()
        Section(s.optString("id"), s.optString("type"), s.optString("title"), s.str("subtitle"), s.str("cover_url"),
            LangGuard.filter(s.optJSONArray("tracks").tracks()), albs.map { al -> al.copy(tracks = LangGuard.filter(al.tracks)) }.filter { it.tracks.isNotEmpty() }, s.optJSONArray("artists").artistItems())
    }
    return Feed(secs, if (j.isNull("favourites_playlist_id")) null else j.optLong("favourites_playlist_id"))
}

/** Server-side flag (once per account). A failed network call must never throw the user back to the picker. */
suspend fun onboardingDone(): Boolean {
    val flag = "onb_" + (Api.uid ?: "")
    if (S.b(flag)) return true
    val r = Api.rpc("onboarding_done")
    val done = r == true || r?.toString() == "true"
    if (done) S.put(flag, true)
    return done
}

fun humanErr(e: Exception): String {
    val m = (e.message ?: "").lowercase()
    return when {
        "row-level security" in m || "not allowed" in m -> "This account is not allowed yet. Ask Ayush to add your email."
        "locked by host" in m -> "The host has locked this."
        "listen-only" in m -> "You are listen-only in this room."
        "muted" in m -> "The host muted you."
        "at least 3" in m -> "Pick at least 3 artists."
        "network" in m || "unable to resolve" in m || "timeout" in m || "failed to connect" in m -> "No connection. Check your internet and try again."
        "invalid login" in m || "invalid_credentials" in m -> "Wrong email or password."
        "already registered" in m || "already been registered" in m -> "This email already has an account. Sign in instead."
        else -> "Something went wrong. Please try again."
    }
}
