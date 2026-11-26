package com.metrolist.music.wavy

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.delay

/** Dynamic-Island-style pill below the status bar. Tap to spring-expand into a compact player. */
@Composable
fun IslandPill(pc: PlayerConnection, onMusicTab: () -> Unit) {
    val meta by pc.mediaMetadata.collectAsState()
    val playing by pc.isPlaying.collectAsState()
    val m = meta ?: return
    var open by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(m.id, open) { while (open) { pos = pc.player.currentPosition; delay(500) } }
    val dur = pc.player.duration.takeIf { it > 0 } ?: (m.duration * 1000L)
    val hap = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(Modifier.fillMaxSize()) {
        if (open) Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { open = false })
        val w by animateDpAsState(if (open) 360.dp else 128.dp, spring(dampingRatio = 0.8f, stiffness = 380f), label = "iw")
        val h by animateDpAsState(if (open) 150.dp else 34.dp, spring(dampingRatio = 0.8f, stiffness = 380f), label = "ih")
        Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp).padding(horizontal = 12.dp)
            .widthIn(max = w).fillMaxWidth().height(h).clip(RoundedCornerShape(if (open) 30.dp else 20.dp)).background(Color.Black)
            .pointerInput(open) { detectVerticalDragGestures { _, dy -> if (open && dy < -12) open = false } }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!open) { open = true; hap.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) } }) {
            if (!open) {
                Row(Modifier.fillMaxSize().padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(m.thumbnailUrl, null, Modifier.size(24.dp).clip(CircleShape).background(W.Card), contentScale = ContentScale.Crop)
                    Spacer(Modifier.weight(1f))
                    EqBars(playing)
                    Spacer(Modifier.width(4.dp))
                }
            } else {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(m.thumbnailUrl, null, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(W.Card), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(m.title, style = ts(16, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.artists.joinToString { it.name }, style = ts(13, color = Color(0xFFB9B2C4)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        EqBars(playing)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
                        Box(Modifier.fillMaxWidth((pos.toFloat() / dur.coerceAtLeast(1)).coerceIn(0f, 1f)).fillMaxHeight().background(W.Pink))
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.skip_previous), null, Modifier.size(40.dp).clip(CircleShape).clickable { pc.seekToPrevious() }.padding(6.dp), tint = Color.White)
                        Icon(painterResource(if (playing) R.drawable.pause else R.drawable.play), null, Modifier.size(44.dp).clip(CircleShape).clickable { pc.togglePlayPause() }.padding(6.dp), tint = Color.White)
                        Icon(painterResource(R.drawable.skip_next), null, Modifier.size(40.dp).clip(CircleShape).clickable { pc.seekToNext() }.padding(6.dp), tint = Color.White)
                        Text("Open Music", Modifier.clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF)).clickable { open = false; onMusicTab() }.padding(horizontal = 12.dp, vertical = 8.dp), style = ts(13, FontWeight.Medium))
                    }
                }
            }
        }
    }
}

@Composable
fun EqBars(playing: Boolean) {
    val t = rememberInfiniteTransition(label = "eq")
    Row(Modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 0 until 4) {
            val f by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(380 + i * 110, easing = LinearEasing), RepeatMode.Reverse), label = "b$i")
            Box(Modifier.width(3.dp).height((if (playing && !Reduce.on) 16f * f else 5f).dp).clip(RoundedCornerShape(2.dp)).background(W.Pink))
        }
    }
}
