package android.view

/** A key press, with the same codes as on the phone (the window turns Windows keys into these). Own code. */
class KeyEvent(val action: Int, val keyCode: Int, private val meta: Int = 0, val repeatCount: Int = 0) {
    val isCtrlPressed: Boolean get() = meta and META_CTRL_ON != 0
    val isShiftPressed: Boolean get() = meta and META_SHIFT_ON != 0
    val isAltPressed: Boolean get() = meta and META_ALT_ON != 0
    val isMetaPressed: Boolean get() = meta and META_META_ON != 0
    val metaState: Int get() = meta
    fun getUnicodeChar(): Int = when (keyCode) {
        in KEYCODE_A..KEYCODE_Z -> (if (isShiftPressed) 'A' else 'a').code + (keyCode - KEYCODE_A)
        in KEYCODE_0..KEYCODE_9 -> '0'.code + (keyCode - KEYCODE_0)
        KEYCODE_SPACE -> ' '.code
        else -> 0
    }

    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val META_SHIFT_ON = 1
        const val META_ALT_ON = 2
        const val META_CTRL_ON = 0x1000
        const val META_META_ON = 0x10000

        const val KEYCODE_UNKNOWN = 0
        const val KEYCODE_DPAD_UP = 19; const val KEYCODE_DPAD_DOWN = 20; const val KEYCODE_DPAD_LEFT = 21; const val KEYCODE_DPAD_RIGHT = 22
        const val KEYCODE_DPAD_CENTER = 23
        const val KEYCODE_VOLUME_UP = 24; const val KEYCODE_VOLUME_DOWN = 25; const val KEYCODE_CAMERA = 27
        const val KEYCODE_0 = 7; const val KEYCODE_1 = 8; const val KEYCODE_2 = 9; const val KEYCODE_3 = 10; const val KEYCODE_4 = 11
        const val KEYCODE_5 = 12; const val KEYCODE_6 = 13; const val KEYCODE_7 = 14; const val KEYCODE_8 = 15; const val KEYCODE_9 = 16
        const val KEYCODE_STAR = 17; const val KEYCODE_POUND = 18
        const val KEYCODE_A = 29; const val KEYCODE_B = 30; const val KEYCODE_C = 31; const val KEYCODE_D = 32; const val KEYCODE_E = 33
        const val KEYCODE_F = 34; const val KEYCODE_G = 35; const val KEYCODE_H = 36; const val KEYCODE_I = 37; const val KEYCODE_J = 38
        const val KEYCODE_K = 39; const val KEYCODE_L = 40; const val KEYCODE_M = 41; const val KEYCODE_N = 42; const val KEYCODE_O = 43
        const val KEYCODE_P = 44; const val KEYCODE_Q = 45; const val KEYCODE_R = 46; const val KEYCODE_S = 47; const val KEYCODE_T = 48
        const val KEYCODE_U = 49; const val KEYCODE_V = 50; const val KEYCODE_W = 51; const val KEYCODE_X = 52; const val KEYCODE_Y = 53
        const val KEYCODE_Z = 54
        const val KEYCODE_COMMA = 55; const val KEYCODE_PERIOD = 56
        const val KEYCODE_TAB = 61; const val KEYCODE_SPACE = 62; const val KEYCODE_ENTER = 66; const val KEYCODE_DEL = 67
        const val KEYCODE_MINUS = 69; const val KEYCODE_EQUALS = 70; const val KEYCODE_LEFT_BRACKET = 71; const val KEYCODE_RIGHT_BRACKET = 72
        const val KEYCODE_BACKSLASH = 73; const val KEYCODE_SEMICOLON = 74; const val KEYCODE_APOSTROPHE = 75; const val KEYCODE_SLASH = 76
        const val KEYCODE_PLUS = 81
        const val KEYCODE_PAGE_UP = 92; const val KEYCODE_PAGE_DOWN = 93
        const val KEYCODE_ESCAPE = 111; const val KEYCODE_FORWARD_DEL = 112
        const val KEYCODE_MOVE_HOME = 122; const val KEYCODE_MOVE_END = 123
        const val KEYCODE_F1 = 131; const val KEYCODE_F2 = 132; const val KEYCODE_F3 = 133; const val KEYCODE_F4 = 134; const val KEYCODE_F5 = 135
        const val KEYCODE_F6 = 136; const val KEYCODE_F7 = 137; const val KEYCODE_F8 = 138; const val KEYCODE_F9 = 139; const val KEYCODE_F10 = 140
        const val KEYCODE_F11 = 141; const val KEYCODE_F12 = 142
        const val KEYCODE_NUMPAD_0 = 144; const val KEYCODE_NUMPAD_9 = 153
        const val KEYCODE_MEDIA_PLAY_PAUSE = 85
        const val KEYCODE_BACK = 4
        const val KEYCODE_GRAVE = 68
        const val KEYCODE_ALT_LEFT = 57; const val KEYCODE_ALT_RIGHT = 58
        const val KEYCODE_SHIFT_LEFT = 59; const val KEYCODE_SHIFT_RIGHT = 60
        const val KEYCODE_CTRL_LEFT = 113; const val KEYCODE_CTRL_RIGHT = 114
        const val KEYCODE_CAPS_LOCK = 115; const val KEYCODE_SCROLL_LOCK = 116
        const val KEYCODE_META_LEFT = 117; const val KEYCODE_META_RIGHT = 118
        const val KEYCODE_FUNCTION = 119; const val KEYCODE_NUM_LOCK = 143

        /** "KEYCODE_A"-style names, as on the phone. */
        private val names: Map<Int, String> by lazy {
            KeyEvent::class.java.declaredFields.filter { it.name.startsWith("KEYCODE_") && it.type == Int::class.javaPrimitiveType }
                .mapNotNull { f -> try { f.isAccessible = true; f.getInt(null) to f.name } catch (_: Throwable) { null } }
                .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.minByOrNull { it.length }!! }
        }
        @JvmStatic fun keyCodeToString(code: Int): String = names[code] ?: code.toString()
    }
}
