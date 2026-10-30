package android.content.res

/** Configuration constants the shared code compares with (see LocalConfiguration). */
class Configuration {
    companion object {
        const val KEYBOARD_NOKEYS = 1
        const val KEYBOARD_QWERTY = 2
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
        const val UI_MODE_NIGHT_YES = 0x20
        const val UI_MODE_NIGHT_MASK = 0x30
    }
}
