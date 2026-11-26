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
    val go by rememberUpdatedState(onStart)
    androidx.compose.ui.viewinterop.AndroidView(modifier = Modifier.fillMaxSize().background(Color.Black), factory = { c ->
        android.webkit.WebView(c).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(object { @android.webkit.JavascriptInterface fun start() { post { go() } } }, "WavyApp")
            loadUrl("file:///android_asset/welcome/index.html")
        }
    })
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
    val done by rememberUpdatedState(onDone)
    var web by remember { mutableStateOf<android.webkit.WebView?>(null) }
    fun js(code: String) { web?.post { web?.evaluateJavascript(code, null) } }
    fun msg(m: String) = "wavyDone(" + JSONObject.quote(m) + ")"
    fun auth(email: String, pw: String, rem: Boolean, signup: Boolean) {
        Api.setRemember(rem)
        scope.launch {
            try {
                if (signup) Api.signUp(email, pw) else Api.signIn(email, pw)
                done(!onboardingDone())
            } catch (e: ApiError) { js(msg(humanErr(e))) } catch (e: Exception) { js(msg("Something went wrong. Check your connection.")) }
        }
    }
    androidx.compose.ui.viewinterop.AndroidView(modifier = Modifier.fillMaxSize().background(Color.Black).imePadding(), factory = { c ->
        android.webkit.WebView(c).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface fun login(e: String, p: String, r: Boolean) { post { auth(e, p, r, false) } }
                @android.webkit.JavascriptInterface fun signup(e: String, p: String, r: Boolean) { post { auth(e, p, r, true) } }
                @android.webkit.JavascriptInterface fun forgot(e: String) { post {
                    if (e.isBlank()) js("toast('Enter your email first')") else scope.launch {
                        try { Api.recover(e); js("toast('If this email has an account, a reset link was sent.')") } catch (x: ApiError) { js("toast(" + JSONObject.quote(humanErr(x)) + ")") }
                    } } }
                @android.webkit.JavascriptInterface fun google() { post { Toast.makeText(ctx, "Google sign-in is not switched on yet. Use email for now.", Toast.LENGTH_LONG).show() } }
            }, "WavyApp")
            loadUrl("file:///android_asset/welcome/login.html")
            web = this
        }
    })
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
