package com.metrolist.music.wavy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Drag-to-close for sheets drawn over the player. The sheet follows the finger, a fast swipe or a long pull closes it, and the drag never reaches the player behind. */
class SheetDrag(val mod: Modifier)

@Composable
fun rememberSheetDrag(onClose: () -> Unit): SheetDrag {
    val off = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val close = rememberUpdatedState(onClose)
    val conn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy < 0f && off.value > 0f) {
                    val n = (off.value + dy).coerceAtLeast(0f)
                    val used = n - off.value
                    scope.launch { off.snapTo(n) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) scope.launch { off.snapTo(off.value + available.y) }
                return available
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (off.value <= 0f) return Velocity.Zero
                finish(scope, off, available.y, close.value)
                return available
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }
    val ds = rememberDraggableState { d -> scope.launch { off.snapTo((off.value + d).coerceAtLeast(0f)) } }
    val m = Modifier
        .offset { IntOffset(0, off.value.toInt()) }
        .nestedScroll(conn)
        .draggable(ds, Orientation.Vertical, onDragStopped = { v -> finish(scope, off, v, close.value) })
    return SheetDrag(m)
}

private fun finish(scope: CoroutineScope, off: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, vy: Float, onClose: () -> Unit) {
    scope.launch {
        if (vy > 1400f || off.value > 260f) { off.animateTo(2600f, tween(160)); onClose() }
        else off.animateTo(0f, spring(stiffness = 600f))
    }
}
