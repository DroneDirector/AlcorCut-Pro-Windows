package com.dronedirector.veyrocutpro.camera

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Simple / Pro layout. Simple shows the everyday modes and tools only (Photo, Video and a few
 * smart modes; the main editor panels); Pro shows everything. Nothing is lost — switching back
 * brings every control and setting back as it was.
 */
object SimpleMode {
    var on by mutableStateOf(false)

    private val REGULAR = listOf(CaptureMode.PHOTO, CaptureMode.VIDEO)
    private val SMART = listOf(CaptureMode.PORTRAIT, CaptureMode.PANORAMA, CaptureMode.LONG_EXPOSURE, CaptureMode.TIMELAPSE, CaptureMode.SLOW_MO)

    fun regular(): List<CaptureMode> = if (on) REGULAR else CaptureMode.REGULAR
    fun smart(): List<CaptureMode> = if (on) SMART else CaptureMode.SMART
    fun allowed(m: CaptureMode) = !on || m in REGULAR || m in SMART

    fun load(c: Context) { on = c.getSharedPreferences("procam", Context.MODE_PRIVATE).getBoolean("ui_simple", false) }
    fun set(c: Context, v: Boolean) {
        on = v
        c.getSharedPreferences("procam", Context.MODE_PRIVATE).edit().putBoolean("ui_simple", v).apply()
    }
}
