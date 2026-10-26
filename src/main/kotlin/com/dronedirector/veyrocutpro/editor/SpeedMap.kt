package com.dronedirector.veyrocutpro.editor

import kotlin.math.PI
import kotlin.math.cos

/** Ready-made speed curves: speed multipliers at evenly spaced points through the clip. */
enum class RampPreset(val label: String, val points: List<Float>) {
    MONTAGE("Montage", listOf(2.5f, 2.5f, 0.4f, 2.5f, 2.5f)),
    HERO("Hero", listOf(1f, 1f, 0.25f, 0.25f, 1f)),
    BULLET("Bullet", listOf(1.5f, 0.2f, 0.2f, 0.2f, 1.5f)),
    FLASH_IN("Flash in", listOf(4f, 2f, 1f, 1f, 1f)),
    FLASH_OUT("Flash out", listOf(1f, 1f, 1f, 2f, 4f)),
    JUMP("Jump cut", listOf(1f, 1f, 5f, 1f, 1f)),
    SLOW_IN("Slow in", listOf(0.3f, 0.6f, 1f, 1f, 1f)),
    SLOW_OUT("Slow out", listOf(1f, 1f, 1f, 0.6f, 0.3f)),
}

/** Speed multiplier from a ramp at clip progress [p] (0..1), smoothly interpolated. */
fun rampAt(points: List<Float>, p: Float): Float {
    if (points.size < 2) return points.firstOrNull() ?: 1f
    val x = p.coerceIn(0f, 1f) * (points.size - 1)
    val i = x.toInt().coerceAtMost(points.size - 2)
    val f = x - i
    val e = ((1 - cos(f * PI)) / 2).toFloat()
    return points[i] + (points[i + 1] - points[i]) * e
}

private const val STEPS = 240
private val tables = object : LinkedHashMap<String, FloatArray>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > 64
}

/** Cumulative timeline time (ms) at STEPS+1 evenly spaced source positions of a ramped clip. */
private fun Clip.table(): FloatArray {
    val r = ramp ?: return FloatArray(0)
    val key = "$startMs:$endMs:$speed:${r.joinToString(",")}"
    synchronized(tables) {
        tables[key]?.let { return it }
        val t = FloatArray(STEPS + 1)
        val srcLen = (endMs - startMs).coerceAtLeast(1).toFloat()
        val ds = srcLen / STEPS
        for (i in 1..STEPS) {
            val p = (i - 0.5f) / STEPS
            t[i] = t[i - 1] + ds / (speed * rampAt(r, p)).coerceIn(0.05f, 16f)
        }
        tables[key] = t
        return t
    }
}

/** Playback speed at source time [srcMs] (absolute in the file). */
fun Clip.speedAtSrc(srcMs: Long): Float {
    val r = ramp ?: return speed
    val p = (srcMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1)
    return (speed * rampAt(r, p)).coerceIn(0.05f, 16f)
}

/** Timeline time (ms from the clip's start on the timeline) at source time [srcMs]. */
fun Clip.tlOffsetOf(srcMs: Long): Long {
    if (ramp == null) return ((srcMs - startMs) / speed).toLong()
    val t = table()
    val x = ((srcMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1)).coerceIn(0f, 1f) * STEPS
    val i = x.toInt().coerceAtMost(STEPS - 1)
    return (t[i] + (t[i + 1] - t[i]) * (x - i)).toLong()
}

/** Source time (absolute in the file) at timeline offset [tlMs] from the clip's start. */
fun Clip.srcAtOffset(tlMs: Long): Long {
    if (ramp == null) return startMs + (tlMs * speed).toLong()
    val t = table()
    val target = tlMs.toFloat().coerceIn(0f, t[STEPS])
    var lo = 0; var hi = STEPS
    while (hi - lo > 1) { val m = (lo + hi) / 2; if (t[m] <= target) lo = m else hi = m }
    val f = if (t[hi] > t[lo]) (target - t[lo]) / (t[hi] - t[lo]) else 0f
    return startMs + ((lo + f) / STEPS * (endMs - startMs)).toLong()
}

/** Length on the timeline of a ramped clip. */
fun Clip.rampedLength(): Long = table().let { if (it.isEmpty()) 0L else it[STEPS].toLong() }

/** A volume keyframe: [t] = ms from the clip's start in the source, [gain] 0..2 (1 = clip volume). */
data class VolKey(val t: Long, val gain: Float)

/** Volume multiplier at [ms] (source ms from the clip start) between keyframes (kept sorted by time). */
fun volAt(keys: List<VolKey>?, ms: Long): Float {
    if (keys.isNullOrEmpty()) return 1f
    val k = keys
    if (ms <= k.first().t) return k.first().gain
    if (ms >= k.last().t) return k.last().gain
    val i = k.indexOfLast { it.t <= ms }
    val a = k[i]; val b = k[i + 1]
    val p = (ms - a.t).toFloat() / (b.t - a.t).coerceAtLeast(1)
    return a.gain + (b.gain - a.gain) * p
}
