package com.dronedirector.veyrocutpro

/** The phone app's resource ids the shared screens name; on Windows they point at files in the app's resources. */
object R {
    object drawable { const val ic_launcher_background = 1; const val ic_launcher_foreground = 2 }
    object mipmap { const val ic_launcher = 3 }
    internal fun path(id: Int): String = when (id) {
        drawable.ic_launcher_background -> "drawable/ic_launcher_background.xml"
        drawable.ic_launcher_foreground -> "drawable/ic_launcher_foreground.xml"
        else -> "icon.png"
    }
}

/** The app itself (what a shortcut opens). */
class MainActivity
