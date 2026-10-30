package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf

/** The window as a phone view (used for haptics, which a PC doesn't have). */
val LocalView = staticCompositionLocalOf { android.view.View() }
