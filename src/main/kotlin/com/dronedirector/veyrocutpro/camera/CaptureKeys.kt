package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dronedirector.veyrocutpro.editor.CaptureShortcuts

/** Keyboard shortcuts in Capture (Bluetooth / USB keyboard); the keys are changeable. Own code. */
object CaptureKeys {
    /** The shortcuts list is open. */
    var show by mutableStateOf(false)

    fun run(c: Context, cam: ProCameraController, e: KeyEvent): Boolean {
        if (e.action != KeyEvent.ACTION_DOWN) return false
        CaptureShortcuts.load(c)
        val id = CaptureShortcuts.actionFor(e) ?: return false
        if (e.repeatCount > 0 && id !in setOf("zoomin", "zoomout", "evup", "evdown")) return true
        val s = cam.state.value
        when (id) {
            "shutter" -> cam.shutter()
            "zoomin" -> cam.setZoom(s.zoom * 1.12f)
            "zoomout" -> cam.setZoom(s.zoom / 1.12f)
            "zoom1" -> cam.setZoom(1f)
            "evup" -> cam.setEv(s.ev + 1)
            "evdown" -> cam.setEv(s.ev - 1)
            "aelock" -> cam.toggleAeLock()
            "awblock" -> cam.toggleAwbLock()
            "autofocus" -> cam.setFocusAuto()
            "flip" -> cam.flipCamera()
            "flash" -> cam.setFlash(FlashSetting.entries[(s.flash.ordinal + 1) % FlashSetting.entries.size])
            "timer" -> cam.setTimer(when (s.timerSec) { 0 -> 3; 3 -> 10; else -> 0 })
            "nextmode" -> CaptureMode.entries.let { m -> cam.setMode(m[(s.mode.ordinal + 1) % m.size]) }
            "prevmode" -> CaptureMode.entries.let { m -> cam.setMode(m[(s.mode.ordinal - 1 + m.size) % m.size]) }
            "grid" -> cam.setGrid(GridMode.entries[(s.grid.ordinal + 1) % GridMode.entries.size])
            "level" -> cam.toggleLevel()
            "histogram" -> cam.toggleHistogram()
            "zebras" -> cam.toggleZebras()
            "peaking" -> cam.togglePeaking()
            "falsecolor" -> cam.toggleFalseColor()
            "shortcuts" -> show = true
            else -> return false
        }
        return true
    }
}
