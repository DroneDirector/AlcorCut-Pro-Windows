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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected

/** The Cast button's place (Capture and the editors). */
object CastPlace {
    var version by mutableIntStateOf(0)
        private set
    fun get(c: Context, screen: String): Int { version; return c.getSharedPreferences("procam", Context.MODE_PRIVATE).getInt("cast_place_$screen", 0) }
    fun set(c: Context, screen: String, v: Int) { c.getSharedPreferences("procam", Context.MODE_PRIVATE).edit().putInt("cast_place_$screen", v).apply(); version++ }
}

/**
 * Casting to a TV from the PC: the Chromecast's own media player shows the live stream from this PC
 * ([com.dronedirector.veyrocutpro.camera.CastFeed]); [com.dronedirector.veyrocutpro.camera.Chromecast] talks to the TV.
 */
object CastLink {
    var device by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var connecting by androidx.compose.runtime.mutableStateOf(false)
        private set
    private val sources = ArrayList<suspend () -> Bitmap?>()
    private var cc: com.dronedirector.veyrocutpro.camera.Chromecast? = null
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "cast").apply { isDaemon = true } }

    private suspend fun frame(): Bitmap? = sources.lastOrNull()?.invoke()

    fun use(src: suspend () -> Bitmap?) { sources.remove(src); sources.add(src) }
    fun release(src: suspend () -> Bitmap?) { sources.remove(src) }

    fun connect(d: com.dronedirector.veyrocutpro.camera.CastDevice) {
        if (connecting) return
        connecting = true
        io.execute {
            cc?.close()
            val c = com.dronedirector.veyrocutpro.camera.Chromecast(d) { why ->
                javax.swing.SwingUtilities.invokeLater {
                    if (cc != null) { cc = null; device = null; connecting = false; com.dronedirector.veyrocutpro.camera.CastFeed.stop(); why?.let { android.widget.Toast.makeText(android.content.Context.app, it, android.widget.Toast.LENGTH_LONG).show() } }
                }
            }
            val err = c.connect()
            if (err != null) { javax.swing.SwingUtilities.invokeLater { connecting = false; android.widget.Toast.makeText(android.content.Context.app, err, android.widget.Toast.LENGTH_LONG).show() }; return@execute }
            cc = c
            com.dronedirector.veyrocutpro.camera.CastFeed.host = c.localAddress
            javax.swing.SwingUtilities.invokeLater { com.dronedirector.veyrocutpro.camera.CastFeed.start { frame() } }
            // The TV is told to play once a few seconds of stream are ready (an empty live list makes it give up).
            var waited = 0
            while (!c.closed && (com.dronedirector.veyrocutpro.camera.CastFeed.ready() < 3 || !c.ready) && waited < 80) { Thread.sleep(250); waited++ }
            val url = com.dronedirector.veyrocutpro.camera.CastFeed.url()
            if (!c.closed && url != null) c.play(url)
            javax.swing.SwingUtilities.invokeLater { if (!c.closed) device = d.name; connecting = false }
        }
    }

    fun stop() {
        val c = cc; cc = null
        io.execute { c?.stop() }
        com.dronedirector.veyrocutpro.camera.CastFeed.stop()
        device = null; connecting = false
    }
}

/** Gear setting: where the cast button goes. */
@Composable
fun CastPlaceSetting(screen: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Text("Cast button (Chromecast)", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    com.dronedirector.veyrocutpro.editor.Chips(listOf("On the picture (corner)", "Below the picture"), CastPlace.get(ctx, screen)) { CastPlace.set(ctx, screen, it) }
    Text("Shows the live picture on a TV with a Chromecast or Google TV on the same Wi-Fi (a few seconds behind). Hide the button in the layout menu.", color = Pro.Dim, fontSize = 11.sp)
    Text("For a projector or a TV without Chromecast: Windows key + K (wireless display).", color = Pro.Dim, fontSize = 11.sp)
}

/** The cast button: pick a TV, or stop casting. [source] gives the current picture. */
@Composable
fun CastButton(source: suspend () -> Bitmap?, modifier: Modifier = Modifier, small: Boolean = false, sound: com.dronedirector.veyrocutpro.camera.CastFeed.Sound = com.dronedirector.veyrocutpro.camera.CastFeed.Sound.MIC) {
    val src by androidx.compose.runtime.rememberUpdatedState(source)
    val stable: suspend () -> Bitmap? = androidx.compose.runtime.remember { suspend { src() } }
    androidx.compose.runtime.DisposableEffect(Unit) { CastLink.use(stable); com.dronedirector.veyrocutpro.camera.CastFeed.setSound(sound); onDispose { CastLink.release(stable) } }
    var picking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val on = CastLink.device != null
    val accent = Pro.Amber
    com.dronedirector.veyrocutpro.camera.CastFeed.accent = android.graphics.Color.argb((accent.alpha * 255).toInt(), (accent.red * 255).toInt(), (accent.green * 255).toInt(), (accent.blue * 255).toInt())
    androidx.compose.foundation.layout.Box(
        modifier.size(if (small) 34.dp else 40.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (on) accent else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
            .border(1.5.dp, accent.copy(alpha = if (on || CastLink.connecting) 1f else 0.6f), androidx.compose.foundation.shape.CircleShape)
            .clickable { picking = true },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Icon(if (on) androidx.compose.material.icons.Icons.Filled.CastConnected else androidx.compose.material.icons.Icons.Filled.Cast, contentDescription = "Cast to TV",
            tint = if (on) androidx.compose.ui.graphics.Color.Black else accent, modifier = Modifier.size(if (small) 18.dp else 22.dp))
    }
    if (picking) CastPicker(onDismiss = { picking = false })
}

@Composable
private fun CastPicker(onDismiss: () -> Unit) {
    val found = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf<com.dronedirector.veyrocutpro.camera.CastDevice>() }
    var looking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (CastLink.device == null) {
            val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.dronedirector.veyrocutpro.camera.Chromecast.discover() }
            for (d in list) if (found.none { it.address == d.address }) found.add(d)
            looking = false
            kotlinx.coroutines.delay(3000)
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = panelHere(),
        title = { Text(CastLink.device?.let { "Casting to $it" } ?: "Cast to a TV", color = androidx.compose.ui.graphics.Color.White) },
        text = {
            androidx.compose.foundation.layout.Column {
                when {
                    CastLink.device != null -> Text("The live picture is on the TV (a few seconds behind).", color = Pro.Dim)
                    CastLink.connecting -> Text("Connecting…", color = Pro.Dim)
                    found.isEmpty() -> Text(if (looking) "Looking for a Chromecast or Google TV on this Wi-Fi…" else "No Chromecast or Google TV found on this network yet. Still looking… (the PC and the TV must be on the same network)", color = Pro.Dim)
                    else -> found.forEach { d ->
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(fgHere().copy(alpha = 0.06f))
                                .clickable { CastLink.connect(d); onDismiss() }.padding(14.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Filled.Cast, contentDescription = null, tint = Pro.Amber, modifier = Modifier.size(20.dp))
                            Text(d.name, color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (CastLink.device != null) androidx.compose.material3.TextButton(onClick = { CastLink.stop(); onDismiss() }) { Text("STOP CASTING", color = Pro.Amber) }
            else androidx.compose.material3.TextButton(onClick = onDismiss) { Text("CANCEL", color = Pro.Amber) }
        },
    )
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
