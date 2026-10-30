package com.dronedirector.veyrocutpro.camera

import kotlin.math.roundToInt

/** A still-photo resolution a camera supports. [highRes] sizes need the slower high-resolution mode. */
data class PhotoSize(val width: Int, val height: Int, val highRes: Boolean, val square: Boolean = false, val aiMp: Int = 0) {
    private val pixels: Long get() = if (square) height.toLong() * height else width.toLong() * height
    val megapixels: Float get() = pixels / 1_000_000f
    val label: String
        get() = if (aiMp > 0) "AI $aiMp MP" else if (megapixels >= 10f) "${megapixels.roundToInt()} MP" else "%.1f MP".format(megapixels)
    val detail: String
        get() = if (aiMp > 0) "Shot at ${if (square) "${height}×$height" else "${width}×$height"}, AI-upscaled to $aiMp MP in the background"
        else if (square) "${height}×$height" else "${width}×$height"
    val key: String get() = if (aiMp > 0) "ai$aiMp:${width}x$height" else "${width}x$height"
}
