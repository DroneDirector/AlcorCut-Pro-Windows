package com.dronedirector.veyrocutpro.camera

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** AI upscale settings (off unless the user turns it on). */
object UpscaleSettings {
    private const val PREFS = "procam"
    var enabled by mutableStateOf(false)     // shows the AI sizes in the photo size picker
        private set
    /** Size chosen for the current photo (set by the camera from the selected AI size). */
    var targetMp by mutableIntStateOf(0)
    var high by mutableStateOf(false)         // High quality: slower, keeps more real detail
        private set
    var keepOriginal by mutableStateOf(true)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = p.getBoolean("ai_upscale_on", false)
        high = p.getBoolean("ai_upscale_high", false)
        keepOriginal = p.getBoolean("ai_upscale_keep", true)
        loaded = true
    }

    fun set(context: Context, on: Boolean = enabled, hq: Boolean = high, keep: Boolean = keepOriginal) {
        enabled = on; high = hq; keepOriginal = keep
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("ai_upscale_on", on).putBoolean("ai_upscale_high", hq).putBoolean("ai_upscale_keep", keep).apply()
    }
}
