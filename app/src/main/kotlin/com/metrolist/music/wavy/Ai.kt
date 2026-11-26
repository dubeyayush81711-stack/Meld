package com.metrolist.music.wavy

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** AI helpers. Every call can fail (limits, network, provider down); callers always keep a normal fallback. */
object Ai {
    suspend fun chat(task: String, history: List<Pair<String, String>>): String? = try {
        val msgs = JSONArray(); history.takeLast(10).forEach { msgs.put(JSONObject().put("role", it.first).put("content", it.second)) }
        val r = Api.fn("wavy-ai", JSONObject().put("task", task).put("messages", msgs))
        r?.optString("reply")?.takeIf { it.isNotBlank() }
    } catch (e: Exception) { null }

    /** Returns the ids in the order the AI prefers, or null so the caller keeps its own order. */
    suspend fun rerank(query: String, langs: List<String>, items: List<Track>): List<String>? = try {
        if (items.size < 3) null else {
            val arr = JSONArray(); items.take(40).forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist)) }
            val r = Api.fn("wavy-ai", JSONObject().put("task", "rerank").put("query", query).put("langs", JSONArray(langs)).put("items", arr))
            val o = r?.optJSONArray("order"); if (o == null) null else (0 until o.length()).map { o.getString(it) }
        }
    } catch (e: Exception) { null }

    suspend fun reorder(query: String, langs: List<String>, items: List<Track>): List<Track> {
        val order = rerank(query, langs, items) ?: return items
        val pos = order.withIndex().associate { it.value to it.index }
        val head = items.take(40).sortedBy { pos[it.id] ?: Int.MAX_VALUE }
        return head + items.drop(40)
    }

    suspend fun bugReport(text: String, history: List<Pair<String, String>>): Boolean = try {
        val h = history.takeLast(8).joinToString("\n") { it.first + ": " + it.second.take(300) }
        Api.fn("wavy-ai", JSONObject().put("task", "bug").put("text", text.take(1500)).put("context", h).put("build", WAVY_BUILD))?.optBoolean("ok", false) ?: false
    } catch (e: Exception) { false }
}

/** Chat screen used by both the Wavy agent and the support helper. */
@Composable
fun AiChatScreen(task: String, title: String, hello: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var msgs by remember { mutableStateOf(listOf("assistant" to hello)) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf<String?>(null) }
    val st = rememberLazyListState()
    LaunchedEffect(msgs.size, busy) { if (msgs.isNotEmpty()) st.animateScrollToItem(msgs.size) }
    fun send(t: String) {
        if (t.isBlank() || busy) return
        msgs = msgs + ("user" to t.trim()); input = ""; busy = true
        scope.launch {
            val r = Ai.chat(task, msgs)
            msgs = msgs + ("assistant" to (r ?: "I can't reach the assistant right now. Please try again in a bit."))
            busy = false
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF050507), Color(0xFF09090D), Color(0xFF0B0B10))))) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().background(GlassBar)) { BackBar(title, onBack) }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), state = st, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 14.dp)) {
                items(msgs) { (role, text) ->
                    val me = role == "user"
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (me) Alignment.CenterEnd else Alignment.CenterStart) {
                        Text(text, Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp)).background(if (me) W.Pink else Color(0x33FFFFFF)).padding(horizontal = 14.dp, vertical = 10.dp), style = ts(15, color = Color.White))
                    }
                }
                if (busy) item { Text("Thinking...", style = ts(14, color = W.Dim)) }
                if (task == "support") item {
                    Text(reported ?: "Send a bug report", Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(50)).clickable(enabled = reported == null) {
                        val last = msgs.lastOrNull { it.first == "user" }?.second ?: ""
                        if (last.isBlank()) reported = "Describe the problem first, then tap again" else scope.launch { reported = "Sending..."; reported = if (Ai.bugReport(last, msgs)) "Bug report sent. Thank you!" else "Could not send. Try again later." }
                    }.padding(horizontal = 18.dp, vertical = 10.dp), style = ts(14, FontWeight.Medium))
                }
            }
            Row(Modifier.fillMaxWidth().background(GlassBar).navigationBarsPadding().imePadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(input, { input = it }, Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = ts(15, color = Color.White), cursorBrush = androidx.compose.ui.graphics.SolidColor(W.Pink), decorationBox = { inner -> if (input.isEmpty()) Text("Message", style = ts(15, color = W.Dim)); inner() })
                Spacer(Modifier.width(10.dp))
                Text("Send", Modifier.clip(RoundedCornerShape(50)).background(W.Pink).clickable { send(input) }.padding(horizontal = 18.dp, vertical = 12.dp), style = ts(15, FontWeight.SemiBold))
            }
        }
    }
}
