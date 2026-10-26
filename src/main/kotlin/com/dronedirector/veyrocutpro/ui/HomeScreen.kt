package com.dronedirector.veyrocutpro.ui



enum class AppScreen { HOME, CAPTURE, EDITOR, PHOTO_EDITOR, VIDEO_EDITOR }

/** Which screen the app opens on (the start screen unless the user picked one in a settings menu). */
object StartScreen {
    private const val KEY = "start_screen"
    fun get(context: android.content.Context): AppScreen {
        val v = context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).getString(KEY, null)
        return AppScreen.entries.firstOrNull { it.name == v && it in listOf(AppScreen.CAPTURE, AppScreen.PHOTO_EDITOR, AppScreen.VIDEO_EDITOR) } ?: AppScreen.HOME
    }
    fun set(context: android.content.Context, screen: AppScreen) {
        context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).edit().putString(KEY, screen.name).apply()
    }
}
