package com.metrolist.music.wavy

import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.repeatOnLifecycle
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

val PARTY_THEMES = listOf("Wavy" to Color(0xFF2B0B3A), "Ocean" to Color(0xFF0B2A4A), "Forest" to Color(0xFF0E3A2A), "Sunset" to Color(0xFF4A1E0B), "Midnight" to Color(0xFF14142E))
fun partyTheme(key: String): Color = PARTY_THEMES.firstOrNull { it.first == S.s("party_theme_$key", "Wavy") }?.second ?: PARTY_THEMES[0].second

private suspend fun call(fn: String, vararg kv: Pair<String, Any?>): Any? {
    val o = JSONObject(); kv.forEach { o.put(it.first, it.second ?: JSONObject.NULL) }; return Api.rpc(fn, o)
}

@Composable
fun PartyScreen(pc: PlayerConnection?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var pid by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }
    var optLock by remember { mutableStateOf(false) }; var optApprove by remember { mutableStateOf(false) }; var optControl by remember { mutableStateOf(true) }; var optTheme by remember { mutableStateOf("Wavy") }; val optLocks = remember { mutableStateMapOf("add" to false, "skip" to false, "chat" to false, "voice" to false) }
    LaunchedEffect(Unit) { try { val a = call("party_mine") as? JSONArray; if (a != null && a.length() > 0 && a.getJSONObject(0).optString("status") == "active") pid = a.getJSONObject(0).optString("id") } catch (e: Exception) { } }
    if (pid != null) { PartyRoom(pc, pid!!, onLeft = { pid = null; step = 0 }, onBack = onBack); return }
    if (step == 1) {
        PartyBackdrop(tint = PARTY_THEMES.first { it.first == optTheme }.second) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp).verticalScroll(rememberScrollState())) {
                BackBar("Room settings", { step = 0 })
                Text("Set it up first. You can change all of this live once the room is running.", style = ts(14, color = Color(0xFFCFC8D9)))
                Spacer(Modifier.height(16.dp))
                PillField(name, { name = it }, "Party name")
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                    listOf(Triple("Everyone can control playback", optControl, { v: Boolean -> optControl = v }), Triple("Approve people before they join", optApprove, { v: Boolean -> optApprove = v }), Triple("Lock the invite code", optLock, { v: Boolean -> optLock = v })).forEach { (l, v, set) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(l, Modifier.weight(1f), style = ts(15)); Switch(v, { set(it) }) } }
                }
                Spacer(Modifier.height(18.dp))
                Text("Lock for members (you can still allow people one by one)", style = ts(13, color = Color(0xFFCFC8D9)))
                Spacer(Modifier.height(6.dp))
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                    listOf("add" to "Adding songs", "skip" to "Skip votes", "chat" to "Chat", "voice" to "Voice clips").forEach { (k, l) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(l, Modifier.weight(1f), style = ts(15)); Switch(optLocks[k] == true, { optLocks[k] = it }) } }
                }
                Spacer(Modifier.height(18.dp))
                Text("Room theme", style = ts(16, FontWeight.SemiBold)); Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PARTY_THEMES.forEach { (n, c) -> Box(Modifier.size(46.dp).clip(CircleShape).background(c).border(if (optTheme == n) 3.dp else 1.dp, if (optTheme == n) W.Pink else Color(0x55FFFFFF), CircleShape).clickable { optTheme = n }) }
                }
                Spacer(Modifier.height(28.dp))
                Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(50)).background(W.Pink).clickable(enabled = !busy) {
                    busy = true; err = null
                    scope.launch { try {
                        val id = ((call("party_create", "p_name" to name.ifBlank { "Party" }) as JSONObject).optString("id"))
                        try { call("party_settings", "p_party" to id, "p_invite_locked" to optLock, "p_require_approval" to optApprove, "p_default_can_control" to optControl) } catch (e: Exception) { }
                        optLocks.forEach { (k, on) -> if (on) try { call("party_set_lock", "p_party" to id, "p_feature" to k, "p_locked" to true) } catch (e: Exception) { } }
                        S.put("party_theme_$id", optTheme); pid = id
                    } catch (e: Exception) { err = humanErr(e) }; busy = false }
                }, contentAlignment = Alignment.Center) { Text(if (busy) "Creating..." else "Create room", style = ts(17, FontWeight.SemiBold)) }
                if (err != null) Text(err!!, Modifier.padding(top = 14.dp), style = ts(14, color = Color(0xFFFF6B8A)))
                Spacer(Modifier.height(40.dp))
            }
        }
        return
    }
    PartyBackdrop(tint = Color(0xFF2B0B3A)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp)) {
            BackBar("Party", onBack)
            Text("Listen together", style = ts(30, FontWeight.Bold)); Spacer(Modifier.height(6.dp))
            Text("Create a room or join one with a code. Everyone hears the same song at the same time.", style = ts(15, color = Color(0xFFCFC8D9)))
            Spacer(Modifier.height(28.dp))
            PillField(name, { name = it }, "Room name")
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(50)).background(W.Pink).clickable { step = 1 }, contentAlignment = Alignment.Center) { Text("Set up room", style = ts(17, FontWeight.SemiBold)) }
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
    // The room is its own playback space: the user's own queue is stopped on entry, and everything stops when the room is left or ends.
    val leaveRoom = { PartyState.active = false; PartyState.full = false; try { pc?.player?.let { it.pause(); it.clearMediaItems() } } catch (_: Exception) { }; onLeft() }
    LaunchedEffect(pid) { PartyState.active = true; try { pc?.player?.let { it.pause(); it.clearMediaItems() } } catch (_: Exception) { } }
    var st by remember { mutableStateOf<JSONObject?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf("") }; var q by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Track>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var showMic by remember { mutableStateOf(false) }
    var lockInfo by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(pid, tick) { try { lockInfo = call("party_locks", "p_party" to pid) as? JSONObject } catch (e: Exception) { } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { PartyState.full = false } }
    val partyLife = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(pid, tick) { partyLife.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { while (true) { try { st = call("party_state", "p_party" to pid) as? JSONObject } catch (e: Exception) { }; delay(if (PartyState.foreground) 1800L else 4500L) } } }
    LaunchedEffect(q) { if (q.length < 2) { found = emptyList(); return@LaunchedEffect }; delay(350)
        try { found = ((Api.rpc("search_all", JSONObject().put("p_q", q).put("p_limit", 20)) as? JSONObject)?.optJSONArray("tracks")).tracks() } catch (e: Exception) { }
        if (found.isEmpty()) found = try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.metrolist.innertube.YouTube.search(q, com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.mapNotNull { it.asTrack() } ?: emptyList() } } catch (e: Exception) { emptyList() } }
    val s = st
    // keep this phone in step with the room
    LaunchedEffect(s?.optJSONObject("playback")?.optInt("version"), s?.optJSONObject("playback")?.optString("queue_id")) {
        val pb = s?.optJSONObject("playback") ?: return@LaunchedEffect; val tr = pb.optJSONObject("track")?.toTrack() ?: return@LaunchedEffect
        if (pc == null) return@LaunchedEffect
        if (pc.mediaMetadata.value?.id != tr.id) playTracks(pc, listOf(tr), 0, "Party")
        val pos = pb.optLong("position_ms"); if (kotlin.math.abs(pc.player.currentPosition - pos) > 2500) pc.player.seekTo(pos)
        if (pb.optBoolean("is_playing")) pc.player.play() else pc.player.pause()
    }
    LaunchedEffect(s?.optBoolean("ended")) { if (s?.optBoolean("ended") == true) { Toast.makeText(ctx, "The party ended", Toast.LENGTH_SHORT).show(); leaveRoom() } }
    if (s == null || s.optString("my_status") != "active") { Backdrop { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (s?.optString("my_status") == "pending") "Waiting for the host to let you in..." else "Joining...", style = ts(16, color = W.Dim)) } }; return }
    val admin = s.optString("my_role") == "admin"; val can = s.optBoolean("my_can_control"); val muted = s.optBoolean("my_muted")
    val pb = s.getJSONObject("playback"); val cur = pb.optJSONObject("track")?.toTrack(); val playing = pb.optBoolean("is_playing"); val qid = pb.optLong("queue_id")
    val members = s.getJSONArray("members"); val queue = s.getJSONArray("queue"); val msgs = s.getJSONArray("messages")
    SideEffect { PartyState.title = cur?.title ?: ""; PartyState.artist = cur?.artist ?: ""; PartyState.thumb = cur?.cover; PartyState.playing = playing; PartyState.members = members.length() }
    val skip = s.getJSONObject("skip")
    // the one who can control moves the room to the next song when this one ends
    LaunchedEffect(can, qid) { while (can && qid != 0L) { delay(1500); if (pc?.player?.playbackState == androidx.media3.common.Player.STATE_ENDED) { try { call("party_next", "p_party" to pid, "p_ended_queue" to qid) } catch (e: Exception) { }; break } } }
    var themeTick by remember { mutableIntStateOf(0) }
    val bgPath = s.optString("bg_path").takeIf { it.isNotBlank() && it != "null" }
    var bgBmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(bgPath) {
        bgBmp = if (bgPath == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Api.storageGet("party-bg", bgPath)?.let { b -> try { android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() } catch (e: Exception) { null } } }
    }
    val pickBg = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            try {
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
                    var sample = 1; while (maxOf(o.outWidth, o.outHeight) / sample > 1600) sample *= 2
                    val bmp = ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) } ?: throw ApiError("Could not read the photo")
                    val out = java.io.ByteArrayOutputStream(); bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out); out.toByteArray()
                }
                val path = "$pid/bg${System.currentTimeMillis() / 1000}.jpg"
                Api.storagePut("party-bg", path, bytes, "image/jpeg")
                call("party_set_background", "p_party" to pid, "p_path" to path, "p_blur" to s.optInt("bg_blur")); tick++
            } catch (e: Exception) { Toast.makeText(ctx, "Could not set the background. Try a smaller photo.", Toast.LENGTH_SHORT).show() }
        }
    }
    var confirmEnd by remember { mutableStateOf(false) }
    PartyBackdrop(tint = remember(themeTick) { partyTheme(pid) }, bg = bgBmp, blur = s.optInt("bg_blur")) {
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
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Text(if (PartyState.full) "Exit full screen" else "Full screen", Modifier.padding(start = 20.dp, top = 6.dp).clickable { PartyState.full = !PartyState.full }, style = ts(12, color = W.Pink))
            Row(Modifier.padding(16.dp, 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Queue", "Chat", "People").forEachIndexed { i, n -> Box(Modifier.clip(RoundedCornerShape(50)).background(if (tab == i) W.Pink else Color(0x2EFFFFFF)).clickable { tab = i }.padding(horizontal = 20.dp, vertical = 9.dp)) { Text(n, style = ts(15, FontWeight.SemiBold)) } }
            }
            when (tab) {
                0 -> Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { PillField(q, { q = it }, "Search a song to add") }
                        Icon(painterResource(R.drawable.mic), null, tint = Color.White, modifier = Modifier.padding(end = 16.dp).size(26.dp).clickable { showMic = true })
                    }
                    if (showMic) MicScreen(onClose = { showMic = false }, onResult = { said -> showMic = false; q = said })
                    var plShow by remember { mutableStateOf(false) }
                    var myPls by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
                    Text("Add one of my playlists", Modifier.padding(start = 20.dp, top = 2.dp, bottom = 6.dp).clickable { plShow = !plShow; if (plShow) scope.launch { myPls = try { val arr = (Api.rpc("profile_stats") as? JSONObject)?.optJSONArray("playlists"); (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }.map { it.optLong("id") to (if (it.optBoolean("is_favourite")) "Favourites" else it.optString("name")) } } catch (e: Exception) { emptyList() } } }, style = ts(13, FontWeight.SemiBold, W.Pink))
                    if (plShow) Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp)) {
                        if (myPls.isEmpty()) Text("No playlists found.", style = ts(13, color = W.Dim))
                        myPls.forEach { (plId, plName) -> Text(plName, Modifier.fillMaxWidth().clickable { plShow = false; scope.launch { try { val n = call("party_add_playlist", "p_party" to pid, "p_playlist" to plId); tick++; Toast.makeText(ctx, "Added $n songs from $plName", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(ctx, humanErr(e), Toast.LENGTH_SHORT).show() } } }.padding(vertical = 9.dp), style = ts(15), maxLines = 1) }
                    }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                        if (q.length >= 2) items(found) { t -> TrackRow(t, null) { scope.launch { try { call("party_add_external_song", "p_party" to pid, "p_video_id" to t.id, "p_title" to t.title, "p_artist" to t.artist, "p_cover" to (t.cover ?: ""), "p_dur" to t.dur); q = ""; tick++ } catch (e: Exception) { Toast.makeText(ctx, humanErr(e), Toast.LENGTH_SHORT).show() } } } }
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
                    var reqMode by remember { mutableStateOf(false) }
                    LazyColumn(Modifier.weight(1f), reverseLayout = true, contentPadding = PaddingValues(16.dp)) {
                        items((0 until msgs.length()).reversed().map { msgs.getJSONObject(it) }) { m ->
                            val body = m.optString("body")
                            if (body.startsWith("\u266AREQ|")) {
                                val f = body.split("|"); val vid = f.getOrNull(1) ?: ""; val ti = f.getOrNull(2) ?: ""; val ar = f.getOrNull(3) ?: ""; val du = f.getOrNull(4)?.toIntOrNull() ?: 0
                                val cv = "https://i.ytimg.com/vi/$vid/mqdefault.jpg"
                                Column(Modifier.padding(vertical = 6.dp)) {
                                    Text(m.optString("name") + " suggests a song", style = ts(12, FontWeight.SemiBold, W.Pink))
                                    Row(Modifier.padding(top = 4.dp).fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Cover(cv, 52.dp, 8)
                                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(ti, style = ts(15, FontWeight.SemiBold), maxLines = 1); Text(ar, style = ts(12, color = Color(0xFFCFC8D9)), maxLines = 1) }
                                        Text("Add", Modifier.clip(RoundedCornerShape(50)).background(W.Pink).clickable { scope.launch { try { call("party_add_external_song", "p_party" to pid, "p_video_id" to vid, "p_title" to ti, "p_artist" to ar, "p_cover" to cv, "p_dur" to du); tick++; Toast.makeText(ctx, "Added to the queue", Toast.LENGTH_SHORT).show() } catch (e: Exception) { Toast.makeText(ctx, humanErr(e), Toast.LENGTH_SHORT).show() } } }.padding(16.dp, 8.dp), style = ts(13, FontWeight.SemiBold))
                                    }
                                }
                            } else if (body.startsWith("\uD83C\uDF99VOICE|")) {
                                val f = body.split("|"); val vp = f.getOrNull(1) ?: ""; val secs = f.getOrNull(2) ?: "0"
                                var vState by remember(vp) { mutableStateOf(0) }
                                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(m.optString("name"), style = ts(12, FontWeight.SemiBold, W.Pink), modifier = Modifier.padding(end = 10.dp))
                                    Row(Modifier.glass(RoundedCornerShape(50)).clickable(enabled = vState != 1) { vState = 1
                                        scope.launch { try {
                                            val bytes = Api.storageGet("party-voice", vp) ?: throw ApiError("gone")
                                            val fl = java.io.File(ctx.cacheDir, "vc_" + vp.hashCode() + ".m4a"); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fl.writeBytes(bytes) }
                                            val mp = android.media.MediaPlayer(); mp.setDataSource(fl.absolutePath); mp.prepare(); vState = 2; mp.setOnCompletionListener { it.release(); vState = 0 }; mp.start()
                                        } catch (e: Exception) { vState = 0; Toast.makeText(ctx, "This voice clip is not available.", Toast.LENGTH_SHORT).show() } }
                                    }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (vState == 1) "Loading..." else if (vState == 2) "Playing..." else "\u25B6 Voice clip ${secs}s", style = ts(14, FontWeight.Medium))
                                    }
                                }
                            } else Text(buildAnnotatedString { withStyle(SpanStyle(color = W.Pink, fontWeight = FontWeight.SemiBold)) { append(m.optString("name") + "  ") }; append(body) }, Modifier.padding(vertical = 5.dp), style = ts(15))
                        }
                    }
                    if (reqMode) {
                        Box(Modifier.padding(horizontal = 12.dp)) { PillField(q, { q = it }, "Search a song to suggest") }
                        LazyColumn(Modifier.heightIn(max = 240.dp)) {
                            items(if (q.length >= 2) found else emptyList()) { t -> TrackRow(t, null) {
                                val body = "\u266AREQ|${t.id}|${t.title.take(70).replace("|", " ")}|${t.artist.take(50).replace("|", " ")}|${t.dur}"
                                reqMode = false; q = ""
                                scope.launch { try { call("party_send_message", "p_party" to pid, "p_body" to body); tick++ } catch (e: Exception) { Toast.makeText(ctx, humanErr(e), Toast.LENGTH_SHORT).show() } } } }
                        }
                    }
                    if (!muted) Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.music_note), null, Modifier.padding(end = 10.dp).size(28.dp).clickable { reqMode = !reqMode; q = "" }, tint = if (reqMode) W.Pink else Color.White)
                        var rec by remember { mutableStateOf<android.media.MediaRecorder?>(null) }
                        var recFile by remember { mutableStateOf<java.io.File?>(null) }
                        var recStart by remember { mutableLongStateOf(0L) }
                        fun stopAndSend() {
                            val r = rec ?: return; val fl = recFile ?: return; rec = null
                            val secs = ((System.currentTimeMillis() - recStart) / 1000).toInt().coerceAtLeast(1)
                            try { r.stop() } catch (e: Exception) { }; r.release()
                            if (secs < 1) return
                            scope.launch { try {
                                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fl.readBytes() }
                                val path = "$pid/${System.currentTimeMillis() / 1000}_${Api.uid.take(8)}.m4a"
                                Api.storagePut("party-voice", path, bytes, "audio/mp4")
                                call("party_send_message", "p_party" to pid, "p_body" to "\uD83C\uDF99VOICE|$path|$secs"); tick++
                            } catch (e: Exception) { Toast.makeText(ctx, "Could not send the voice clip.", Toast.LENGTH_SHORT).show() } }
                        }
                        fun startRec() {
                            try {
                                val fl = java.io.File(ctx.cacheDir, "rec_${System.currentTimeMillis()}.m4a"); recFile = fl
                                val r = android.media.MediaRecorder(); r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC); r.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                                r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC); r.setAudioEncodingBitRate(48000); r.setAudioSamplingRate(32000); r.setMaxDuration(60000); r.setOutputFile(fl.absolutePath)
                                r.setOnInfoListener { _, what, _ -> if (what == android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopAndSend() }
                                r.prepare(); r.start(); rec = r; recStart = System.currentTimeMillis()
                            } catch (e: Exception) { rec = null; Toast.makeText(ctx, "Could not start recording.", Toast.LENGTH_SHORT).show() }
                        }
                        val micPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok -> if (ok) startRec() }
                        DisposableEffect(Unit) { onDispose { try { rec?.stop() } catch (e: Exception) { }; rec?.release() } }
                        Icon(painterResource(R.drawable.mic), null, Modifier.padding(end = 10.dp).size(28.dp).clickable {
                            if (rec != null) stopAndSend()
                            else if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) startRec() else micPerm.launch(android.Manifest.permission.RECORD_AUDIO)
                        }, tint = if (rec != null) Color(0xFFFF4D6D) else Color.White)
                        Box(Modifier.weight(1f)) { PillField(msg, { msg = it }, "Message") }
                        Icon(painterResource(R.drawable.arrow_upward), null, Modifier.padding(start = 10.dp).size(30.dp).clickable { if (msg.isNotBlank()) { val b = msg; msg = ""; scope.launch { try { call("party_send_message", "p_party" to pid, "p_body" to b); tick++ } catch (e: Exception) { Toast.makeText(ctx, humanErr(e), Toast.LENGTH_SHORT).show() } } } }, tint = W.Pink)
                    } else Text("You are muted by the host.", Modifier.padding(16.dp), style = ts(13, color = W.Dim))
                }
                else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items((0 until members.length()).map { members.getJSONObject(it) }) { m ->
                        val uid = m.optString("user_id"); var menu by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(W.Grad), contentAlignment = Alignment.Center) {
                                val pav = if (uid == Api.uid) rememberAvatar(androidx.compose.ui.platform.LocalContext.current) else null
                                if (pav != null) androidx.compose.foundation.Image(pav.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                else Text(m.optString("name").take(1).uppercase(), style = ts(18, FontWeight.Bold)) }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(m.optString("name"), style = ts(16, FontWeight.SemiBold))
                                Text(when { m.optString("status") == "pending" -> "Wants to join"; m.optString("role") == "admin" -> "Host"; !m.optBoolean("can_control") -> "Listen only"; m.optBoolean("muted") -> "Muted"; else -> "Can control" }, style = ts(12, color = Color(0xFFB9B2C4))) }
                            if (admin && m.optString("role") != "admin") Box {
                                if (m.optString("status") == "pending") Row { listOf("approve" to "Approve", "reject" to "Reject").forEach { (a, l) -> Text(l, Modifier.padding(start = 12.dp).clickable { scope.launch { try { call("party_member_action", "p_party" to pid, "p_user" to uid, "p_action" to a); tick++ } catch (e: Exception) { } } }, style = ts(14, FontWeight.SemiBold, if (a == "approve") W.Pink else Color.White)) } }
                                else { Icon(painterResource(R.drawable.more_vert), null, Modifier.size(28.dp).clickable { menu = true }, tint = Color.White)
                                    DropdownMenu(menu, { menu = false }) {
                                        listOf(if (m.optBoolean("can_control")) "listen_only" to "Listen only" else "allow_control" to "Allow control", if (m.optBoolean("muted")) "unmute" to "Unmute chat" else "mute" to "Mute chat", "remove" to "Remove").forEach { (a, l) ->
                                            DropdownMenuItem(text = { Text(l) }, onClick = { menu = false; scope.launch { try { call("party_member_action", "p_party" to pid, "p_user" to uid, "p_action" to a); tick++ } catch (e: Exception) { } } }) }
                                        val gr = lockInfo?.optJSONObject("members")?.optJSONArray(uid)
                                        val has = (0 until (gr?.length() ?: 0)).any { gr!!.optString(it) == "add" }
                                        DropdownMenuItem(text = { Text(if (has) "Take back song adding" else "Let add songs") }, onClick = { menu = false; scope.launch { try { call("party_grant", "p_party" to pid, "p_user" to uid, "p_feature" to "add", "p_on" to !has); tick++ } catch (e: Exception) { } } }) } }
                            }
                        }
                    }
                }
            }
            if (tab != 1) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 168.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                @Composable fun Ctl(icon: Int, label: String, on: Boolean = false, red: Boolean = false, click: () -> Unit) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(54.dp).clip(CircleShape).background(if (red) Color(0xFFE5354F) else if (on) W.Pink else Color(0x33FFFFFF)).clickable(onClick = click), contentAlignment = Alignment.Center) { Icon(painterResource(icon), null, Modifier.size(26.dp), tint = Color.White) }
                    Text(label, Modifier.padding(top = 4.dp), style = ts(11, color = Color(0xFFCFC8D9))) }
                Ctl(R.drawable.queue_music, "Queue", tab == 0) { tab = 0 }
                Ctl(R.drawable.chat_bubble, "Chat", tab == 1) { tab = 1 }
                Ctl(R.drawable.group, "People", tab == 2) { tab = 2 }
                Ctl(R.drawable.share, "Invite") { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Join my Wavy party. Code: ${s.optString("code")}"), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                Ctl(R.drawable.close, if (admin) "End" else "Leave", red = true) { if (admin) confirmEnd = true else scope.launch { try { call("party_leave", "p_party" to pid); leaveRoom() } catch (e: Exception) { } } }
            }
        }
        if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false }, title = { Text("End party for everyone?") }, text = { Text("Everyone in the room is removed and the room closes.") },
            confirmButton = { Text("End party", Modifier.clickable { confirmEnd = false; scope.launch { try { Api.storageClear("party-voice", pid.toString()); call("party_end", "p_party" to pid); leaveRoom() } catch (e: Exception) { } } }.padding(12.dp), color = Color(0xFFFF6B8A)) },
            dismissButton = { Text("Cancel", Modifier.clickable { confirmEnd = false }.padding(12.dp), color = Color.White) })
        if (settings) {
            val set = s.getJSONObject("settings")
            AlertDialog(onDismissRequest = { settings = false }, confirmButton = { Text("Done", Modifier.clickable { settings = false }.padding(12.dp), color = W.Pink) }, title = { Text("Room settings") }, text = {
                Column {
                    listOf(Triple("invite_locked", "Lock invite code", set.optBoolean("invite_locked")), Triple("require_approval", "Approve people before they join", set.optBoolean("require_approval")), Triple("default_can_control", "Everyone can control playback", set.optBoolean("default_can_control"))).forEach { (k, l, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text(l, Modifier.weight(1f)); Switch(v, { nv -> scope.launch { try { call("party_settings", "p_party" to pid, "p_" + k to nv); tick++ } catch (e: Exception) { } } }) } }
                    val lk = lockInfo?.optJSONObject("locks")
                    Text("Lock for members (you can still allow people one by one)", Modifier.padding(top = 10.dp, bottom = 2.dp), style = ts(12, color = Color(0xFFB9B2C4)))
                    listOf("add" to "Adding songs", "skip" to "Skip votes", "chat" to "Chat", "voice" to "Voice clips").forEach { (k, l) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Text(l, Modifier.weight(1f)); Switch(lk?.optBoolean(k) == true, { nv -> scope.launch { try { call("party_set_lock", "p_party" to pid, "p_feature" to k, "p_locked" to nv); tick++ } catch (e: Exception) { Toast.makeText(ctx, "Could not change this", Toast.LENGTH_SHORT).show() } } }) } }
                    Text("Room background", Modifier.padding(top = 10.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Choose photo", Modifier.clickable { pickBg.launch("image/*") }, color = W.Pink)
                        if (bgPath != null) Text("Remove", Modifier.clickable { scope.launch { try { call("party_reset_background", "p_party" to pid); tick++ } catch (e: Exception) { } } }, color = W.Pink)
                    }
                    if (bgPath != null) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf("Sharp" to 0, "Soft" to 40, "Blurry" to 90).forEach { (l, v) -> Text(l, Modifier.clickable { scope.launch { try { call("party_set_background_blur", "p_party" to pid, "p_blur" to v); tick++ } catch (e: Exception) { } } }, color = if (s.optInt("bg_blur") == v) W.Pink else Color(0xFFB9B2C4)) }
                    }
                    Text("Room theme", Modifier.padding(top = 10.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PARTY_THEMES.forEach { (n, c) -> Box(Modifier.size(36.dp).clip(CircleShape).background(c).border(if (S.s("party_theme_$pid", "Wavy") == n) 3.dp else 1.dp, if (S.s("party_theme_$pid", "Wavy") == n) W.Pink else Color(0x55FFFFFF), CircleShape).clickable { S.put("party_theme_$pid", n); themeTick++ }) } }
                    Text("New code", Modifier.padding(top = 10.dp).clickable { scope.launch { try { call("party_settings", "p_party" to pid, "p_new_code" to true); tick++ } catch (e: Exception) { } } }, color = W.Pink)
                } })
        }
    }
}


/** Party screens sit on pure black. The room colour is only a faint glow at the top. */
@Composable
fun PartyBackdrop(tint: Color, bg: androidx.compose.ui.graphics.ImageBitmap? = null, blur: Int = 0, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color.Black).drawBehind {
        drawRect(Brush.verticalGradient(listOf(tint.copy(alpha = 0.16f), Color.Transparent), 0f, size.height * 0.4f))
    }) {
        if (bg != null) {
            androidx.compose.foundation.Image(bg, null, Modifier.fillMaxSize().then(if (blur > 0) Modifier.blur((blur / 4).dp) else Modifier), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color(0x99000000)))
        } else WallpaperLayer("party")
        content()
    }
}


/** True while this phone is in a Party room. The normal mini player is replaced by the island pill, and room songs are the only ones playing. */
object PartyState { var foreground = true; var active by mutableStateOf(false); var full by mutableStateOf(false)
    var title by mutableStateOf(""); var artist by mutableStateOf(""); var thumb by mutableStateOf<String?>(null); var playing by mutableStateOf(false); var members by mutableIntStateOf(0) }

@Composable
fun RoomPill(onGo: () -> Unit) {
    val ps = PartyState
    androidx.compose.animation.AnimatedVisibility(ps.active, enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(0.75f, 420f)) { -it } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)),
        exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(200)) { -it } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))) {
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp, start = 12.dp, end = 12.dp), contentAlignment = Alignment.TopCenter) {
            Row(Modifier.widthIn(max = 380.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Color.Black).clickable(onClick = onGo).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                coil3.compose.AsyncImage(ps.thumb, null, Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0x22FFFFFF)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(if (ps.title.isEmpty()) "Party room" else ps.title, style = ts(13, androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text((if (ps.playing) "Playing" else "Paused") + " · " + ps.members + " in room" + (if (ps.artist.isNotEmpty()) " · " + ps.artist else ""), style = ts(11, color = Color(0xFFB9B2C4)), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Text("Go to room", Modifier.clip(RoundedCornerShape(50)).background(W.Pink).padding(horizontal = 10.dp, vertical = 6.dp), style = ts(11, androidx.compose.ui.text.font.FontWeight.SemiBold))
            }
        }
    }
}
