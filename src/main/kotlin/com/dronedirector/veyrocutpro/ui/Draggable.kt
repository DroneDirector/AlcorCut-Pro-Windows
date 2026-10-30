package com.dronedirector.veyrocutpro.ui

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Saved overlay positions, as fractions of the viewfinder so they survive rotation and resizing. */
object OverlayLayout {
    private const val PREFS = "procam_layout"

    fun load(context: Context, key: String): Pair<Float, Float>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("${key}_x")) return null
        return p.getFloat("${key}_x", 0f) to p.getFloat("${key}_y", 0f)
    }

    fun save(context: Context, key: String, fx: Float, fy: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("${key}_x", fx).putFloat("${key}_y", fy).apply()
    }

    fun loadSize(context: Context, key: String): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat("${key}_s", 1f).coerceIn(0.5f, 2.5f)

    fun loadMini(context: Context, key: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("${key}_m", false)

    fun saveSize(context: Context, key: String, size: Float, mini: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("${key}_s", size).putBoolean("${key}_m", mini).apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/**
 * Places [content] in the viewfinder at its saved position (or [defaultAlignment] the first time)
 * and lets the user drag it anywhere inside. Two fingers resize it; a double tap shrinks it to a
 * small but still live version (and back); a long press shows a resize grip and a minimise button.
 * While dragging it gets an amber outline. Position, size and minimised state are kept per [key]
 * and per screen orientation.
 */
@Composable
fun DraggableOverlay(
    key: String,
    defaultAlignment: Alignment,
    version: Int,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val storeKey = key + if (landscape) "_land" else "_port"
        val areaW = with(density) { maxWidth.toPx() }
        val areaH = with(density) { maxHeight.toPx() }
        val margin = with(density) { 8.dp.toPx() }

        var childSize by remember { mutableStateOf(IntSize.Zero) }
        val saved = remember(storeKey, version) { OverlayLayout.load(context, storeKey) }
        var x by remember(storeKey, version) { mutableFloatStateOf(Float.NaN) }
        var y by remember(storeKey, version) { mutableFloatStateOf(Float.NaN) }
        var zoomK by remember(storeKey, version) { mutableFloatStateOf(OverlayLayout.loadSize(context, storeKey)) }
        var mini by remember(storeKey, version) { mutableStateOf(OverlayLayout.loadMini(context, storeKey)) }
        var dragging by remember { mutableStateOf(false) }
        var handles by remember { mutableStateOf(false) }
        val k by animateFloatAsState(if (mini) MINI else zoomK, label = "size")
        val w = childSize.width * k
        val h = childSize.height * k

        // Resolve the starting position once the overlay's size is known.
        if (x.isNaN() && childSize != IntSize.Zero) {
            if (saved != null) {
                x = saved.first * (areaW - w)
                y = saved.second * (areaH - h)
            } else {
                val inner = IntSize((areaW - 2 * margin).roundToInt(), (areaH - 2 * margin).roundToInt())
                val p = defaultAlignment.align(IntSize(w.roundToInt(), h.roundToInt()), inner, dir)
                x = p.x + margin
                y = p.y + margin
            }
        }
        val maxX = (areaW - w).coerceAtLeast(0f)
        val maxY = (areaH - h).coerceAtLeast(0f)
        fun save() {
            val mx = (areaW - w).coerceAtLeast(1f)
            val my = (areaH - h).coerceAtLeast(1f)
            OverlayLayout.save(context, storeKey, (x.coerceIn(0f, mx) / mx), (y.coerceIn(0f, my) / my))
            OverlayLayout.saveSize(context, storeKey, zoomK, mini)
        }
        LaunchedEffect(handles, zoomK, mini) { if (handles) { delay(4000); handles = false } }

        Box(
            Modifier
                .offset { IntOffset((if (x.isNaN()) -10_000f else x.coerceIn(0f, maxX)).roundToInt(), (if (y.isNaN()) 0f else y.coerceIn(0f, maxY)).roundToInt()) }
                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .then(if (dragging || handles) Modifier.border(1.dp, Pro.Amber, RoundedCornerShape(8.dp)) else Modifier)
                .pointerInput(storeKey, version) {
                    detectTapGestures(
                        onDoubleTap = { mini = !mini; save() },
                        onLongPress = { handles = true },
                    )
                }
                .pointerInput(storeKey, version) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var active = false
                        var moved = Offset.Zero
                        do {
                            val ev = awaitPointerEvent()
                            val down = ev.changes.filter { it.pressed }
                            if (down.isEmpty()) break
                            val pan = ev.calculatePan()
                            val zoom = ev.calculateZoom()
                            if (!active) {
                                moved += pan
                                if (moved.getDistance() > viewConfiguration.touchSlop || (down.size >= 2 && abs(zoom - 1f) > 0.02f)) { active = true; dragging = true }
                            }
                            if (active) {
                                if (down.size >= 2 && zoom != 1f) { mini = false; zoomK = (zoomK * zoom).coerceIn(MIN, MAX) }
                                x = (x + pan.x).coerceIn(0f, (areaW - w).coerceAtLeast(0f))
                                y = (y + pan.y).coerceIn(0f, (areaH - h).coerceAtLeast(0f))
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (ev.changes.any { it.pressed })
                        if (active) { dragging = false; save() }
                    }
                },
        ) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .onSizeChanged { childSize = it }
                    .graphicsLayer { scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0f, 0f); alpha = if (mini) 0.85f else 1f },
            ) { content() }
            if (handles) {
                // Minimise / restore.
                Box(
                    Modifier.align(Alignment.TopEnd).offset(10.dp, (-10).dp).size(24.dp).clip(CircleShape).background(Pro.Amber)
                        .pointerInput(Unit) { detectTapGestures { mini = !mini; save() } },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (mini) Icons.Filled.OpenInFull else Icons.Filled.CloseFullscreen, "Minimise", tint = Color.Black, modifier = Modifier.size(14.dp)) }
                // Resize grip.
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(10.dp, 10.dp).size(24.dp).clip(CircleShape).background(Pro.Amber)
                        .pointerInput(Unit) {
                            detectDragGestures(onDragEnd = { save() }) { change, drag ->
                                change.consume()
                                if (childSize.width > 0) { mini = false; zoomK = ((childSize.width * zoomK + drag.x) / childSize.width).coerceIn(MIN, MAX) }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.OpenWith, "Resize", tint = Color.Black, modifier = Modifier.size(14.dp)) }
            }
        }
    }
}

private const val MIN = 0.5f
private const val MAX = 2.5f
private const val MINI = 0.4f
