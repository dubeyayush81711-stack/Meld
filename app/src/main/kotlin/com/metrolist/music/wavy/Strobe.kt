package com.metrolist.music.wavy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

/** Colour strobe: a full-screen colour that changes on every detected beat. Needs a one-time photosensitivity OK. */
@Composable
fun StrobeOverlay() {
    var active by remember { mutableStateOf(S.b("fx_strobe")) }
    var ok by remember { mutableStateOf(S.b("strobe_ok")) }
    LaunchedEffect(Unit) { while (true) { active = S.b("fx_strobe"); ok = S.b("strobe_ok"); delay(400) } }
    val a = remember { Animatable(0f) }
    var hue by remember { mutableFloatStateOf(0f) }
    val beat = WavyFx.beat.value
    LaunchedEffect(beat) {
        if (active && ok && beat > 0L) { hue = (hue + 67f) % 360f; a.snapTo(0.55f); a.animateTo(0f, tween(260)) }
    }
    if (active && !ok) Dialog(onDismissRequest = { S.put("fx_strobe", false); active = false }) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xF2171419)).padding(24.dp)) {
            Text("Flashing lights warning", style = ts(20, FontWeight.Bold))
            Text("Colour strobe flashes the whole screen in time with the beat. Flashing lights can trigger seizures in people with photosensitive epilepsy. Only turn it on if this is safe for you and everyone watching.", Modifier.padding(top = 10.dp), style = ts(15, color = Color(0xFFD9D2E2)))
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cancel", Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).clickable { S.put("fx_strobe", false); active = false }.padding(vertical = 12.dp), style = ts(15, FontWeight.SemiBold), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("I understand", Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Color.White).clickable { S.put("strobe_ok", true); ok = true }.padding(vertical = 12.dp), style = ts(15, FontWeight.SemiBold, Color.Black), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
    if (active && ok) {
        Box(Modifier.fillMaxSize().background(Color.hsv(hue, 0.9f, 1f).copy(alpha = a.value)))
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Text("Strobe on \u00B7 tap to stop", Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).clickable { S.put("fx_strobe", false); active = false; WavyFx.refresh() }.padding(horizontal = 14.dp, vertical = 6.dp), style = ts(12, FontWeight.Medium))
        }
    }
}
