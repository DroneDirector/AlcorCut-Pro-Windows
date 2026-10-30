package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tethered shooting: a camera on a USB cable (PTP / MTP). Each new shot is copied into the phone
 * (Pictures/AlcorCut Pro Import/<day>), added to the filmstrip and, if wanted, opened with a develop preset.
 * It keeps running while you edit; the chip at the top of the photo shows it.
 */
object Tether {
    var running by mutableStateOf(false)
    var status by mutableStateOf("")
    var count by mutableStateOf(0)
    var develop by mutableStateOf<String?>(null)
    var openEach by mutableStateOf(true)
    /** Set by the photo editor: a new shot arrived (on the main thread). */
    var onShot: ((Uri) -> Unit)? = null
    private var job: Job? = null
    private var src: MtpSource? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(c: Context) {
        if (running) return
        val app = c.applicationContext
        status = "Connecting to the camera…"; count = 0
        MtpSource.connect(app) { s, err ->
            if (s == null) { status = err ?: "No camera found — connect it with its USB cable and set it to PTP / MTP"; return@connect }
            src = s; running = true
            job = scope.launch {
                // What's on the card already isn't imported: only shots taken from now on.
                var known = try { s.handles() } catch (_: Throwable) { emptySet() }
                status = "Waiting for shots — take a photo on the camera"
                com.dronedirector.veyrocutpro.ui.AppLog.i("Tether: connected, ${known.size} file(s) already on the camera")
                var fails = 0
                while (isActive) {
                    delay(1500)
                    val now = try { s.handles() } catch (_: Throwable) { null }
                    if (now == null) { if (++fails >= 4) { status = "The camera was disconnected"; break }; continue }
                    fails = 0
                    for (h in now - known) {
                        val item = try { s.info(h) } catch (_: Throwable) { null } ?: continue
                        if (item.video) continue
                        status = "Copying ${item.name}…"
                        // A camera may still be writing the file: wait a moment first.
                        delay(800)
                        val res = ProImport.run(app, listOf(item), ProImport.Options(0, "", true, null, null, develop), open = { s.open(app, it) }, progress = { _, _, _ -> }, stop = { !isActive })
                        res.firstOrNull()?.let { (u, _) ->
                            count++
                            withContext(Dispatchers.Main) { onShot?.invoke(u) }
                        }
                        status = "Waiting for shots — $count received"
                    }
                    known = known + now
                }
                stopInternal()
            }
        }
    }

    private fun stopInternal() { running = false; try { src?.close() } catch (_: Throwable) {}; src = null }
    fun stop() { job?.cancel(); job = null; stopInternal(); status = "Stopped — $count shot(s) received" }
}

/** Tethered shooting: start / stop, the develop preset, open each shot. */
@Composable
fun TetherDialog(onDismiss: () -> Unit) {
    val c = LocalContext.current
    UserPresets.init(c)
    AlertDialog(onDismissRequest = onDismiss, containerColor = Ed.Panel,
        title = { Text("Tethered shooting", color = Pro.Fg) },
        text = {
            Column {
                Text("Connect the camera with its USB cable and set it to PTP / MTP (or 'PC connection'). Every photo you take then comes straight into the filmstrip.", color = Pro.Dim, fontSize = 12.sp)
                if (Tether.status.isNotBlank()) Text(Tether.status, color = Pro.Amber, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
                Text("Develop preset for new shots", color = Pro.Fg, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillChip("None", Tether.develop == null) { Tether.develop = null }
                    UserPresets.list.forEach { p -> PillChip(p.name, Tether.develop == p.id) { Tether.develop = p.id } }
                }
                if (UserPresets.list.isEmpty()) Text("Save a look under Presets → My presets to use it here.", color = Pro.Dim, fontSize = 11.sp)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillChip("Open each new shot", Tether.openEach) { Tether.openEach = true }
                    PillChip("Only add to the filmstrip", !Tether.openEach) { Tether.openEach = false }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (Tether.running) Tether.stop() else Tether.start(c) }) { Text(if (Tether.running) "STOP" else "START", color = Pro.Amber) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE", color = Pro.Dim) } },
    )
}
