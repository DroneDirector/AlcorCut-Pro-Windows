package com.awxkee.jxlcoder

/** JPEG XL saving isn't on Windows yet. */
enum class JxlEffort { LIGHTNING, THUNDER, FALCON, CHEETAH, HARE, WOMBAT, SQUIRREL, KITTEN, TORTOISE }
object JxlCoder {
    fun encode(bitmap: android.graphics.Bitmap, quality: Int = 90, effort: JxlEffort = JxlEffort.SQUIRREL): ByteArray =
        throw UnsupportedOperationException("not on Windows yet")
}
