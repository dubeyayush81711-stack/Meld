package com.metrolist.music.wavy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.metrolist.music.R

/** Full-screen mic sheet: Voice (live speech to text) and Song (UI only until identify is set up). */
@Composable
fun MicScreen(onClose: () -> Unit, onResult: (String) -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var heard by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Listening...") }
    var level by remember { mutableStateOf(0f) }
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var restart by remember { mutableStateOf(0) }
    var songMsg by remember { mutableStateOf<String?>(null) }
    val perm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted = it; if (!it) status = "Microphone permission is needed" }
    LaunchedEffect(Unit) { if (!granted) perm.launch(Manifest.permission.RECORD_AUDIO) }

    DisposableEffect(tab, granted, restart) {
        var sr: SpeechRecognizer? = null
        if (tab == 0 && granted) {
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { status = "Voice input is not available on this phone" }
            else {
                heard = ""; status = "Listening..."
                sr = SpeechRecognizer.createSpeechRecognizer(ctx)
                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(p: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(v: Float) { level = ((v + 2f) / 12f).coerceIn(0f, 1f) }
                    override fun onBufferReceived(b: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(e: Int) { status = if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) "Didn't catch that. Tap the mic to try again." else "Voice input failed. Tap the mic to try again." }
                    override fun onResults(r: Bundle?) { val s = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(); if (!s.isNullOrBlank()) onResult(s.trim()) else status = "Didn't catch that. Tap the mic to try again." }
                    override fun onPartialResults(p: Bundle?) { val s = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(); if (!s.isNullOrBlank()) heard = s }
                    override fun onEvent(t: Int, b: Bundle?) {}
                })
                sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true))
            }
        }
        onDispose { try { sr?.destroy() } catch (e: Exception) { } }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val bg = if (tab == 0) Modifier.background(Color(0xFF0B090E)) else Modifier.background(Brush.verticalGradient(listOf(Color(0xFF0B090E), Color(0xFF1E1030), Color(0xFF2B0B3A))))
        Box(Modifier.fillMaxSize().then(bg).statusBarsPadding().navigationBarsPadding()) {
            Icon(painterResource(R.drawable.close), "Close", Modifier.padding(start = 20.dp, top = 22.dp).size(30.dp).clickable { onClose() }, tint = Color.White)
            Row(Modifier.align(Alignment.TopCenter).padding(top = 14.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).padding(4.dp)) {
                listOf("Voice", "Song").forEachIndexed { i, label ->
                    val sel = tab == i
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (sel) Color.White else Color.Transparent).clickable { tab = i; songMsg = null }.padding(horizontal = 22.dp, vertical = 12.dp)) {
                        Text(label, style = ts(17, FontWeight.Medium, if (sel) Color(0xFF111111) else Color(0xDDFFFFFF)))
                    }
                }
            }
            if (tab == 0) {
                Column(Modifier.align(Alignment.TopStart).padding(start = 24.dp, end = 24.dp, top = 150.dp)) {
                    Text(if (heard.isNotEmpty()) heard else status, style = ts(36, FontWeight.Normal))
                }
                if (heard.isEmpty() && status == "Listening...") Column(Modifier.align(Alignment.Center).padding(top = 160.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Try saying", style = ts(18, color = Color(0x99FFFFFF)))
                    Spacer(Modifier.height(14.dp))
                    Text("\"Play some music\"", style = ts(18, color = Color(0x99FFFFFF)))
                }
                val sc by animateFloatAsState(1f + level * 0.35f, tween(120), label = "lv")
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp).size(190.dp).clickable { if (!granted) perm.launch(Manifest.permission.RECORD_AUDIO) else restart++ }, contentAlignment = Alignment.Center) {
                    val rip = rememberInfiniteTransition(label = "rip")
                    val rt by rip.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "rt")
                    Canvas(Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2, size.height / 2); val r0 = 55.dp.toPx(); val span = 150.dp.toPx() * (1f + level * 0.5f)
                        if (Reduce.on) drawCircle(Color(0x66FFFFFF), r0 * 1.7f * sc, c, style = Stroke(2f))
                        else for (k in 0..3) {
                            val t = (rt + k / 4f) % 1f; val e = 1f - (1f - t) * (1f - t)
                            val a = (1f - t) * (0.28f + 0.5f * level)
                            drawCircle(Brush.radialGradient(listOf(Color(0x00FF2E7E), Color(0xFFFF2E7E).copy(alpha = a * 0.55f)), center = c, radius = r0 + span * e), r0 + span * e, c)
                            drawCircle(Color(0xFFFF4F8B).copy(alpha = a), r0 + span * e, c, style = Stroke(2.5f.dp.toPx() * (1f - t) + 1f))
                        }
                    }
                    Box(Modifier.size(110.dp).clip(CircleShape).background(W.Pink), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.mic), "Mic", Modifier.size(46.dp), tint = Color.White)
                    }
                }
            } else {
                Text(songMsg ?: "Play, sing or hum a song", Modifier.align(Alignment.TopStart).padding(start = 24.dp, end = 24.dp, top = 150.dp), style = ts(36, FontWeight.Normal))
                val inf = rememberInfiniteTransition(label = "pulse")
                val p by inf.animateFloat(0.85f, 1.25f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp).size(240.dp).clickable { songMsg = "Song identify is not set up yet" }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().scale(p)) {
                        drawCircle(Brush.radialGradient(listOf(Color(0x887A4BE0), Color(0x44FF4F8B), Color.Transparent), center = Offset(size.width / 2, size.height / 2), radius = size.minDimension / 2f))
                    }
                    Box(Modifier.size(118.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(16, 26, 40, 40).forEach { h -> Box(Modifier.width(6.dp).height(h.dp).clip(RoundedCornerShape(3.dp)).background(W.Pink)) }
                        }
                    }
                }
            }
        }
    }
}
