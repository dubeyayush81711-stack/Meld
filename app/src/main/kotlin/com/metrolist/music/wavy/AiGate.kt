package com.metrolist.music.wavy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/** Second net for any shelf: asks the wavy-verify function which songs do not fit the chosen languages or are teasers and low-quality uploads. Keeps the list as it is when the call fails or times out. */
object AiGate {
    suspend fun verify(list: List<Track>, now: Track? = null): List<Track> {
        if (list.size < 3 || S.b("verify_ai_off")) return list
        return try {
            withContext(Dispatchers.IO) {
                val arr = JSONArray(); list.take(20).forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist)) }
                val body = JSONObject().put("langs", JSONArray(LangGuard.selected())).put("items", arr)
                if (now != null) body.put("now", JSONObject().put("title", now.title).put("artist", now.artist))
                val r = withTimeoutOrNull(6000) { Api.fn("wavy-verify", body) } ?: return@withContext list
                val rm = r.optJSONArray("remove") ?: return@withContext list
                val drop = (0 until rm.length()).map { rm.optString(it) }.toSet()
                val out = list.filter { it.id !in drop }
                if (out.size < 3) list else out
            }
        } catch (e: Exception) { list }
    }
}
