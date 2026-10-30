package com.awxkee.jxlcoder

/** JPEG XL saving on Windows with cjxl (libjxl, BSD 3-Clause), which comes with the app. */
enum class JxlEffort { LIGHTNING, THUNDER, FALCON, CHEETAH, HARE, WOMBAT, SQUIRREL, KITTEN, TORTOISE }
object JxlCoder {
    fun encode(bitmap: android.graphics.Bitmap, quality: Int = 90, effort: JxlEffort = JxlEffort.SQUIRREL): ByteArray =
        com.dronedirector.veyrocutpro.editor.WinEncode.jxl(bitmap, quality)
            ?: throw IllegalStateException(com.dronedirector.veyrocutpro.editor.WinEncode.lastError ?: "failed")
}
