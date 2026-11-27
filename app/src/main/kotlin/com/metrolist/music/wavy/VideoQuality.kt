package com.metrolist.music.wavy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.metrolist.music.R

private val VQ = listOf("auto" to "Auto (recommended)", "1080" to "1080p", "720" to "720p", "360" to "360p")

/** One row showing the current video quality. Tapping opens a list like YouTube's quality menu, with a tick on the chosen one. */
@Composable
fun VideoQualityRow() {
    var vq by remember { mutableStateOf(S.s("reel_vq", "auto")) }
    var open by remember { mutableStateOf(false) }
    val label = if (vq == "auto") "Auto" else vq + "p"
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Video quality", style = ts(17, FontWeight.Medium))
            Text("Lower is smoother on slow phones.", style = ts(13, color = Color(0xFFB9B2C4)))
        }
        Text(label, style = ts(15, FontWeight.Medium, W.Pink))
    }
    if (open) Dialog(onDismissRequest = { open = false }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF1B1620)).padding(vertical = 10.dp)) {
            Text("Video quality", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = ts(18, FontWeight.SemiBold))
            VQ.forEach { (k, l) ->
                Row(Modifier.fillMaxWidth().clickable { vq = k; S.put("reel_vq", k); open = false }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(l, Modifier.weight(1f), style = ts(16, if (vq == k) FontWeight.SemiBold else FontWeight.Normal))
                    if (vq == k) Icon(painterResource(R.drawable.check), null, Modifier.size(22.dp), tint = W.Pink)
                }
            }
        }
    }
}
