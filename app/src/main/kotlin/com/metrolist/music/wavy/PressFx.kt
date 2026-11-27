package com.metrolist.music.wavy

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/** Cheap press feedback: the card shrinks a little under the finger and springs back. Watches touches without consuming them, so taps and scrolls still work. */
fun Modifier.pressScale(): Modifier = composed {
    var down by remember { mutableStateOf(false) }
    val s = animateFloatAsState(if (down) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "press")
    this.pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); down = true; waitForUpOrCancellation(); down = false } }
        .graphicsLayer { scaleX = s.value; scaleY = s.value }
}
