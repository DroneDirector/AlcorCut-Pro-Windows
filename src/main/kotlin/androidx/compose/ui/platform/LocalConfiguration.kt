package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf

/** Screen size in dp, like the phone's (the window fills it in from its own size). */
class Configuration(
    @JvmField val screenWidthDp: Int = 1280,
    @JvmField val screenHeightDp: Int = 800,
) {
    @JvmField val smallestScreenWidthDp: Int = minOf(screenWidthDp, screenHeightDp)
    @JvmField val orientation: Int = if (screenWidthDp >= screenHeightDp) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT
    @JvmField val densityDpi: Int = 160
    @JvmField val fontScale: Float = 1f
    @JvmField val uiMode: Int = 0x20
    companion object {
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
        const val UI_MODE_NIGHT_YES = 0x20
        const val UI_MODE_NIGHT_MASK = 0x30
    }
}

val LocalConfiguration = androidx.compose.runtime.compositionLocalOf { Configuration() }
