package com.dronedirector.veyrocutpro.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/**
 * Pinch and move on a picture: [onGesture] gets the fingers' centre, how far they moved and how
 * much they pinched (1 = no change) while at least [minPointers]() fingers are down; [onEnd] runs
 * once when the last finger lifts after a gesture. Fingers a child already uses (a layer being
 * dragged, a brush stroke) are left alone. Own code.
 */
suspend fun PointerInputScope.pinchAndMove(
    minPointers: () -> Int,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onEnd: () -> Unit = {},
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var active = false
        var moved = Offset.Zero
        var scale = 1f
        do {
            val ev = awaitPointerEvent()
            val down = ev.changes.filter { it.pressed }
            // One finger: only when nothing else uses it. Two fingers: always (pinch beats a brush stroke).
            if (down.size >= minPointers() && (down.size >= 2 || down.none { it.isConsumed })) {
                val zoom = ev.calculateZoom()
                val pan = ev.calculatePan()
                if (!active) {
                    moved += pan; scale *= zoom
                    if (abs(scale - 1f) > 0.02f || moved.getDistance() > viewConfiguration.touchSlop) active = true
                }
                if (active) {
                    onGesture(ev.calculateCentroid(useCurrent = true), pan, zoom)
                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (ev.changes.any { it.pressed })
        if (active) onEnd()
    }
}
