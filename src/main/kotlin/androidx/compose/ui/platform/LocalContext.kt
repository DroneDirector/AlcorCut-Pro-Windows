package androidx.compose.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf

/** The app (folders, settings, files) for the shared screens. */
val LocalContext = staticCompositionLocalOf<android.content.Context> { android.content.Context.app }
