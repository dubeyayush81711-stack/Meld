package com.metrolist.music.wavy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Set by WavyActivity when the app opens from the password reset email link: access token, refresh token, error text. */
object ResetAuth { val link = mutableStateOf<Triple<String, String, String?>?>(null) }

/** "Create new password" page shown over everything after the reset link opens the app. */
@Composable
fun NewPasswordScreen(link: Triple<String, String, String?>, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf(link.third) }
    val bad = link.third != null
    Column(Modifier.fillMaxSize().background(W.Bg).statusBarsPadding().imePadding().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
        Text(if (bad) "Link problem" else "Create new password", style = ts(28, FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Text(if (bad) "Ask for a new reset link from the sign in page." else "Choose a password you have not used before. At least 6 characters.", style = ts(15, color = Color(0xB3FFFFFF)))
        Spacer(Modifier.height(24.dp))
        if (!bad) {
            PillField(pw, { pw = it }, "New password", password = true)
            Spacer(Modifier.height(12.dp))
            PillField(pw2, { pw2 = it }, "Repeat new password", password = true)
            Spacer(Modifier.height(10.dp))
        }
        if (msg != null && !bad) Text(msg!!, Modifier.padding(vertical = 6.dp), style = ts(14, color = Color(0xFFFFB3D1)))
        if (bad) Text(link.third ?: "", Modifier.padding(bottom = 10.dp), style = ts(14, color = Color(0xFFFFB3D1)))
        Spacer(Modifier.height(10.dp))
        Text(if (busy) "Saving..." else if (bad) "Back to sign in" else "Save password",
            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(W.Pink).clickable(enabled = !busy) {
                if (bad) { onDone(false); return@clickable }
                if (pw.length < 6) { msg = "Password must be at least 6 characters"; return@clickable }
                if (pw != pw2) { msg = "The two passwords do not match"; return@clickable }
                busy = true; msg = null
                scope.launch {
                    try { Api.oauthSession(link.first, link.second); Api.setPassword(pw); busy = false; onDone(true) }
                    catch (e: ApiError) { busy = false; msg = humanErr(e) }
                    catch (e: Exception) { busy = false; msg = "Something went wrong. Check your connection." }
                }
            }.padding(vertical = 16.dp), style = ts(16, FontWeight.SemiBold), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
