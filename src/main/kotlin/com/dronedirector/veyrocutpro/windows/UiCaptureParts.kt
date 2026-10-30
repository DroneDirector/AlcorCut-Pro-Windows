package com.dronedirector.veyrocutpro.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

/** The Cast button's place (Capture and the editors). */
object CastPlace {
    var version by mutableIntStateOf(0)
        private set
    fun get(c: Context, screen: String): Int { version; return c.getSharedPreferences("procam", Context.MODE_PRIVATE).getInt("cast_place_$screen", 0) }
    fun set(c: Context, screen: String, v: Int) { c.getSharedPreferences("procam", Context.MODE_PRIVATE).edit().putInt("cast_place_$screen", v).apply(); version++ }
}

/** Showing the picture on a TV: on a PC that's Windows' own casting (Windows key + K), so there's no button. */
@Composable
fun CastButton(source: suspend () -> Bitmap?, modifier: Modifier = Modifier, small: Boolean = false) {}

@Composable
fun CastPlaceSetting(screen: String) {
    Text("Show Capture on a TV or projector with Windows' own casting: press Windows key + K and pick the screen (Miracast / wireless display).", color = Pro.Dim, fontSize = 11.sp)
}

/** The stop-motion frames as a video (each picture fitted into the first one's shape), with the PC's encoder. */
suspend fun StopMotion.makeVideo(c: Context, list: List<Uri>, fps: Int): Uri? = com.dronedirector.veyrocutpro.camera.stopMotionVideo(c, list, fps)
