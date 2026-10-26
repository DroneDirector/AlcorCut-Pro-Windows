package com.dronedirector.veyrocutpro.ui

import androidx.compose.runtime.Composable

/**
 * Layouts for an external monitor. Only when the app is shown on a monitor (Samsung DeX, HDMI /
 * USB-C desktop mode, any display other than the device's own): on the phone's or tablet's own
 * screen this is always [NONE], so those layouts never change. Unplugging switches back at once
 * (the screen isn't reloaded, so the open edit stays).
 */
enum class MonitorClass { NONE, WIDE, ULTRA_WIDE, SUPER_ULTRA_WIDE }

/** Which monitor layout to use for a window of [widthDp] × [heightDp] (NONE off a monitor). */
@Composable
fun monitorClass(widthDp: Float, heightDp: Float): MonitorClass {
    if (!onExternalMonitor() || heightDp <= 0f || widthDp <= heightDp) return MonitorClass.NONE
    val r = widthDp / heightDp
    return when {
        r >= 2.9f -> MonitorClass.SUPER_ULTRA_WIDE   // 32:9 (3.56)
        r >= 2.0f -> MonitorClass.ULTRA_WIDE         // 21:9 (2.33–2.4)
        else -> MonitorClass.WIDE                    // 16:9 / 16:10
    }
}
