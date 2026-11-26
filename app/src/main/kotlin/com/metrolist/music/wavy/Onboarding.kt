
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
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.*
import androidx.compose.material3.*

import java.util.Calendar

class Taste { var dob by mutableStateOf("2006-11-25"); var gender by mutableStateOf(""); val langs = mutableStateListOf<String>() }

val OB_LANGS = listOf(
    Triple("Hindi", "hindi", Color(0xFFE5541F)), Triple("International", "international", Color(0xFFD9951C)),
    Triple("Punjabi", "punjabi", Color(0xFF8227B8)), Triple("Tamil", "south", Color(0xFFD1B665)),
    Triple("Telugu", "south", Color(0xFF14A86B)), Triple("Malayalam", "south", Color(0xFF5CC3D1)),
    Triple("Marathi", "", Color(0xFF9B6B58)), Triple("Gujarati", "", Color(0xFFD35DB5)),
    Triple("Bengali", "", Color(0xFF1E5BD6)), Triple("Kannada", "south", Color(0xFFD92B2B)))

@Composable
private fun ObFrame(title: String, back: (() -> Unit)?, header: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (header != null) Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                if (back != null) Icon(painterResource(R.drawable.arrow_back), null, Modifier.padding(start = 20.dp).size(26.dp).clickable(onClick = back), tint = Color.White)
                Text(header, Modifier.align(Alignment.Center), style = ts(16, FontWeight.SemiBold))
            }
            Text(title, Modifier.padding(start = 24.dp, top = if (header != null) 40.dp else 36.dp, end = 24.dp), style = ts(30, FontWeight.Bold).copy(lineHeight = 38.sp))
            content()
        }
    }
}

@Composable
private fun NextBtn(label: String = "Next", enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.alpha(if (enabled) 1f else 0.5f).clip(RoundedCornerShape(50)).background(Color.White).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 64.dp, vertical = 16.dp)) {
        Text(label, style = ts(20, FontWeight.Bold, Color.Black)) }
}

@Composable
private fun Wheel(items: List<String>, initial: Int, modifier: Modifier, onSel: (Int) -> Unit) {
    val h = 54.dp
    val st = rememberLazyListState(initial)
    val list = listOf("") + items + listOf("")
    LaunchedEffect(st.firstVisibleItemIndex) { onSel(st.firstVisibleItemIndex.coerceIn(0, items.size - 1)) }
    Box(modifier.height(h * 3)) {
        LazyColumn(state = st, flingBehavior = rememberSnapFlingBehavior(st), modifier = Modifier.fillMaxSize()) {
            itemsIndexed(list) { i, s ->
                val on = i - 1 == st.firstVisibleItemIndex + 0
                Box(Modifier.fillMaxWidth().height(h), contentAlignment = Alignment.Center) {
                    Text(s, style = ts(if (i == st.firstVisibleItemIndex + 1) 22 else 18, if (i == st.firstVisibleItemIndex + 1) FontWeight.Bold else FontWeight.Normal, if (i == st.firstVisibleItemIndex + 1) Color.White else Color(0xFF8A8499)))
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = h).fillMaxWidth().height(2.dp).background(W.Pink))
        Box(Modifier.align(Alignment.TopCenter).offset(y = h * 2).fillMaxWidth().height(2.dp).background(W.Pink))
    }
}

@Composable
fun DobScreen(t: Taste, onBack: () -> Unit, onNext: () -> Unit) {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    val years = (thisYear - 100..thisYear - 5).map { it.toString() }
    val days = (1..31).map { it.toString() }
    var m by remember { mutableIntStateOf(10) }; var d by remember { mutableIntStateOf(24) }; var y by remember { mutableIntStateOf(years.indexOf("2006").coerceAtLeast(0)) }
    ObFrame("What's your date of\nbirth?", onBack, "Create account") {
        Spacer(Modifier.height(80.dp))
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Wheel(months, 10 - 0, Modifier.weight(1f)) { m = it }
            Wheel(days, 24, Modifier.weight(1f)) { d = it }
            Wheel(years, y, Modifier.weight(1f)) { y = it }
        }
        Spacer(Modifier.height(50.dp))
        Box(Modifier.padding(start = 24.dp)) { NextBtn { t.dob = "%04d-%02d-%02d".format(years[y].toInt(), m + 1, (d + 1).coerceAtMost(28.coerceAtLeast(java.time.YearMonth.of(years[y].toInt(), m + 1).lengthOfMonth()))); onNext() } }
    }
}

@Composable
fun GenderScreen(t: Taste, onBack: () -> Unit, onNext: () -> Unit) {
    val opts = listOf("Female" to "female", "Male" to "male", "Non-binary" to "other", "Prefer not to say" to "prefer_not_to_say", "Other" to "other")
    ObFrame("What's your gender?", onBack, "Create account") {
        Spacer(Modifier.height(60.dp))
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            opts.forEach { (n, v) ->
                val on = t.gender == v + n
                Box(Modifier.clip(RoundedCornerShape(50)).border(2.dp, if (on) W.Pink else Color.White, RoundedCornerShape(50)).background(if (on) W.Pink else Color.Transparent)
                    .clickable { t.gender = v + n; onNext() }.padding(horizontal = 24.dp, vertical = 14.dp)) { Text(n, style = ts(16, FontWeight.SemiBold)) }
            }
        }
    }
}

@Composable
fun LanguagesScreen(t: Taste, onNext: () -> Unit) {
    ObFrame("What music do you like?", null) {
        Spacer(Modifier.height(24.dp))
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(OB_LANGS) { (n, _, c) ->
                val on = n in t.langs
                Box(Modifier.height(86.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(c, c.copy(alpha = 0.7f)))).then(if (on) Modifier.border(3.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                    .clickable { if (on) t.langs.remove(n) else t.langs.add(n) }) {
                    Text(n, Modifier.padding(16.dp), style = ts(18, FontWeight.SemiBold))
                    Box(Modifier.align(Alignment.BottomEnd).offset(x = 20.dp, y = 30.dp).size(70.dp).clip(CircleShape).background(Color(0x33000000)))
                    if (on) Icon(painterResource(R.drawable.check), null, Modifier.align(Alignment.BottomEnd).padding(10.dp).size(22.dp), tint = Color.White)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) { NextBtn(enabled = t.langs.isNotEmpty()) { onNext() } }
    }
}

/** language codes the backend charts know about, for the chosen tiles */
fun Taste.codes(): List<String> = OB_LANGS.filter { it.first in langs && it.second.isNotEmpty() }.map { it.second }.distinct()
suspend fun Taste.save() {
    val b = JSONObject().put("user_id", Api.uid)
    if (dob.isNotBlank()) b.put("dob", dob)
    gender.takeWhile { it.isLowerCase() || it == '_' }.takeIf { it.isNotEmpty() }?.let { b.put("gender", it) }
    val l = JSONArray(); langs.forEach { l.put(it.lowercase()) }; b.put("languages", l)
    Api.post("user_prefs", b, upsert = true)
}
