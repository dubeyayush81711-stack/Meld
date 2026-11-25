package com.metrolist.music.wavy

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

@Composable
fun SplashScreen() {
    val s = remember { Animatable(0.6f) }; val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { a.animateTo(1f, tween(500)) }
        s.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 120f))
    }
    val pulse = rememberInfiniteTransition(label = "p").animateFloat(1f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pp")
    Box(Modifier.fillMaxSize().background(W.Bg), contentAlignment = Alignment.Center) {
        Box(Modifier.size(260.dp).scale(pulse.value).alpha(a.value * 0.5f)
            .background(Brush.radialGradient(listOf(Color(0x66FF2D78), Color.Transparent)), CircleShape))
        Image(painterResource(R.mipmap.ic_launcher_static), null, Modifier.size(112.dp).scale(s.value).alpha(a.value).clip(RoundedCornerShape(28.dp)))
    }
}

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val tr = rememberInfiniteTransition(label = "w")
    val float = tr.animateFloat(-8f, 8f, infiniteRepeatable(tween(2600, easing = EaseInOutSine), RepeatMode.Reverse), label = "f")
    val tw = tr.animateFloat(0.3f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "t")
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val rise by animateFloatAsState(if (shown) 0f else 40f, tween(700, easing = FastOutSlowInEasing), label = "r")
    val fade by animateFloatAsState(if (shown) 1f else 0f, tween(700), label = "fa")
    Backdrop(streaks = false, tint = Color(0xFF2A0B3A)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 26.dp)) {
            Text("Wavy", Modifier.fillMaxWidth().padding(top = 18.dp), style = ts(34, FontWeight.SemiBold, W.Pink2).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, letterSpacing = 3.sp), textAlign = TextAlign.Center)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.welcome_headphones), null, Modifier.fillMaxWidth(0.92f).offset(y = float.value.dp), contentScale = ContentScale.Fit)
                Text("\u2726", Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 20.dp).alpha(tw.value), style = ts(34, color = Color(0xFFEDE6FF)))
                Text("\u2726", Modifier.align(Alignment.BottomEnd).padding(end = 30.dp, bottom = 30.dp).alpha(1.3f - tw.value), style = ts(22, color = Color(0xFFEDE6FF)))
            }
            Column(Modifier.offset(y = rise.dp).alpha(fade)) {
                Text("Millions Of Songs\nWaiting For You.", style = ts(32, FontWeight.SemiBold).copy(lineHeight = 42.sp))
                Spacer(Modifier.height(14.dp))
                Text("Discover New Artists, Trending Tracks,\nAnd Playlists Made For You.", style = ts(16, color = Color(0xFFCFC8D9)).copy(lineHeight = 26.sp))
                Spacer(Modifier.height(44.dp))
                Box(Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFFE91E63), Color(0xFFC2185B)))).clickable(onClick = onStart)
                    .padding(horizontal = 52.dp, vertical = 20.dp)) {
                    Text("Get Started   \u00BB", style = ts(20, FontWeight.Medium))
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
fun PillField(value: String, onChange: (String) -> Unit, hint: String, password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text) {
    var show by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(58.dp).border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(50)).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, style = ts(15, FontWeight.Medium, Color(0xCCFFFFFF)))
            BasicTextField(value, onChange, singleLine = true, textStyle = ts(15, FontWeight.Medium), cursorBrush = SolidColor(W.Pink),
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
                visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None, modifier = Modifier.fillMaxWidth())
        }
        if (password) Icon(painterResource(if (show) R.drawable.visibility_off else R.drawable.visibility), null, Modifier.size(22.dp).clickable { show = !show }, tint = Color(0xB3FFFFFF))
    }
}

@Composable
fun LoginScreen(onDone: (needsPicker: Boolean) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }
    var signup by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }; var rem by remember { mutableStateOf(Api.remember()) }
    Backdrop(tint = Color(0xFF2A0B30)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(46.dp))
            Image(painterResource(R.mipmap.ic_launcher_static), null, Modifier.size(82.dp).clip(RoundedCornerShape(22.dp)))
            Spacer(Modifier.height(10.dp))
            Text("wavy", style = ts(64, FontWeight.SemiBold).copy(letterSpacing = (-2).sp))
            Spacer(Modifier.weight(1f))
            PillField(email, { email = it; err = null }, "Email", keyboard = KeyboardType.Email)
            Spacer(Modifier.height(16.dp))
            PillField(pw, { pw = it; err = null }, "Password", password = true)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(5.dp)).clip(RoundedCornerShape(5.dp))
                    .background(if (rem) W.Pink else Color.Transparent).clickable { rem = !rem; Api.setRemember(rem) }, contentAlignment = Alignment.Center) {
                    if (rem) Icon(painterResource(R.drawable.check), null, Modifier.size(16.dp), tint = Color.White)
                }
                Spacer(Modifier.width(10.dp)); Text("Remember me", style = ts(13, color = Color(0xCCFFFFFF)))
                Spacer(Modifier.weight(1f))
                Text("Forgot password?", Modifier.clickable {
                    if (email.isBlank()) { err = "Enter your email first." } else scope.launch {
                        try { Api.recover(email); Toast.makeText(ctx, "If this email has an account, a reset link was sent.", Toast.LENGTH_LONG).show() } catch (e: ApiError) { err = humanErr(e) }
                    }
                }, style = ts(13, color = Color(0xCCFFFFFF)))
            }
            if (err != null) { Spacer(Modifier.height(10.dp)); Text(err!!, style = ts(13, color = Color(0xFFFF6B8A)), textAlign = TextAlign.Center) }
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(50)).background(W.Pink).clickable(enabled = !busy) {
                if (email.isBlank() || pw.length < 6) { err = "Enter your email and a password of at least 6 characters."; return@clickable }
                busy = true; err = null
                scope.launch {
                    try {
                        if (signup) Api.signUp(email, pw) else Api.signIn(email, pw)
                        onDone(!onboardingDone())
                    } catch (e: ApiError) { err = humanErr(e); busy = false }
                }
            }, contentAlignment = Alignment.Center) { Text(if (busy) "Please wait..." else if (signup) "Create account" else "Sign in", style = ts(17, FontWeight.Medium)) }
            Spacer(Modifier.height(30.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(Color(0x55FFFFFF)))
                Text("  or continue with  ", style = ts(16, FontWeight.Medium, Color(0xE6FFFFFF)))
                Box(Modifier.weight(1f).height(1.dp).background(Color(0x55FFFFFF)))
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.size(56.dp).border(1.dp, Color(0x55FFFFFF), CircleShape).clip(CircleShape).clickable {
                Toast.makeText(ctx, "Google sign-in is not switched on yet. Use email for now.", Toast.LENGTH_LONG).show()
            }, contentAlignment = Alignment.Center) { Text("G", style = ts(24, FontWeight.Bold)) }
            Spacer(Modifier.weight(1f))
            Row {
                Text(if (signup) "Already have an account? " else "New to Wavy? ", style = ts(14, FontWeight.Medium))
                Text(if (signup) "LOG IN" else "SIGN UP", Modifier.clickable { signup = !signup; err = null }, style = ts(14, FontWeight.SemiBold, W.Pink))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ArtistPicker(taste: Taste?, onDone: () -> Unit) {
    var chip by remember { mutableStateOf<String?>(null) }
    val codes = remember { taste?.codes() ?: emptyList() }
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var limit by remember { mutableIntStateOf(30) }
    var artists by remember { mutableStateOf<List<ArtistItem>>(emptyList()) }
    val picked = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(q, limit, chip) {
        if (q.isNotEmpty()) delay(300)
        try {
            val sel = if (chip != null) listOf(chip!!) else codes
            artists = if (sel.isEmpty()) (Api.rpc("search_artists", JSONObject().put("p_q", q).put("p_limit", if (q.isEmpty()) limit else 60)) as? JSONArray).artistItems()
            else (Api.rpc("search_artists_lang", JSONObject().put("p_q", q).put("p_limit", if (q.isEmpty()) limit else 60).put("p_langs", JSONArray(sel))) as? JSONArray).artistItems()
        }
        catch (e: ApiError) { err = humanErr(e) }
    }
    Backdrop(streaks = false, tint = Color(0xFF3A1060)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Text("Choose 3 or more\nartists you like.", Modifier.padding(start = 22.dp, top = 28.dp, end = 22.dp), style = ts(30, FontWeight.Bold).copy(lineHeight = 38.sp))
            Row(Modifier.padding(22.dp, 20.dp, 22.dp, 14.dp).fillMaxWidth().height(54.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.search), null, Modifier.size(22.dp), tint = Color(0xFF333333))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Search", style = ts(18, FontWeight.SemiBold, Color(0xFF444444)))
                    BasicTextField(q, { q = it }, singleLine = true, textStyle = ts(18, FontWeight.SemiBold, Color(0xFF111111)), cursorBrush = SolidColor(W.Pink), modifier = Modifier.fillMaxWidth())
                }
            }
            androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Box(Modifier.clip(RoundedCornerShape(50)).background(if (chip == null) W.Pink else Color(0x2EFFFFFF)).clickable { chip = null }.padding(horizontal = 22.dp, vertical = 10.dp)) { Text("For You", style = ts(16, FontWeight.SemiBold)) } }
                items(codes.size) { i -> val c = codes[i]
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (chip == c) W.Pink else Color(0x2EFFFFFF)).clickable { chip = c }.padding(horizontal = 22.dp, vertical = 10.dp)) {
                        Text(OB_LANGS.first { it.second == c }.let { if (c == "south") "South" else it.first }, style = ts(16, FontWeight.SemiBold)) } }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.weight(1f)) {
                LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(artists, key = { it.id }) { a ->
                        val on = a.id in picked
                        val sc by animateFloatAsState(if (on) 0.92f else 1f, spring(0.5f), label = "sc")
                        Column(Modifier.clickable { if (on) picked.remove(a.id) else picked.add(a.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(104.dp).scale(sc), contentAlignment = Alignment.Center) {
                                Box(Modifier.fillMaxSize().clip(CircleShape).background(W.Card).then(if (on) Modifier.border(3.dp, W.Pink, CircleShape) else Modifier)) {
                                    if (a.thumb != null) AsyncImage(a.thumb, a.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                                if (on) Box(Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(W.Pink), contentAlignment = Alignment.Center) {
                                    Icon(painterResource(R.drawable.check), null, Modifier.size(18.dp), tint = Color.White) }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(a.name, style = ts(14, FontWeight.SemiBold), textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                    if (q.isEmpty() && artists.size >= limit) item {
                        Box(Modifier.size(104.dp).clip(CircleShape).background(W.Pink).clickable { limit += 30 }, contentAlignment = Alignment.Center) {
                            Text("More for\nyou", style = ts(16, FontWeight.SemiBold), textAlign = TextAlign.Center) }
                    }
                }
            }
            if (err != null) Text(err!!, Modifier.padding(horizontal = 22.dp), style = ts(13, color = Color(0xFFFF6B8A)))
            val ready = picked.size >= 3
            Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.alpha(if (ready) 1f else 0.5f).clip(RoundedCornerShape(50)).background(Color.White).clickable(enabled = ready && !busy) {
                    busy = true; err = null
                    scope.launch {
                        try {
                            val ids = JSONArray(); picked.forEach { ids.put(it) }
                            taste?.let { try { it.save() } catch (e: Exception) { } }; Api.rpc("save_artists", JSONObject().put("p_ids", ids)); onDone()
                        } catch (e: ApiError) { err = humanErr(e); busy = false }
                    }
                }.padding(horizontal = 64.dp, vertical = 16.dp)) {
                    Text(if (ready) "Next" else "Pick ${3 - picked.size} more", style = ts(20, FontWeight.Bold, Color.Black))
                }
            }
        }
    }
}
