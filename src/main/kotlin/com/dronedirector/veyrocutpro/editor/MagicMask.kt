package com.dronedirector.veyrocutpro.editor

import java.util.UUID

/** A Magic Mask selection on a clip (or a video layer's clip): where it was made and what was picked. */
data class MagicSel(
    val id: String = UUID.randomUUID().toString(),
    /** Source time (ms) of the frame the prompts were made on. */
    val srcMs: Long = 0L,
    val points: List<MaskPoint> = emptyList(),
    /** One-point picks: 0 = the AI's best guess, 1 = whole object, 2 = part, 3 = detail. */
    val choice: Int = 0,
    /** Source range the tracking covers, and how many frames it has (0 = not tracked yet). */
    val fromMs: Long = 0L,
    val toMs: Long = 0L,
    val frames: Int = 0,
)
