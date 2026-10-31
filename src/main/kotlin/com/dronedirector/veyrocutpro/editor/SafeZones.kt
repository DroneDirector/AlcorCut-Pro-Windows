package com.dronedirector.veyrocutpro.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro

/**
 * Platform safe zones on the video viewer: where the app's own buttons, captions and names cover
 * the picture on TikTok, Reels and Shorts, plus the broadcast action / title safe areas.
 * The platform margins are approximate (the apps change their layouts) — a guide, not a rule.
 * Only drawn on the viewer; never exported.
 */
object SafeZones {
    /** Margins as a share of the frame: left, top, right, bottom. */
    class Zone(val label: String, val l: Float, val t: Float, val r: Float, val b: Float)

    val zones = listOf(
        Zone("Off", 0f, 0f, 0f, 0f),
        Zone("TikTok", 0.04f, 0.07f, 0.15f, 0.20f),
        Zone("Reels", 0.06f, 0.14f, 0.06f, 0.35f),
        Zone("Shorts", 0.04f, 0.10f, 0.15f, 0.25f),
        Zone("Action / title safe", 0.035f, 0.035f, 0.035f, 0.035f),
    )

    var current by mutableIntStateOf(0)
}

@Composable
fun SafeZoneOverlay(modifier: Modifier = Modifier) {
    val i = SafeZones.current
    if (i <= 0 || i >= SafeZones.zones.size) return
    val z = SafeZones.zones[i]
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val x0 = w * z.l; val y0 = h * z.t; val x1 = w * (1 - z.r); val y1 = h * (1 - z.b)
            val shade = Color.Black.copy(alpha = 0.35f)
            drawRect(shade, Offset.Zero, Size(w, y0))
            drawRect(shade, Offset(0f, y1), Size(w, h - y1))
            drawRect(shade, Offset(0f, y0), Size(x0, y1 - y0))
            drawRect(shade, Offset(x1, y0), Size(w - x1, y1 - y0))
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            drawRect(Pro.Amber, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(1.5.dp.toPx(), pathEffect = dash))
            if (i == SafeZones.zones.lastIndex) {
                // Title safe (90 %) inside action safe (93 %).
                val tx = w * 0.05f; val ty = h * 0.05f
                drawRect(Color.White.copy(alpha = 0.7f), Offset(tx, ty), Size(w - 2 * tx, h - 2 * ty), style = Stroke(1.dp.toPx(), pathEffect = dash))
            }
        }
        Text(z.label.uppercase(), style = Pro.Label.copy(color = Pro.Amber, fontSize = 9.sp), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
    }
}
