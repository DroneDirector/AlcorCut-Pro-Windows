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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

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

/** Connect to a phone: the 4-digit PIN shown on the phone (Capture → gear → Remote & output → Connect to PC). */
@Composable
fun PhonePinDialog(c: com.dronedirector.veyrocutpro.camera.ProCameraController) {
    val d = c.pinRequest ?: return
    var pin by androidx.compose.runtime.remember(d) { androidx.compose.runtime.mutableStateOf(c.lastPin(d)) }
    var busy by androidx.compose.runtime.remember(d) { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(c.pinError) { busy = false }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { c.cancelPin() }, containerColor = panelHere(),
        title = { Text("Connect to ${d.name}", color = androidx.compose.ui.graphics.Color.White) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("Type the PIN shown on the phone (Capture → gear → Remote & output → Connect to PC).", color = Pro.Dim, fontSize = 13.sp)
                androidx.compose.material3.OutlinedTextField(
                    value = pin, onValueChange = { v -> pin = v.filter { it.isDigit() }.take(4) }, singleLine = true,
                    label = { Text("PIN") },
                    modifier = Modifier.padding(top = 10.dp),
                )
                c.pinError?.let { Text(it, color = Pro.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = pin.length == 4 && !busy, onClick = { busy = true; c.connectPhone(d, pin) }) {
                Text(if (busy) "Connecting…" else "Connect", color = Pro.Amber)
            }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { c.cancelPin() }) { Text("Cancel", color = androidx.compose.ui.graphics.Color.White) } },
    )
}
