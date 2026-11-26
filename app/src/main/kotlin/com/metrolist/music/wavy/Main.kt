package com.metrolist.music.wavy

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import coil3.compose.AsyncImage
import coil3.toBitmap
import coil3.request.allowHardware
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

@Composable
fun WavyApp(pc: PlayerConnection?) {
    var stage by remember { mutableStateOf("splash") }
    val taste = remember { Taste() }
    LaunchedEffect(Unit) {
        delay(1500)
        stage = if (!Api.signedIn) "welcome" else try {
            if (onboardingDone()) "main" else "ob1"
        } catch (e: Exception) { if (Api.signedIn) "main" else "welcome" }
    }
    Box(Modifier.fillMaxSize().background(W.Bg)) {
        if (stage == "welcome" || stage == "login") {
            // WebView screens are drawn outside AnimatedContent: a WebView under a fade/slide layer renders black.
            key(stage) {
                if (stage == "welcome") WelcomeScreen { stage = "login" }
                else LoginScreen { needs -> stage = if (needs) "ob1" else "main" }
            }
        } else AnimatedContent(stage, transitionSpec = { (fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 14 }) togetherWith fadeOut(tween(250)) }, label = "stage") { s ->
            when (s) {
                "splash" -> SplashScreen()
                "welcome" -> WelcomeScreen { stage = "login" }
                "login" -> LoginScreen { needs -> stage = if (needs) "ob1" else "main" }
                "ob1" -> DobScreen(taste, { stage = "login" }) { stage = "ob2" }
                "ob2" -> GenderScreen(taste, { stage = "ob1" }) { stage = "ob3" }
                "ob3" -> LanguagesScreen(taste) { stage = "picker" }
                "picker" -> ArtistPicker(taste) { stage = "main" }
                else -> Shell(pc) { try { pc?.player?.let { it.pause(); it.stop(); it.clearMediaItems() } } catch (_: Exception) {}; Api.signOut(); stage = "welcome" }
            }
        }
    }
}

private val tabs = listOf("Home" to R.drawable.home_outlined, "Search" to R.drawable.search, "Music" to R.drawable.music_note, "Your Library" to R.drawable.library_music_outlined)

@Composable
fun Shell(pc: PlayerConnection?, onSignOut: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var full by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<String?>(null) }
    val meta by (pc?.mediaMetadata ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val ctx = LocalContext.current
    val micLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { WavyFx.refresh() }
    fun askMic() { if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }
    LaunchedEffect(pc) { if (pc != null) WavyFx.attach(ctx, pc) }
    // count a play after 15 s of listening so Favourites and recommendations learn
    LaunchedEffect(meta?.id) {
        val id = meta?.id ?: return@LaunchedEffect
        delay(15000)
        try { Api.post("plays", JSONObject().put("user_id", Api.uid).put("track_id", id)) } catch (e: Exception) { }
    }
    LaunchedEffect(Unit) { ArtistNav.open = { id, n -> page = "artist:" + (id ?: "") + ":" + n } }
    BackHandler(full) { full = false }
    BackHandler(!full && page != null) { page = when { page == "set:hub" -> null; page!!.startsWith("set:") -> "set:hub"; else -> null } }
    BackHandler(!full && tab != 0) { tab = 0 }
    val haze = dev.chrisbanes.haze.rememberHazeState()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (!Reduce.on) Modifier.hazeSource(haze) else Modifier)) {
            AnimatedContent(tab, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }, label = "tab") { t ->
                when (t) {
                    0 -> HomeScreen(pc, onSearch = { tab = 1 }, onSignOut = onSignOut, onProfile = { page = "editprofile" }, onParty = { page = "party" }, onSettings = { page = "set:hub" }, onPlaylist = { id, n -> page = "pl:$id:$n" })
                    1 -> SearchScreen(pc, onLang = { l -> page = "lang:" + l.lang + ":" + l.title }, onProfile = { page = "editprofile" }, initial = "")
                    2 -> MusicFeed(pc, onSearch = { tab = 1 })
                    3 -> LibraryScreen(pc, onProfile = { page = "editprofile" }, onPlaylist = { id, n -> page = "pl:$id:$n" }, onAddArtists = { page = "picker" })
                    else -> Backdrop { Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                        Text(tabs[t].first + " is coming in the next build.", style = ts(16, color = W.Dim)) } }
                }
            }
        }
        AnimatedVisibility(page != null, enter = slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(250)), exit = slideOutHorizontally(tween(250)) { it / 3 } + fadeOut(tween(200))) {
            val p = remember { page ?: "" }.let { page ?: it }
            Box(Modifier.fillMaxSize().background(W.Bg)) {
                when {
                    p.startsWith("artist:") -> { val parts = p.split(":", limit = 3); key(p) { ArtistScreen(parts[1].ifBlank { null }, parts[2], pc) { page = null } } }
                    p.startsWith("lang:") -> { val parts = p.split(":"); LangListScreen(parts[2], parts[1], pc) { page = null } }
                    p.startsWith("pl:") -> { val parts = p.split(":", limit = 3); TrackListScreen(parts[2], pc, { page = null }) { loadPlaylistTracks(parts[1].toLong()) } }
                    p == "picker" -> ArtistPicker(null) { page = null }
                    p == "party" -> PartyScreen(pc) { page = null }
                    p == "agent" -> AiChatScreen("agent", "Ultron", "I am Ultron. Name a mood, an artist or a language and I will choose your songs. Ask me how this app works, if you must.") { page = null }
                    p == "support" -> AiChatScreen("support", "Ultron", "Ultron here. Ask how anything in Wavy works. If something is broken, describe it and tap Send a bug report.") { page = "set:about" }
                    p == "profile" -> ProfileScreen(onBack = { page = null }, onSignOut = onSignOut, onSettings = { page = "set:hub" })
                    p == "editprofile" -> EditProfileRoute { page = null }
                    p == "set:hub" -> SettingsHub({ page = null }, onSignOut) { page = it }
                    p == "set:content" -> ContentSettings { page = "set:hub" }
                    p == "set:playback" -> PlaybackSettings { page = "set:hub" }
                    p == "set:data" -> DataSettings { page = "set:hub" }
                    p == "set:sound" -> SoundProfiles { page = "set:hub" }
                    p == "set:pro" -> ProSettings({ page = "set:hub" }) { askMic() }
                    p == "set:adv" -> AdvancedSettings(pc) { page = "set:hub" }
                    p == "set:apps" -> AppsSettings { page = "set:hub" }
                    p == "set:notif" -> NotifSettings { page = "set:hub" }
                    p == "set:about" -> AboutSettings({ page = "set:hub" }) { page = it }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter)) {
            val chat = page == "agent" || page == "support"
            if (meta != null && pc != null && tab != 2 && !chat) MiniPlayer(pc) { full = true }
            LaunchedEffect(tab) { ErrorLog.screen = listOf("home", "search", "music", "library", "profile").getOrElse(tab) { "tab" + tab } }
            if (pc != null) LaunchedEffect(pc) { pc.error.collect { e -> if (e != null) ErrorLog.report("playback", "Playback error " + e.errorCodeName, e) } }
if (!chat) NavBar(tab, haze, onDouble = { if (tabs[it].first == "Music") MusicRefresh.n++ }) { page = null; full = false; tab = it }
        }
        if (meta != null && pc != null && tab != 2 && !full && (page == "agent" || page == "support")) IslandPill(pc) { page = null; full = false; tab = 2 }
        AnimatedVisibility(full && pc != null, enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(200))) {
            if (pc != null) PlayerScreen(pc) { full = false }
        }
        StrobeOverlay()
    }
}

@Composable
fun NavBar(sel: Int, haze: dev.chrisbanes.haze.HazeState? = null, onDouble: (Int) -> Unit = {}, onSel: (Int) -> Unit) {
    var lastTap by remember { mutableStateOf(-1 to 0L) }
    val hap = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().then(if (haze != null) Modifier.hazeBlur(input = dev.chrisbanes.haze.HazeInput.Sources(haze), style = dev.chrisbanes.haze.blur.HazeBlurStyle { blurRadius(64.dp) }) else Modifier).then(if (haze != null) Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color(0x00141019), 0.3f to Color(0xE6141019), 1f to Color(0xF0141019))) else Modifier.background(Color(0xEB141019)))) {
        val tabW = maxWidth / tabs.size
        val ind by androidx.compose.animation.core.animateDpAsState(tabW * sel + (tabW - 36.dp) / 2, androidx.compose.animation.core.spring(dampingRatio = 0.65f, stiffness = 420f), label = "ind")
        Box(Modifier.offset(x = ind, y = 4.dp).width(36.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(W.Pink))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 12.dp, bottom = 8.dp)) {
            tabs.forEachIndexed { i, (name, icon) ->
                val on = i == sel
                val col by animateColorAsState(if (on) W.Pink else Color(0xFFBDB8C4), tween(200), label = "c")
                val sc by androidx.compose.animation.core.animateFloatAsState(if (on) 1.18f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 500f), label = "sc")
                Column(Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { hap.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove); val now = System.currentTimeMillis(); onSel(i); if (lastTap.first == i && now - lastTap.second < 400) { onDouble(i); lastTap = -1 to 0L } else lastTap = i to now }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(if (i == 0 && on) R.drawable.home_filled else icon), null, Modifier.size(28.dp).graphicsLayer { scaleX = sc; scaleY = sc }, tint = col)
                    Text(name, style = ts(12, FontWeight.Medium, col), maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(pc: PlayerConnection, onOpen: () -> Unit) {
    val meta by pc.mediaMetadata.collectAsState()
    val playing by pc.isPlaying.collectAsState()
    val m = meta ?: return
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(m.id) { while (true) { pos = pc.player.currentPosition; delay(500) } }
    val dur = pc.player.duration.takeIf { it > 0 } ?: (m.duration * 1000L)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var tint by remember { mutableStateOf(Color(0xFF3A1428)) }
    LaunchedEffect(m.thumbnailUrl) {
        val u = m.thumbnailUrl ?: return@LaunchedEffect
        try {
            val r = coil3.SingletonImageLoader.get(ctx).execute(coil3.request.ImageRequest.Builder(ctx).data(u).allowHardware(false).build())
            val b = (r as? coil3.request.SuccessResult)?.image?.toBitmap()
            if (b != null) {
                val p = androidx.palette.graphics.Palette.from(b).generate()
                val c = p.getDarkMutedColor(p.getMutedColor(p.getDominantColor(0xFF3A1428.toInt())))
                val hsv = FloatArray(3); android.graphics.Color.colorToHSV(c, hsv)
                hsv[2] = hsv[2].coerceAtMost(0.32f); hsv[1] = hsv[1].coerceAtMost(0.75f)
                tint = Color(android.graphics.Color.HSVToColor(hsv))
            }
        } catch (_: Exception) {}
    }
    val tintA by androidx.compose.animation.animateColorAsState(tint, label = "tint")
    Column(Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(22.dp)).background(tintA).clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(hiRes(m.thumbnailUrl), null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(W.Card), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(m.title, style = ts(16, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.artists.joinToString { it.name }, style = ts(13, color = Color(0xFFD6CCD3)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), null, Modifier.size(40.dp).clip(CircleShape).clickable { pc.togglePlayPause() }.padding(6.dp), tint = Color.White)
            Icon(painterResource(R.drawable.skip_next), null, Modifier.size(40.dp).clip(CircleShape).clickable { pc.seekToNext() }.padding(6.dp), tint = Color.White)
        }
        Box(Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp).fillMaxWidth().height(2.dp).background(Color(0x55FFFFFF))) {
            Box(Modifier.fillMaxWidth((pos.toFloat() / dur.coerceAtLeast(1)).coerceIn(0f, 1f)).fillMaxHeight().background(Color.White))
        }
    }
}

@Composable
fun Cover(url: String?, size: androidx.compose.ui.unit.Dp, radius: Int = 10, modifier: Modifier = Modifier) {
    AsyncImage(url, null, modifier.size(size).clip(RoundedCornerShape(radius.dp)).background(Brush.linearGradient(listOf(Color(0xFF3A2A44), Color(0xFF15121A)))), contentScale = ContentScale.Crop)
}

@Composable
fun HomeScreen(pc: PlayerConnection?, onSearch: () -> Unit, onSignOut: () -> Unit, onProfile: () -> Unit, onParty: () -> Unit = {}, onSettings: () -> Unit = {}, onPlaylist: (Long, String) -> Unit = { _, _ -> }) {
    var feed by remember { mutableStateOf<Feed?>(null) }
    var name by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        err = null
        try {
            feed = parseFeed(Api.rpc("home_feed") as JSONObject)
            val p = Api.get("profiles?id=eq.${Api.uid}&select=name") as? JSONArray
            name = p?.optJSONObject(0)?.optString("name").orEmpty().ifBlank { Api.email.substringBefore("@") }
        } catch (e: ApiError) { err = humanErr(e) } catch (e: Exception) { err = "Something went wrong." }
    }
    var cat by remember { mutableStateOf("All") }
    var tiles by remember { mutableStateOf<List<Triple<Long, String, String?>>>(emptyList()) }
    var chill by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(reload) {
        try {
            val a = (Api.rpc("profile_stats") as? JSONObject)?.optJSONArray("playlists")
            tiles = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }.filter { !it.optBoolean("is_favourite") }
                .map { Triple(it.getLong("id"), it.optString("name"), if (it.isNull("cover_url")) null else it.optString("cover_url")) }
        } catch (e: Exception) { }
    }
    LaunchedEffect(cat) {
        if (cat == "Chill" && chill.isEmpty()) try { chill = (Api.rpc("search_all", JSONObject().put("p_q", "chill").put("p_limit", 30)) as? JSONObject)?.optJSONArray("songs").tracks() } catch (e: Exception) { }
    }
    val st = rememberLazyListState()
    val shift = if (st.firstVisibleItemIndex == 0) st.firstVisibleItemScrollOffset else 4000
    Box(Modifier.fillMaxSize().background(W.Bg)) {
        Backdrop(Modifier.fillMaxWidth().height(1400.dp).offset { IntOffset(0, -shift) }, tint = Color(0xFF2B0B3A)) {}
        val f = feed
        LazyColumn(state = st, contentPadding = PaddingValues(bottom = 210.dp), modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            col {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Home", Modifier.weight(1f), style = ts(26, FontWeight.Bold))
                    listOf(Triple(R.drawable.search, onSearch, 0), Triple(R.drawable.group_outlined, onParty, 1), Triple(R.drawable.settings, onSettings, 2)).forEach { (ic, f, _) ->
                        Box(Modifier.padding(start = 6.dp).size(40.dp).glass(CircleShape).clickable(onClick = f), contentAlignment = Alignment.Center) {
                            Icon(painterResource(ic), null, Modifier.size(20.dp), tint = Color.White) }
                    }
                    Box(Modifier.padding(start = 6.dp).size(40.dp).clip(CircleShape).background(W.Grad).clickable(onClick = onProfile), contentAlignment = Alignment.Center) {
                        val hav = rememberAvatar(androidx.compose.ui.platform.LocalContext.current)
                        if (hav != null) androidx.compose.foundation.Image(hav.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        else Text(name.take(1).uppercase().ifEmpty { "W" }, style = ts(20, FontWeight.Bold)) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("All", "Music", "Chill", "Podcasts").forEach { c ->
                        Box(Modifier.height(34.dp).then(if (c == cat) Modifier.clip(RoundedCornerShape(50)).background(W.Pink) else Modifier.glass(RoundedCornerShape(50))).clickable { cat = c }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text(c, style = ts(13, if (c == cat) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1, softWrap = false)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (cat == "Podcasts") item {
                Text("Podcasts are not available yet. Music is what Wavy plays for now.", Modifier.padding(horizontal = 28.dp, vertical = 40.dp), style = ts(15, color = W.Dim))
            } else if (cat == "Chill") item {
                Column {
                    Text("Chill", Modifier.padding(start = 20.dp, bottom = 12.dp), style = ts(24, FontWeight.SemiBold))
                    if (chill.isEmpty()) Text("Loading...", Modifier.padding(24.dp), style = ts(14, color = W.Dim))
                    chill.forEachIndexed { i, t -> TrackRow(t, pc) { playTracks(pc, chill, i, "Chill") } }
                }
            } else {
                if (cat == "All" && f != null) item { QuickTiles(f.favouritesId, tiles, onPlaylist) }
                if (f != null) item { Hero(f, pc) }
                if (f == null) item {
                    Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                        if (err != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(err!!, style = ts(14, color = W.Dim)); Spacer(Modifier.height(12.dp))
                            Text("Try again", Modifier.clip(RoundedCornerShape(50)).background(W.Pink).clickable { reload++ }.padding(20.dp, 10.dp), style = ts(14, FontWeight.SemiBold))
                        } else Text("Loading...", style = ts(14, color = W.Dim))
                    }
                } else feedItems(f, pc)
            }
        }
    }
}

@Composable
fun QuickTiles(favId: Long?, tiles: List<Triple<Long, String, String?>>, onPlaylist: (Long, String) -> Unit) {
    val all = buildList { if (favId != null) add(Triple(favId, "Liked Songs", null as String?)); addAll(tiles) }.take(6)
    if (all.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        all.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (id, n, cover) ->
                    Row(Modifier.weight(1f).height(64.dp).glass(RoundedCornerShape(20.dp)).clickable { onPlaylist(id, if (n == "Liked Songs") "Favourites" else n) }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).background(W.Grad), contentAlignment = Alignment.Center) {
                            if (cover != null) Cover(cover, 64.dp, 0)
                            else Icon(painterResource(if (n == "Liked Songs") R.drawable.favorite else R.drawable.library_music), null, Modifier.size(26.dp), tint = Color.White)
                        }
                        Text(n, Modifier.padding(horizontal = 12.dp), style = ts(14, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
fun Hero(f: Feed, pc: PlayerConnection?) {
    val s = f.sections.firstOrNull { it.type == "made_for_you" && it.tracks.isNotEmpty() } ?: f.sections.firstOrNull { it.type == "trending" && it.tracks.isNotEmpty() } ?: return
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Picked For You", Modifier.padding(bottom = 10.dp), style = ts(20, FontWeight.Bold))
        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).clickable { playTracks(pc, s.tracks, 0, s.title) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(s.tracks.first().cover ?: s.cover, 96.dp, 18)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text("Playlist", Modifier.glass(RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 2.dp), style = ts(10, FontWeight.SemiBold, Color(0xFFFFD6E4)))
                Text(s.title, Modifier.padding(top = 6.dp), style = ts(16, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.tracks.take(3).joinToString(", ") { it.artist }, style = ts(12, color = Color(0xFFB9B2C4)), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(44.dp).glass(CircleShape, 2.2f), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.play), null, Modifier.size(24.dp), tint = Color.White) }
        }
    }
    Spacer(Modifier.height(26.dp))
}

private fun androidx.compose.foundation.lazy.LazyListScope.feedItems(f: Feed, pc: PlayerConnection?) {
    val first = f.sections.firstOrNull { it.type == "made_for_you" } ?: f.sections.firstOrNull { it.type == "trending" }
    if (first != null && first.tracks.isNotEmpty()) col {
        Column {
            Text("Jump into a session based on your tastes", Modifier.padding(horizontal = 20.dp), style = ts(15, color = Color(0xFFB9B2C4)))
            Text("Start listening", Modifier.padding(horizontal = 20.dp, vertical = 2.dp), style = ts(24, FontWeight.SemiBold))
            Spacer(Modifier.height(10.dp))
            first.tracks.take(3).forEachIndexed { i, t -> TrackRow(t, pc) { playTracks(pc, first.tracks, i, first.title) } }
            Spacer(Modifier.height(28.dp))
        }
    }
    col { MoodRow("Chill", "chill", pc) }
    col { MoodRow("Party and dance", "party", pc) }
    col { MoodRow("Top hits for you", "top", pc) }
    val mixes = f.sections.filter { it.type == "artist_mix" }
    if (mixes.isNotEmpty()) col {
        SectionHeader("To get you started")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(mixes) { _, s -> BigCard(s.tracks.firstOrNull()?.cover ?: s.cover, s.title, s.subtitle) { playTracks(pc, s.tracks, 0, s.title) } }
        }
        Spacer(Modifier.height(28.dp))
    }
    for (s in f.sections) {
        if (s.type == "artist_mix" || (s === first)) continue
        when {
            s.albums.isNotEmpty() -> col(s.id) {
                SectionHeader(s.title)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(s.albums) { _, a -> MiniCard(a.cover, a.title, a.artist) { playTracks(pc, a.tracks, 0, a.title) } }
                }
                Spacer(Modifier.height(28.dp))
            }
            s.artists.isNotEmpty() -> col(s.id) {
                SectionHeader(s.title)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(s.artists) { _, a ->
                        Column(Modifier.width(130.dp).clickable { ArtistNav.open?.invoke(a.id, a.name) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Cover(a.thumb, 130.dp, 65)
                            Text(a.name, Modifier.padding(top = 8.dp), style = ts(14, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
            s.type == "trending" -> col(s.id) {
                SectionHeader(s.title)
                s.tracks.take(5).forEachIndexed { i, t -> TrackRow(t, pc) { playTracks(pc, s.tracks, i, s.title) } }
                Spacer(Modifier.height(28.dp))
            }
            s.tracks.isNotEmpty() -> col(s.id) {
                if (s.type == "more_like") Row(Modifier.padding(start = 20.dp, bottom = 12.dp).clickable { ArtistNav.open?.invoke(null, s.title.removePrefix("More like ")) }, verticalAlignment = Alignment.CenterVertically) {
                    Cover(s.cover, 46.dp, 23)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("More like", style = ts(13, color = Color(0xFFB9B2C4)))
                        Text(s.title.removePrefix("More like "), style = ts(22, FontWeight.SemiBold))
                    }
                } else SectionHeader(s.title)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(s.tracks) { i, t -> MiniCard(t.cover, t.title, t.artist) { playTracks(pc, s.tracks, i, s.title) } }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(t: String) = Text(t, Modifier.padding(start = 20.dp, bottom = 14.dp), style = ts(24, FontWeight.SemiBold))

@Composable
fun BigCard(cover: String?, label: String, sub: String?, onClick: () -> Unit) {
    Column(Modifier.width(160.dp).clickable(onClick = onClick)) {
        Box {
            Cover(cover, 160.dp, 12)
            Text(label, Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFF2D7B0)).padding(horizontal = 8.dp, vertical = 3.dp),
                style = ts(13, FontWeight.SemiBold, Color(0xFF1A1018)), maxLines = 1)
        }
        if (sub != null) Text(sub, Modifier.padding(top = 8.dp), style = ts(12, color = Color(0xFFB9B2C4)), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MiniCard(cover: String?, title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = onClick)) {
        Cover(cover, 150.dp, 12)
        Text(title, Modifier.padding(top = 8.dp), style = ts(14, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, style = ts(12, color = Color(0xFFB9B2C4)), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TrackRow(t: Track, pc: PlayerConnection?, onPlay: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    var pls by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(t.cover, 62.dp, 8)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(t.title, style = ts(17, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, style = ts(14, color = Color(0xFFB9B2C4)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            Icon(painterResource(R.drawable.more_vert), null, Modifier.size(34.dp).clip(CircleShape).clickable { menu = true }.padding(4.dp), tint = Color(0xFFB9B2C4))
            if (menu || pick) {
                val dls by (WDl.util?.downloads ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())).collectAsState()
                val isDone = WDl.doneIds(dls).contains(t.id)
                androidx.compose.ui.window.Dialog(onDismissRequest = { menu = false; pick = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { menu = false; pick = false }) {
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color(0xF2121018)).clickable(enabled = false) {}.navigationBarsPadding().padding(bottom = 12.dp)) {
                            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(width = 44.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF)))
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Cover(t.cover, 56.dp, 10)
                                Column(Modifier.padding(start = 14.dp)) {
                                    Text(t.title, style = ts(18, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(t.artist, style = ts(14, color = Color(0xFFC9C1D3)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            @Composable fun R2(icon: Int, title: String, sub: String?, red: Boolean = false, go: () -> Unit) {
                                Row(Modifier.fillMaxWidth().clickable(onClick = go).padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painterResource(icon), null, Modifier.size(24.dp), tint = if (red) Color(0xFFFF6B8A) else Color(0xFFE9E4EE))
                                    Column(Modifier.padding(start = 20.dp)) {
                                        Text(title, style = ts(16, FontWeight.Medium, if (red) Color(0xFFFF6B8A) else Color.White))
                                        if (sub != null) Text(sub, style = ts(12, color = Color(0xFF9F98AB)))
                                    }
                                }
                            }
                            if (pick) {
                                Text("Add to playlist", Modifier.padding(horizontal = 24.dp, vertical = 4.dp), style = ts(14, color = Color(0xFF9F98AB)))
                                if (pls.isEmpty()) Text("No playlists yet", Modifier.padding(24.dp), style = ts(15, color = Color(0xFFB9B2C4)))
                                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                    pls.forEach { (id, name) ->
                                        R2(R.drawable.playlist_add, name, null) {
                                            pick = false
                                            scope.launch {
                                                val ok = try { Api.post("playlist_tracks", JSONObject().put("playlist_id", id).put("track_id", t.id).put("position", (System.currentTimeMillis() / 1000).toInt()).put("added_by", Api.uid)); true } catch (e: Exception) { false }
                                                Toast.makeText(ctx, if (ok) "Added to $name" else "Could not add. Try again.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            } else {
                                R2(R.drawable.skip_next, "Play next", "Plays right after this song") { menu = false; pc?.playNext(t.toMeta().toMediaItem()) }
                                R2(R.drawable.queue_music, "Add to queue", "Adds to the end of the queue") { menu = false; pc?.addToQueue(t.toMeta().toMediaItem()) }
                                R2(R.drawable.playlist_add, "Add to playlist", "Pick one of your playlists") {
                                    menu = false; pick = true
                                    scope.launch {
                                        try {
                                            val a = (Api.rpc("profile_stats") as? JSONObject)?.optJSONArray("playlists")
                                            pls = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }.filter { !it.optBoolean("is_favourite") }.map { it.optLong("id") to it.optString("name") }
                                        } catch (e: Exception) { }
                                    }
                                }
                                R2(R.drawable.favorite_border, "Like", "Add to Favourites") { menu = false; scope.launch { Toast.makeText(ctx, if (likeTrack(t.id)) "Added to Favourites" else "Could not add. Try again.", Toast.LENGTH_SHORT).show() } }
                                R2(R.drawable.share, "Share", "Send the song link") {
                                    menu = false
                                    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(android.content.Intent.EXTRA_TEXT, "${t.title} - ${t.artist}\nhttps://music.youtube.com/watch?v=${t.id}"), "Share").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                                if (isDone) R2(R.drawable.delete, "Remove download", "Delete the offline copy", red = true) { menu = false; WDl.remove(ctx, t.id); Toast.makeText(ctx, "Removed from Downloads", Toast.LENGTH_SHORT).show() }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun LazyListScope.col(key: Any? = null, content: @Composable ColumnScope.() -> Unit) { item(key = key) { Column(content = content) } }
