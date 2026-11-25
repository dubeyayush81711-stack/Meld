package com.metrolist.music.wavy

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import coil3.compose.AsyncImage
import com.metrolist.music.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import androidx.compose.material3.*
import android.content.Intent
import com.metrolist.music.playback.PlayerConnection

private suspend fun call(fn: String, vararg kv: Pair<String, Any?>): Any? {
    val o = JSONObject(); kv.forEach { o.put(it.first, it.second ?: JSONObject.NULL) }; return Api.rpc(fn, o)
}

@Composable
fun PartyScreen(pc: PlayerConnection?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var pid by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { try { val a = call("party_mine") as? JSONArray; if (a != null && a.length() > 0 && a.getJSONObject(0).optString("status") == "active") pid = a.getJSONObject(0).optString("id") } catch (e: Exception) { } }
    if (pid != null) { PartyRoom(pc, pid!!, onLeft = { pid = null }, onBack = onBack); return }
    Backdrop(tint = Color(0xFF2B0B3A)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp)) {
            BackBar("Party", onBack)
            Text("Listen together", style = ts(30, FontWeight.Bold)); Spacer(Modifier.height(6.dp))
            Text("Create a room or join one with a code. Everyone hears the same song at the same time.", style = ts(15, color = Color(0xFFCFC8D9)))
            Spacer(Modifier.height(28.dp))
            PillField(name, { name = it }, "Room name")
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(50)).background(W.Pink).clickable(enabled = !busy) {
                busy = true; err = null
                scope.launch { try { pid = ((call("party_create", "p_name" to name.ifBlank { "Party" }) as JSONObject).optString("id")) } catch (e: Exception) { err = humanErr(e) }; busy = false }
            }, contentAlignment = Alignment.Center) { Text("Create room", style = ts(17, FontWeight.SemiBold)) }
            Spacer(Modifier.height(34.dp))
            PillField(code, { code = it.uppercase().take(6) }, "Room code")
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(50)).border(2.dp, Color.White, RoundedCornerShape(50)).clickable(enabled = !busy && code.length >= 4) {
                busy = true; err = null
                scope.launch { try { val r = call("party_join", "p_code" to code) as JSONObject
                    if (r.optString("status") == "pending") { err = "Waiting for the host to approve you. Try again in a moment." ; Toast.makeText(ctx, "Join request sent", Toast.LENGTH_SHORT).show() } else pid = r.optString("id") } catch (e: Exception) { err = partyErr(e) }; busy = false }
            }, contentAlignment = Alignment.Center) { Text("Join room", style = ts(17, FontWeight.SemiBold)) }
            if (err != null) Text(err!!, Modifier.padding(top = 14.dp), style = ts(14, color = Color(0xFFFF6B8A)))
        }
    }
}

fun partyErr(e: Exception): String { val m = (e.message ?: "").lowercase(); return when {
    "party not found" in m -> "No room with that code."; "invite locked" in m -> "The host locked this room."; "party full" in m -> "This room is full."
    "removed" in m -> "You were removed from this room."; else -> humanErr(e) } }

@Composable
fun PartyRoom(pc: PlayerConnection?, pid: String, onLeft: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var st by remember { mutableStateOf<JSONObject?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }; var q by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Track>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    LaunchedEffect(pid, tick) { while (true) { try { st = call("party_state", "p_party" to pid) as? JSONObject } catch (e: Exception) { }; delay(1800) } }
    LaunchedEffect(q) { if (q.length < 2) { found = emptyList(); return@LaunchedEffect }; delay(350)
        try { found = ((Api.rpc("search_all", JSONObject().put("p_q", q).put("p_limit", 20)) as? JSONObject)?.optJSONArray("tracks")).tracks() } catch (e: Exception) { } }
    val s = st
    // keep this phone in step with the room
    LaunchedEffect(s?.optJSONObject("playback")?.optInt("version"), s?.optJSONObject("playback")?.optString("queue_id")) {
        val pb = s?.optJSONObject("playback") ?: return@LaunchedEffect; val tr = pb.optJSONObject("track")?.toTrack() ?: return@LaunchedEffect
        if (pc == null) return@LaunchedEffect
        if (pc.mediaMetadata.value?.id != tr.id) playTracks(pc, listOf(tr), 0, "Party")
        val pos = pb.optLong("position_ms"); if (kotlin.math.abs(pc.player.currentPosition - pos) > 2500) pc.player.seekTo(pos)
        if (pb.optBoolean("is_playing")) pc.player.play() else pc.player.pause()
    }
    LaunchedEffect(s?.optBoolean("ended")) { if (s?.optBoolean("ended") == true) { Toast.makeText(ctx, "The party ended", Toast.LENGTH_SHORT).show(); onLeft() } }
    if (s == null || s.optString("my_status") != "active") { Backdrop { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (s?.optString("my_status") == "pending") "Waiting for the host to let you in..." else "Joining...", style = ts(16, color = W.Dim)) } }; return }
    val admin = s.optString("my_role") == "admin"; val can = s.optBoolean("my_can_control"); val muted = s.optBoolean("my_muted")
    val pb = s.getJSONObject("playback"); val cur = pb.optJSONObject("track")?.toTrack(); val playing = pb.optBoolean("is_playing"); val qid = pb.optLong("queue_id")
    val members = s.getJSONArray("members"); val queue = s.getJSONArray("queue"); val msgs = s.getJSONArray("messages")
    val skip = s.getJSONObject("skip")
    // the one who can control moves the room to the next song when this one ends
    LaunchedEffect(can, qid) { while (can && qid != 0L) { delay(1500); if (pc?.player?.playbackState == androidx.media3.common.Player.STATE_ENDED) { try { call("party_next", "p_party" to pid, "p_ended_queue" to qid) } catch (e: Exception) { }; break } } }
    Backdrop(tint = Color(0xFF2B0B3A)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.arrow_back), null, Modifier.size(26.dp).clickable(onClick = onBack), tint = Color.White)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(s.optString("name"), style = ts(20, FontWeight.Bold), maxLines = 1)
                    Text("Code ${s.optString("code")}  ·  ${members.length()} listening", style = ts(13, color = Color(0xFFCFC8D9))) }
                Icon(painterResource(R.drawable.content_copy), null, Modifier.size(24.dp).clickable { (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("code", s.optString("code"))); Toast.makeText(ctx, "Code copied", Toast.LENGTH_SHORT).show() }, tint = Color.White)
                Spacer(Modifier.width(16.dp))
                Icon(painterResource(R.drawable.share), null, Modifier.size(24.dp).clickable { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Join my Wavy party. Code: ${s.optString("code")}"), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, tint = Color.White)
                if (admin) { Spacer(Modifier.width(16.dp)); Icon(painterResource(R.drawable.settings), null, Modifier.size(24.dp).clickable { settings = true }, tint = Color.White) }
            }
            // now playing
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x33FFFFFF)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(cur?.cover, 64.dp, 10)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(cur?.title ?: "Nothing playing yet", style = ts(17, FontWeight.SemiBold), maxLines = 1)
                    Text(cur?.artist ?: "Add a song to the queue", style = ts(13, color = Color(0xFFCFC8D9)), maxLines = 1) }
                if (cur != null) {
                    if (can) Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), null, Modifier.size(40.dp).clickable { scope.launch { try { call("party_set_playback", "p_party" to pid, "p_is_playing" to !playing, "p_position_ms" to (pc?.player?.currentPosition ?: 0L)); tick++ } catch (e: Exception) { } } }, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Icon(painterResource(R.drawable.skip_next), null, Modifier.size(34.dp).clickable { scope.launch { try { if (can) call("party_next", "p_party" to pid, "p_ended_queue" to qid) else call("party_skip_vote", "p_party" to pid); tick++ } catch (e: Exception) { } } }, tint = Color.White)
                }
            }
            if (cur != null && !can) Text("Skip votes ${skip.optInt("votes")}/${skip.optInt("needed")}", Modifier.padding(start = 20.dp, top = 6.dp), style = ts(12, color = Color(0xFFCFC8D9)))
            Row(Modifier.padding(16.dp, 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Queue", "Chat", "People").forEachIndexed { i, n -> Box(Modifier.clip(RoundedCornerShape(50)).background(if (tab == i) W.Pink else Color(0xFF2A2433)).clickable { tab = i }.padding(horizontal = 20.dp, vertical = 9.dp)) { Text(n, style = ts(15, FontWeight.SemiBold)) } }
            }
            when (tab) {
                0 -> Column(Modifier.weight(1f)) {
                    PillField(q, { q = it }, "Search a song to add")
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                        if (q.length >= 2) items(found) { t -> TrackRow(t, null) { scope.launch { try { call("party_add_song", "p_party" to pid, "p_video_id" to t.id); q = ""; tick++ } catch (e: Exception) { Toast.makeText(ctx, humanErr(e), Toast.LENGTH_SHORT).show() } } } }
                        else items((0 until queue.length()).map { queue.getJSONObject(it) }) { it2 ->
                            val t = it2.getJSONObject("track").toTrack(); val id = it2.optLong("id")
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Cover(t.cover, 52.dp, 8)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(t.title, style = ts(16, FontWeight.SemiBold), maxLines = 1); Text("${t.artist} · added by ${it2.optString("added_by_name")}", style = ts(12, color = Color(0xFFB9B2C4)), maxLines = 1) }
                                Text("${it2.optInt("votes")}", style = ts(14, color = if (it2.optBoolean("voted")) W.Pink else Color.White))
                                Icon(painterResource(R.drawable.arrow_upward), null, Modifier.padding(start = 6.dp).size(26.dp).clickable { scope.launch { try { call("party_vote", "p_party" to pid, "p_queue" to id); tick++ } catch (e: Exception) { } } }, tint = if (it2.optBoolean("voted")) W.Pink else Color.White)
                                if (can) Icon(painterResource(R.drawable.play), null, Modifier.padding(start = 8.dp).size(28.dp).clickable { scope.launch { try { call("party_play_now", "p_party" to pid, "p_queue" to id); tick++ } catch (e: Exception) { } } }, tint = Color.White)
                            } }
                    }
                }
                1 -> Column(Modifier.weight(1f)) {
                    LazyColumn(Modifier.weight(1f), reverseLayout = true, contentPadding = PaddingValues(16.dp)) {
                        items((0 until msgs.length()).reversed().map { msgs.getJSONObject(it) }) { m -> Text(buildAnnotatedString { withStyle(SpanStyle(color = W.Pink, fontWeight = FontWeight.SemiBold)) { append(m.optString("name") + "  ") }; append(m.optString("body")) }, Modifier.padding(vertical = 5.dp), style = ts(15)) }
                    }
                    if (!muted) Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { PillField(msg, { msg = it }, "Message") }
                        Icon(painterResource(R.drawable.arrow_upward), null, Modifier.padding(start = 10.dp).size(30.dp).clickable { if (msg.isNotBlank()) { val b = msg; msg = ""; scope.launch { try { call("party_send_message", "p_party" to pid, "p_body" to b); tick++ } catch (e: Exception) { } } } }, tint = W.Pink)
                    } else Text("You are muted by the host.", Modifier.padding(16.dp), style = ts(13, color = W.Dim))
                }
                else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items((0 until members.length()).map { members.getJSONObject(it) }) { m ->
                        val uid = m.optString("user_id"); var menu by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(W.Grad), contentAlignment = Alignment.Center) { Text(m.optString("name").take(1).uppercase(), style = ts(18, FontWeight.Bold)) }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(m.optString("name"), style = ts(16, FontWeight.SemiBold))
                                Text(when { m.optString("status") == "pending" -> "Wants to join"; m.optString("role") == "admin" -> "Host"; !m.optBoolean("can_control") -> "Listen only"; m.optBoolean("muted") -> "Muted"; else -> "Can control" }, style = ts(12, color = Color(0xFFB9B2C4))) }
                            if (admin && m.optString("role") != "admin") Box {
                                if (m.optString("status") == "pending") Row { listOf("approve" to "Approve", "reject" to "Reject").forEach { (a, l) -> Text(l, Modifier.padding(start = 12.dp).clickable { scope.launch { try { call("party_member_action", "p_party" to pid, "p_user" to uid, "p_action" to a); tick++ } catch (e: Exception) { } } }, style = ts(14, FontWeight.SemiBold, if (a == "approve") W.Pink else Color.White)) } }
                                else { Icon(painterResource(R.drawable.more_vert), null, Modifier.size(28.dp).clickable { menu = true }, tint = Color.White)
                                    DropdownMenu(menu, { menu = false }) {
                                        listOf(if (m.optBoolean("can_control")) "listen_only" to "Listen only" else "allow_control" to "Allow control", if (m.optBoolean("muted")) "unmute" to "Unmute chat" else "mute" to "Mute chat", "remove" to "Remove").forEach { (a, l) ->
                                            DropdownMenuItem(text = { Text(l) }, onClick = { menu = false; scope.launch { try { call("party_member_action", "p_party" to pid, "p_user" to uid, "p_action" to a); tick++ } catch (e: Exception) { } } }) } } }
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp).navigationBarsPadding(), contentAlignment = Alignment.Center) {
                Text(if (admin) "End party" else "Leave party", Modifier.clickable { scope.launch { try { if (admin) call("party_end", "p_party" to pid) else call("party_leave", "p_party" to pid); onLeft() } catch (e: Exception) { } } }, style = ts(15, FontWeight.SemiBold, Color(0xFFFF6B8A))) }
        }
        if (settings) {
            val set = s.getJSONObject("settings")
            AlertDialog(onDismissRequest = { settings = false }, confirmButton = { Text("Done", Modifier.clickable { settings = false }.padding(12.dp), color = W.Pink) }, title = { Text("Room settings") }, text = {
                Column {
                    listOf(Triple("invite_locked", "Lock invite code", set.optBoolean("invite_locked")), Triple("require_approval", "Approve people before they join", set.optBoolean("require_approval")), Triple("default_can_control", "Everyone can control playback", set.optBoolean("default_can_control"))).forEach { (k, l, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text(l, Modifier.weight(1f)); Switch(v, { nv -> scope.launch { try { call("party_settings", "p_party" to pid, "p_" + k to nv); tick++ } catch (e: Exception) { } } }) } }
                    Text("New code", Modifier.padding(top = 10.dp).clickable { scope.launch { try { call("party_settings", "p_party" to pid, "p_new_code" to true); tick++ } catch (e: Exception) { } } }, color = W.Pink)
                } })
        }
    }
}
