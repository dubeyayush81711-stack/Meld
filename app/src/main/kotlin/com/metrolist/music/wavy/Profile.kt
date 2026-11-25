package com.metrolist.music.wavy

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun ProfileScreen(onBack: () -> Unit, onSignOut: () -> Unit, onSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var editing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { try { stats = Api.rpc("profile_stats") as? JSONObject } catch (e: Exception) { } }
    val name = stats?.optString("name").orEmpty().ifBlank { Api.email.substringBefore("@") }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
        Box(Modifier.fillMaxWidth().height(330.dp).background(Brush.verticalGradient(listOf(Color(0xFF8E2A70), Color(0xFF3B0C20), Color(0xFF0B0B0D)))))
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.padding(top = 14.dp)) { Icon(painterResource(R.drawable.arrow_back), null, Modifier.size(30.dp).clickable(onClick = onBack), tint = Color.White) }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(100.dp).clip(CircleShape).background(W.Grad), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), style = ts(50, FontWeight.Bold)) }
                Column(Modifier.padding(start = 20.dp)) {
                    Text(name, style = ts(28, FontWeight.Bold))
                    Text("${stats?.optInt("followers") ?: 0} followers \u00B7 ${stats?.optInt("following") ?: 0} following", style = ts(15, color = Color(0xFFE6DDEA)))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row {
                Text("Edit", Modifier.border(2.dp, Color.White, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).clickable { editing = true }.padding(horizontal = 32.dp, vertical = 10.dp), style = ts(16, FontWeight.SemiBold))
                Spacer(Modifier.width(12.dp))
                Text("Settings", Modifier.border(1.dp, Color(0x77FFFFFF), RoundedCornerShape(50)).clip(RoundedCornerShape(50)).clickable(onClick = onSettings).padding(horizontal = 24.dp, vertical = 10.dp), style = ts(15, color = Color(0xCCFFFFFF)))
                Spacer(Modifier.width(12.dp))
                Text("Sign out", Modifier.border(1.dp, Color(0x77FFFFFF), RoundedCornerShape(50)).clip(RoundedCornerShape(50)).clickable(onClick = onSignOut).padding(horizontal = 24.dp, vertical = 10.dp), style = ts(15, color = Color(0xCCFFFFFF)))
            }
            Spacer(Modifier.height(90.dp))
            Text("Playlists", style = ts(24, FontWeight.Bold)); Spacer(Modifier.height(10.dp))
            val pl = stats?.optJSONArray("playlists")
            if (pl == null || pl.length() == 0) Text("No playlists yet. Playlists you add will show here.", style = ts(15, color = W.Dim))
            else (0 until pl.length()).map { pl.getJSONObject(it) }.forEach { p ->
                Text((if (p.optBoolean("is_favourite")) "Favourites" else p.optString("name")) + "  \u00B7  ${p.optInt("track_count")} songs", Modifier.padding(vertical = 8.dp), style = ts(16, FontWeight.Medium))
            }
        }
        if (editing) EditProfile(name, onClose = { editing = false }) { n ->
            scope.launch { try { Api.rpc("set_profile", JSONObject().put("p_name", n)); reload++; editing = false } catch (e: Exception) { } }
        }
    }
}

@Composable
fun EditProfile(name: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var n by remember { mutableStateOf(name) }
    Backdrop(streaks = false) { Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.close), null, Modifier.size(30.dp).clickable(onClick = onClose), tint = Color.White)
            Text("Edit profile", Modifier.weight(1f), style = ts(20, FontWeight.Bold), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("Save", Modifier.clickable { if (n.isNotBlank()) onSave(n.trim()) }, style = ts(20, FontWeight.Bold, Color(0xFFFF3D8B)))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.align(Alignment.CenterHorizontally).size(140.dp).clip(CircleShape).background(W.Grad), contentAlignment = Alignment.Center) { Text(n.take(1).uppercase(), style = ts(70, FontWeight.Bold)) }
        Spacer(Modifier.height(36.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Name", Modifier.width(110.dp), style = ts(20, FontWeight.Bold))
            BasicTextField(n, { if (it.length <= 40) n = it }, singleLine = true, textStyle = ts(20), cursorBrush = SolidColor(W.Pink), modifier = Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(1.dp).background(Color(0x33FFFFFF)))
    } }
}

@Composable
fun EditProfileRoute(onClose: () -> Unit) {
    var name by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { name = try { (Api.rpc("profile_stats") as? JSONObject)?.optString("name").orEmpty().ifBlank { Api.email.substringBefore("@") } } catch (e: Exception) { Api.email.substringBefore("@") } }
    val n = name ?: return
    EditProfile(n, onClose = onClose) { v -> scope.launch { try { Api.rpc("set_profile", JSONObject().put("p_name", v)) } catch (e: Exception) { }; onClose() } }
}
