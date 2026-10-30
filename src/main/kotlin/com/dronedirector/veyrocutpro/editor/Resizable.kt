package com.dronedirector.veyrocutpro.editor

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.dronedirector.veyrocutpro.ui.Pro

/** Remembered panel sizes (dp) per editor and orientation; -1 means "automatic". */
object PaneSizes {
    private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)
    fun get(c: Context, key: String): Float = prefs(c).getFloat("pane_$key", -1f)
    fun set(c: Context, key: String, dp: Float) { prefs(c).edit().putFloat("pane_$key", dp).apply() }
}

/** A size that can be changed by a [ResizeHandle] and is saved when the drag ends. */
class PaneSize(private val context: Context, private val key: String) {
    var dp by mutableStateOf(PaneSizes.get(context, key))
    fun save() = PaneSizes.set(context, key, dp)
    fun reset() { dp = -1f; save() }
}

@Composable
fun rememberPane(key: String, version: Int = 0): PaneSize {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember(key, version) { PaneSize(ctx, key) }
}

/**
 * Divider between two panels. Resizing only starts after holding a finger on it (a short buzz
 * confirms), so it can't happen by accident. Double-tap returns to the default size.
 * [horizontalBar] = a bar across the screen that is dragged up / down.
 */
@Composable
fun ResizeHandle(horizontalBar: Boolean, onDrag: (deltaDp: Float) -> Unit, onEnd: () -> Unit, onReset: () -> Unit) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var active by remember { mutableStateOf(false) }
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    val reset by rememberUpdatedState(onReset)
    val accent = Pro.Amber
    val mod = if (horizontalBar) Modifier.fillMaxWidth().height(16.dp) else Modifier.fillMaxHeight().width(16.dp)
    Box(
        mod.background(if (active) accent.copy(alpha = 0.18f) else Ed.Bg)
            .pointerInput(horizontalBar) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { active = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDragEnd = { active = false; end() },
                    onDragCancel = { active = false; end() },
                    onDrag = { change, amount ->
                        change.consume()
                        val px = if (horizontalBar) amount.y else amount.x
                        drag(with(density) { px.toDp().value })
                    },
                )
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { reset(); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }) },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val line = if (active) accent else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.10f)
            val gripLen = 36.dp.toPx(); val gripW = 5.dp.toPx()
            if (horizontalBar) {
                drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), if (active) 2f else 1f)
                drawRoundRect(if (active) accent else Color.White.copy(alpha = 0.35f), Offset(size.width / 2 - gripLen / 2, size.height / 2 - gripW / 2), Size(gripLen, gripW), CornerRadius(gripW / 2))
            } else {
                drawLine(line, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), if (active) 2f else 1f)
                drawRoundRect(if (active) accent else Color.White.copy(alpha = 0.35f), Offset(size.width / 2 - gripW / 2, size.height / 2 - gripLen / 2), Size(gripW, gripLen), CornerRadius(gripW / 2))
            }
        }
    }
}
