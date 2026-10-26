package com.dronedirector.veyrocutpro.editor

import kotlin.math.abs

/** Crop / zoom / reposition of a clip; optionally animated from [start] to [end] over the clip. */
data class Frame(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f, val rotation: Float = 0f)

/**
 * A clip keyframe: framing and opacity at [t] (source ms from the clip's in point), and how it
 * eases to the next key — 0 smooth, 1 linear, 2 hold.
 */
data class ClipKey(
    val t: Long, val frame: Frame = Frame(), val opacity: Float = 1f, val ease: Int = 0,
    /** ease 3 = own curve: cubic Bézier handles x1, y1, x2, y2 (x 0..1; y may overshoot), like a graph editor. */
    val bez: List<Float>? = null,
)

data class Reframe(
    val start: Frame = Frame(),
    val end: Frame = Frame(),
    val animate: Boolean = false,
    val flip: Boolean = false,
    /** Auto-reframe: horizontal shift (fraction of the frame) at evenly spaced points through the clip. */
    val track: List<Float>? = null,
    /** Keyframes (zoom, position, rotation, opacity); when present they replace start / end. */
    val keys: List<ClipKey>? = null,
) {
    val isIdentity get() = start == Frame() && (!animate || end == Frame()) && !flip && track.isNullOrEmpty() && keys.isNullOrEmpty()
    val hasKeys: Boolean get() = !keys.isNullOrEmpty()
    /** Any keyframe below full opacity (needs the opacity effect on export). */
    val hasOpacity: Boolean get() = keys?.any { it.opacity < 0.999f } == true

    /** The keyframed framing / opacity at [rel] ms from the in point, or null without keyframes. */
    fun keyedAt(rel: Long): ClipKey? {
        val k = keys?.sortedBy { it.t } ?: return null
        if (k.isEmpty()) return null
        if (rel <= k.first().t) return k.first().copy(t = rel)
        if (rel >= k.last().t) return k.last().copy(t = rel)
        val i = k.indexOfLast { it.t <= rel }.coerceIn(0, k.size - 2)
        val a = k[i]; val b = k[i + 1]
        val u = ((rel - a.t).toFloat() / (b.t - a.t).coerceAtLeast(1)).coerceIn(0f, 1f)
        val e = KeyCurve.eased(a, u)
        fun l(x: Float, y: Float) = x + (y - x) * e
        return ClipKey(
            rel,
            Frame(l(a.frame.zoom, b.frame.zoom), l(a.frame.x, b.frame.x), l(a.frame.y, b.frame.y), l(a.frame.rotation, b.frame.rotation)),
            l(a.opacity, b.opacity).coerceIn(0f, 1f), a.ease, a.bez,
        )
    }

    fun opacityAt(rel: Long): Float = keyedAt(rel)?.opacity ?: 1f

    /** Adds or replaces the keyframe at [t] (within [tolMs]). */
    fun upsertKey(t: Long, frame: Frame, opacity: Float, tolMs: Long = 40): Reframe {
        val list = (keys ?: emptyList()).filter { kotlin.math.abs(it.t - t) > tolMs }
        val ease = keys?.firstOrNull { kotlin.math.abs(it.t - t) <= tolMs }?.ease ?: 0
        return copy(keys = (list + ClipKey(t.coerceAtLeast(0), frame, opacity.coerceIn(0f, 1f), ease)).sortedBy { it.t }, animate = false)
    }
    fun at(f: Float): Frame {
        val t = track
        if (!t.isNullOrEmpty()) {
            val x = f.coerceIn(0f, 1f) * (t.size - 1)
            val i = x.toInt().coerceAtMost(t.size - 2).coerceAtLeast(0)
            val tx = if (t.size == 1) t[0] else t[i] + (t[i + 1] - t[i]) * (x - i)
            return (if (animate) atKenBurns(f) else start).copy(x = tx)
        }
        return atKenBurns(f)
    }

    private fun atKenBurns(f: Float): Frame {
        if (!animate) return start
        val e = TransitionMath.ease(f.coerceIn(0f, 1f))
        return Frame(
            start.zoom + (end.zoom - start.zoom) * e, start.x + (end.x - start.x) * e,
            start.y + (end.y - start.y) * e, start.rotation + (end.rotation - start.rotation) * e,
        )
    }
}

data class Stabilize(
    val enabled: Boolean = false, val strength: Float = 0.6f, val tripod: Boolean = false, val autoZoom: Boolean = true,
    /** Use the phone's motion data (Capture's .gcsv) instead of the picture alone; and a file picked by hand. */
    val gyro: Boolean? = null, val gyroFile: String? = null,
)

/** Final transform at a moment: scale, rotation (degrees, clockwise) and shift (fraction of the frame, y down). */
data class Xform(val scale: Float, val rotation: Float, val tx: Float, val ty: Float, val flip: Boolean)

/** Keyframe curves: the eased progress for [u] (0..1) under [a]'s easing. */
object KeyCurve {
    val PRESETS = listOf(
        "Ease in" to listOf(0.42f, 0f, 1f, 1f), "Ease out" to listOf(0f, 0f, 0.58f, 1f), "Ease in-out" to listOf(0.42f, 0f, 0.58f, 1f),
        "Strong in-out" to listOf(0.85f, 0f, 0.15f, 1f), "Back (overshoot)" to listOf(0.34f, 1.56f, 0.64f, 1f), "Anticipate" to listOf(0.36f, -0.4f, 0.66f, 1f),
    )
    private fun bz(t: Float, p1: Float, p2: Float): Float { val m = 1 - t; return 3 * m * m * t * p1 + 3 * m * t * t * p2 + t * t * t }
    /** Cubic-Bézier timing (CSS style): solve x(t) = u, return y(t). */
    fun bezier(u: Float, c: List<Float>): Float {
        if (c.size < 4) return u
        val x1 = c[0].coerceIn(0f, 1f); val x2 = c[2].coerceIn(0f, 1f)
        var lo = 0f; var hi = 1f; var t = u
        repeat(24) { t = (lo + hi) / 2; if (bz(t, x1, x2) < u) lo = t else hi = t }
        return bz(t, c[1], c[3])
    }
    fun eased(k: ClipKey, u: Float): Float = when (k.ease) { 1 -> u; 2 -> 0f; 3 -> bezier(u, k.bez ?: PRESETS[2].second); else -> u * u * (3f - 2f * u) }
}
