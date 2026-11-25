package com.metrolist.music.wavy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
        AnimatedContent(stage, transitionSpec = { (fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 14 }) togetherWith fadeOut(tween(250)) }, label = "stage") { s ->
            when (s) {
                "splash" -> SplashScreen()
                "welcome" -> WelcomeScreen { stage = "login" }
                "login" -> LoginScreen { needs -> stage = if (needs) "ob1" else "main" }
                "ob1" -> DobScreen(taste, { stage = "login" }) { stage = "ob2" }
                "ob2" -> GenderScreen(taste, { stage = "ob1" }) { stage = "ob3" }
                "ob3" -> LanguagesScreen(taste) { stage = "picker" }
                "picker" -> ArtistPicker(taste) { stage = "main" }
                else -> Shell(pc) { Api.signOut(); stage = "welcome" }
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
    BackHandler(full) { full = false }
    BackHandler(!full && page != null) { page = when { page == "set:hub" -> "profile"; page!!.startsWith("set:") -> "set:hub"; else -> null } }
    BackHandler(!full && tab != 0) { tab = 0 }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(tab, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }, label = "tab") { t ->
                when (t) {
                    0 -> HomeScreen(pc, onSearch = { tab = 1 }, onSignOut = onSignOut, onProfile = { page = "profile" }, onParty = { page = "party" }, onSettings = { page = "set:hub" }, onPlaylist = { id, n -> page = "pl:$id:$n" })
                    1 -> SearchScreen(pc, onLang = { l -> page = "lang:" + l.lang + ":" + l.title }, onProfile = { page = "profile" }, initial = "")
                    2 -> MusicFeed(pc)
                    3 -> LibraryScreen(pc, onProfile = { page = "profile" }, onPlaylist = { id, n -> page = "pl:$id:$n" }, onAddArtists = { page = "picker" })
                    else -> Backdrop { Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                        Text(tabs[t].first + " is coming in the next build.", style = ts(16, color = W.Dim)) } }
                }
            }
        }
        AnimatedVisibility(page != null, enter = slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(250)), exit = slideOutHorizontally(tween(250)) { it / 3 } + fadeOut(tween(200))) {
            val p = remember { page ?: "" }.let { page ?: it }
            Box(Modifier.fillMaxSize().background(W.Bg)) {
                when {
                    p.startsWith("lang:") -> { val parts = p.split(":"); TrackListScreen(parts[2], pc, { page = null }) {
                        (Api.rpc("language_top", JSONObject().put("p_lang", parts[1]).put("p_limit", 50)) as? JSONArray).tracks() } }
                    p.startsWith("pl:") -> { val parts = p.split(":", limit = 3); TrackListScreen(parts[2], pc, { page = null }) { loadPlaylistTracks(parts[1].toLong()) } }
                    p == "picker" -> ArtistPicker(null) { page = null }
                    p == "party" -> PartyScreen(pc) { page = null }
                    p == "profile" -> ProfileScreen(onBack = { page = null }, onSignOut = onSignOut, onSettings = { page = "set:hub" })
                    p == "set:hub" -> SettingsHub({ page = "profile" }) { page = it }
                    p == "set:content" -> ContentSettings { page = "set:hub" }
                    p == "set:playback" -> PlaybackSettings { page = "set:hub" }
                    p == "set:data" -> DataSettings { page = "set:hub" }
                    p == "set:sound" -> SoundProfiles { page = "set:hub" }
                    p == "set:pro" -> ProSettings({ page = "set:hub" }) { askMic() }
                    p == "set:adv" -> AdvancedSettings(pc) { page = "set:hub" }
                    p == "set:apps" -> AppsSettings { page = "set:hub" }
                    p == "set:notif" -> NotifSettings { page = "set:hub" }
                    p == "set:about" -> AboutSettings { page = "set:hub" }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter)) {
            if (meta != null && pc != null && tab != 2) MiniPlayer(pc) { full = true }
            NavBar(tab) { page = null; full = false; tab = it }
        }
        AnimatedVisibility(full && pc != null, enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(200))) {
            if (pc != null) PlayerScreen(pc) { full = false }
        }
    }
}

@Composable
fun NavBar(sel: Int, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(GlassBar).navigationBarsPadding().padding(top = 8.dp, bottom = 6.dp)) {
        tabs.forEachIndexed { i, (name, icon) ->
            val on = i == sel
            val col by animateColorAsState(if (on) W.Pink else Color(0xFFBDB8C4), tween(200), label = "c")
            Column(Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSel(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(if (i == 0 && on) R.drawable.home_filled else icon), null, Modifier.size(28.dp), tint = col)
                Text(name, style = ts(12, FontWeight.Medium, col), maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.height(3.dp).width(if (on) 36.dp else 0.dp).clip(RoundedCornerShape(2.dp)).background(W.Pink))
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
    Column(Modifier.padding(horizontal = 14.dp).glass(RoundedCornerShape(16.dp), 1.4f).clickable(onClick = onOpen)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(m.thumbnailUrl, null, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(W.Card), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(m.title, style = ts(16, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.artists.joinToString { it.name }, style = ts(13, color = Color(0xFFD6CCD3)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), null, Modifier.size(40.dp).clip(CircleShape).clickable { pc.togglePlayPause() }.padding(6.dp), tint = Color.White)
            Icon(painterResource(R.drawable.skip_next), null, Modifier.size(40.dp).clip(CircleShape).clickable { pc.seekToNext() }.padding(6.dp), tint = Color.White)
        }
        Box(Modifier.padding(horizontal = 10.dp).padding(bottom = 6.dp).fillMaxWidth().height(2.dp).background(Color(0x55FFFFFF))) {
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
        LazyColumn(state = st, contentPadding = PaddingValues(bottom = 190.dp), modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            col {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(W.Grad).clickable(onClick = onProfile), contentAlignment = Alignment.Center) {
                        Text(name.take(1).uppercase().ifEmpty { "W" }, style = ts(20, FontWeight.Bold)) }
                    Text("Home", Modifier.padding(start = 14.dp).weight(1f), style = ts(28, FontWeight.SemiBold))
                    listOf(Triple(R.drawable.search, onSearch, 0), Triple(R.drawable.group_outlined, onParty, 1), Triple(R.drawable.settings, onSettings, 2)).forEach { (ic, f, _) ->
                        Box(Modifier.padding(start = 8.dp).size(44.dp).glass(CircleShape).clickable(onClick = f), contentAlignment = Alignment.Center) {
                            Icon(painterResource(ic), null, Modifier.size(22.dp), tint = Color.White) }
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("All", "Music", "Chill", "Podcasts").forEach { c ->
                        Text(c, Modifier.glass(RoundedCornerShape(50), if (c == cat) 2.2f else 1f).clickable { cat = c }.padding(horizontal = 20.dp, vertical = 9.dp), style = ts(14, if (c == cat) FontWeight.SemiBold else FontWeight.Medium))
                    }
                }
                val shortName = name.trim().substringBefore(' ').trimEnd { it.isDigit() }.ifEmpty { name.take(12) }.take(14).replaceFirstChar { it.uppercase() }
                Row(Modifier.padding(start = 20.dp, top = 30.dp, bottom = 26.dp)) {
                    Text("Hey, ", style = ts(34, color = Color(0xFFCFC8D9))); Text(shortName, style = ts(34, FontWeight.Medium), maxLines = 1)
                }
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
                        Column(Modifier.width(130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
                if (s.type == "more_like") Row(Modifier.padding(start = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
        val dls by (WDl.util?.downloads ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap())).collectAsState()
        val dst = dls[t.id]?.state
        Icon(painterResource(if (dst == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) R.drawable.offline else R.drawable.download), null,
            Modifier.size(34.dp).clip(CircleShape).clickable {
                if (dst == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) { WDl.remove(ctx, t.id); Toast.makeText(ctx, "Removed from Downloads", Toast.LENGTH_SHORT).show() }
                else if (dst == null || dst == androidx.media3.exoplayer.offline.Download.STATE_FAILED) { WDl.start(t); Toast.makeText(ctx, "Downloading...", Toast.LENGTH_SHORT).show() }
            }.padding(5.dp),
            tint = when (dst) { androidx.media3.exoplayer.offline.Download.STATE_COMPLETED -> W.Pink; null -> Color(0xFFB9B2C4); else -> Color(0xFFFFB3D1) })
        Box {
            Icon(painterResource(R.drawable.more_vert), null, Modifier.size(34.dp).clip(CircleShape).clickable { menu = true }.padding(4.dp), tint = Color(0xFFB9B2C4))
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { menu = false; pc?.playNext(t.toMeta().toMediaItem()) })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menu = false; pc?.addToQueue(t.toMeta().toMediaItem()) })
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = {
                    menu = false; pick = true
                    scope.launch {
                        try {
                            val a = (Api.rpc("profile_stats") as? JSONObject)?.optJSONArray("playlists")
                            pls = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }.filter { !it.optBoolean("is_favourite") }.map { it.optLong("id") to it.optString("name") }
                        } catch (e: Exception) { }
                    }
                })
                DropdownMenuItem(text = { Text("Like") }, onClick = { menu = false; scope.launch { Toast.makeText(ctx, if (likeTrack(t.id)) "Added to Favourites" else "Could not add. Try again.", Toast.LENGTH_SHORT).show() } })
                DropdownMenuItem(text = { Text("Share") }, onClick = {
                    menu = false
                    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, "${t.title} - ${t.artist}\nhttps://music.youtube.com/watch?v=${t.id}"), "Share").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                })
            }
            DropdownMenu(pick, { pick = false }) {
                if (pls.isEmpty()) DropdownMenuItem(text = { Text("No playlists yet") }, onClick = { pick = false })
                pls.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = {
                        pick = false
                        scope.launch {
                            val ok = try { Api.post("playlist_tracks", JSONObject().put("playlist_id", id).put("track_id", t.id).put("position", (System.currentTimeMillis() / 1000).toInt()).put("added_by", Api.uid)); true } catch (e: Exception) { false }
                            Toast.makeText(ctx, if (ok) "Added to $name" else "Could not add. Try again.", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
        }
    }
}

fun LazyListScope.col(key: Any? = null, content: @Composable ColumnScope.() -> Unit) { item(key = key) { Column(content = content) } }
