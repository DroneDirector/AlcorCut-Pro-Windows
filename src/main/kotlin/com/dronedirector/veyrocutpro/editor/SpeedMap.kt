package com.dronedirector.veyrocutpro.editor



/** A volume keyframe: [t] = ms from the clip's start in the source, [gain] 0..2 (1 = clip volume). */
data class VolKey(val t: Long, val gain: Float)

/** Length on the timeline of a ramped clip. */
fun Clip.rampedLength(): Long = table().let { if (it.isEmpty()) 0L else it[STEPS].toLong() }
