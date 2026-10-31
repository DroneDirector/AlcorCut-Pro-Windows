package com.dronedirector.veyrocutpro.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import com.dronedirector.veyrocutpro.ui.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import kotlin.math.roundToInt

/** Speed / Duration, time interpolation, linking, grouping and the markers menu (buttons under the picture). Own code. */

/** A layer or audio clip as these dialogs show it. */
data class TrackItem(val id: String, val name: String, val audio: Boolean, val linked: Boolean, val group: String?, val locked: Boolean)

@Composable
fun SpeedDurationDialog(c: Clip, onDismiss: () -> Unit, onApply: (speed: Float, reverse: Boolean) -> Unit) {
    val src = (c.endMs - c.startMs).coerceAtLeast(1)
    var pct by remember { mutableStateOf((c.speed * 100).roundToInt().toString()) }
    var dur by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", src / c.speed / 1000f)) }
    var reverse by remember { mutableStateOf(false) }
    fun speedOf(): Float? = pct.replace(',', '.').toFloatOrNull()?.div(100f)?.takeIf { it in 0.05f..20f }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pro.PanelSolid,
        title = { Text("Speed / Duration", color = Color.White) },
        text = {
            Column {
                Text(c.name, color = Pro.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (c.ramp != null) Text("This clip has a speed ramp — a fixed speed here replaces it.", color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                OutlinedTextField(pct, { v ->
                    pct = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
                    speedOf()?.let { s -> dur = String.format(java.util.Locale.US, "%.2f", src / s / 1000f) }
                }, singleLine = true, label = { Text("Speed (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = fieldColors())
                OutlinedTextField(dur, { v ->
                    dur = v.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
                    dur.replace(',', '.').toFloatOrNull()?.takeIf { it > 0.05f }?.let { d -> pct = ((src / 1000f / d) * 100).roundToInt().toString() }
                }, singleLine = true, label = { Text("Duration (seconds)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp), colors = fieldColors())
                Text("5 % to 2000 %. Speed and duration follow each other; the clips after it move up or back.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Play backwards", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(reverse, { reverse = it }, colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
                }
                Row(Modifier.padding(top = 4.dp)) {
                    for (p in listOf(25, 50, 100, 200, 400)) Text("$p%", color = Pro.Amber, fontSize = 12.sp,
                        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50)).background(Ed.Raised).clickable {
                            pct = "$p"; dur = String.format(java.util.Locale.US, "%.2f", src / (p / 100f) / 1000f)
                        }.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        },
        confirmButton = { TextButton(enabled = speedOf() != null, onClick = { speedOf()?.let { onApply(it, reverse) } }) { Text("Apply", color = Pro.Amber) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )
}

/** 0 frame sampling, 1 optical flow (smooth slow motion), 2 motion blur. */
@Composable
fun TimeInterpolationDialog(c: Clip, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pro.PanelSolid,
        title = { Text("Time interpolation", color = Color.White) },
        text = {
            Column {
                Text("How the frames of a slowed-down or sped-up clip are made. ${c.name}", color = Pro.Dim, fontSize = 12.sp)
                @Composable
                fun Opt(t: String, d: String, i: Int) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised).clickable { onPick(i) }.padding(12.dp)) {
                        Text(t, color = Color.White, fontSize = 14.sp)
                        Text(d, color = Pro.Dim, fontSize = 11.sp)
                    }
                }
                Opt("Frame sampling", "Repeats or skips frames — fast, the normal way. Slow motion can look jerky.", 0)
                Opt("Optical flow", "Makes the in-between frames from the motion (on this phone) — smooth slow motion at the clip's speed. Takes a while; the result is kept.", 1)
                Opt("Motion blur", "Adds a film camera's shutter blur to moving things — good for sped-up clips and timelapses.", 2)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )
}

@Composable
fun LinkTracksDialog(items: List<TrackItem>, onDismiss: () -> Unit, onSet: (id: String, audio: Boolean, linked: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pro.PanelSolid,
        title = { Text("Link tracks to the video", color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Linked tracks move with the main video when you cut, trim (with \"closes the gap\") or delete clips. Unlinked ones stay exactly where they are — for music that must not shift.", color = Pro.Dim, fontSize = 12.sp)
                if (items.isEmpty()) Text("No layers or audio tracks yet.", color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                for (it in items) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(it.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (it.audio) "Audio" else "Layer", color = Pro.Dim, fontSize = 11.sp)
                    }
                    Switch(it.linked, { on -> onSet(it.id, it.audio, on) }, colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
                }
                if (items.isNotEmpty()) Row(Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { items.forEach { onSet(it.id, it.audio, true) } }) { Text("Link all", color = Pro.Amber) }
                    TextButton(onClick = { items.forEach { onSet(it.id, it.audio, false) } }) { Text("Unlink all", color = Pro.Amber) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Pro.Amber) } },
    )
}

@Composable
fun GroupDialog(items: List<TrackItem>, onDismiss: () -> Unit, onGroup: (List<TrackItem>) -> Unit, onUngroup: (String) -> Unit) {
    val picked = remember { mutableStateListOf<String>() }
    val groups = items.mapNotNull { it.group }.distinct()
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pro.PanelSolid,
        title = { Text("Group / ungroup", color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Pick two or more layers and audio clips: grouped, they move together when you drag one of them on the timeline.", color = Pro.Dim, fontSize = 12.sp)
                for (it in items) {
                    val on = it.id in picked
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Pro.Amber.copy(alpha = 0.18f) else Ed.Raised)
                        .clickable { if (on) picked.remove(it.id) else picked.add(it.id) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (on) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null, tint = Pro.Amber, modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(it.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((if (it.audio) "Audio" else "Layer") + (it.group?.let { g -> " · group " + (groups.indexOf(g) + 1) } ?: ""), color = Pro.Dim, fontSize = 11.sp)
                        }
                    }
                }
                if (groups.isNotEmpty()) {
                    HorizontalDivider(color = com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                    groups.forEachIndexed { i, g ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Group ${i + 1} · " + items.count { it.group == g } + " items", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onUngroup(g) }) { Text("Ungroup", color = Pro.Red) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = picked.size >= 2, onClick = { onGroup(items.filter { it.id in picked }); picked.clear() }) { Text("Group", color = Pro.Amber) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done", color = Color.White) } },
    )
}

/** Markers button (next to Add marker): go to, edit, clear, and export / import marker lists. */
@Composable
fun MarkersMenuButton(
    markers: List<Long>, notes: Map<Long, String>, playhead: Long, fps: Int,
    onGo: (Long) -> Unit, onEdit: (old: Long, new: Long, note: String) -> Unit, onClear: (Long) -> Unit, onClearAll: () -> Unit,
    onImport: (List<Pair<Long, String>>) -> Unit, onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var sub by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Long?>(null) }
    val sorted = markers.sorted()
    val near = sorted.minByOrNull { kotlin.math.abs(it - playhead) }?.takeIf { kotlin.math.abs(it - playhead) < 1500 }
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { u: Uri? ->
        if (u == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(u)?.bufferedWriter()?.use { w ->
                w.write("Timecode,Seconds,Note\n")
                for (m in sorted) w.write("${MarkerList.tc(m, fps)},${String.format(java.util.Locale.US, "%.3f", m / 1000.0)},\"${(notes[m] ?: "").replace("\"", "\"\"")}\"\n")
            }
            onMessage("${sorted.size} markers exported")
        } catch (_: Throwable) { onMessage("Couldn't write the marker list") }
    }
    val importFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u == null) return@rememberLauncherForActivityResult
        val l = try { context.contentResolver.openInputStream(u)?.bufferedReader()?.use { MarkerList.parse(it.readText(), fps) } ?: emptyList() } catch (_: Throwable) { emptyList() }
        if (l.isEmpty()) onMessage("No markers found in that file") else onImport(l)
    }
    Box {
        TransportIcon(Icons.Filled.Bookmarks, "Markers") { open = true; sub = null }
        com.dronedirector.veyrocutpro.ui.ScrollMenu(open, onDismissRequest = { open = false }, width = 280.dp) {
            @Composable
            fun Item(t: String, enabled: Boolean = true, a: () -> Unit) {
                Text(t, color = if (enabled) Color.White else Pro.Dim, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = a).padding(horizontal = 14.dp, vertical = 9.dp))
            }
            Item("Go to marker ▸", sorted.isNotEmpty()) { sub = if (sub == "go") null else "go" }
            if (sub == "go") Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                for (m in sorted) Text(MarkerList.tc(m, fps) + (notes[m]?.let { "  ·  $it" } ?: ""), color = Pro.Amber, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { open = false; onGo(m) }.padding(start = 26.dp, end = 14.dp, top = 6.dp, bottom = 6.dp))
            }
            Item("Edit marker…", near != null) { open = false; editing = near }
            Item("Clear marker at the playhead", near != null) { open = false; near?.let(onClear) }
            Item("Clear all markers", sorted.isNotEmpty()) { open = false; onClearAll() }
            HorizontalDivider(color = com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.1f))
            Item("Marker lists ▸") { sub = if (sub == "lists") null else "lists" }
            if (sub == "lists") {
                Text("Export marker list (.csv)", color = if (sorted.isNotEmpty()) Color.White else Pro.Dim, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = sorted.isNotEmpty()) { open = false; try { exportTo.launch("Markers.csv") } catch (_: Throwable) {} }.padding(start = 26.dp, end = 14.dp, top = 7.dp, bottom = 7.dp))
                Text("Import marker list (.csv, .txt)", color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().clickable { open = false; try { importFrom.launch(arrayOf("text/*", "*/*")) } catch (_: Throwable) {} }.padding(start = 26.dp, end = 14.dp, top = 7.dp, bottom = 7.dp))
            }
        }
    }
    editing?.let { m ->
        var note by remember(m) { mutableStateOf(notes[m] ?: "") }
        var toHead by remember(m) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null }, containerColor = Pro.PanelSolid,
            title = { Text("Marker at " + MarkerList.tc(m, fps), color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(note, { note = it.take(120) }, singleLine = true, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    if (m != playhead) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Move it to the playhead (" + MarkerList.tc(playhead, fps) + ")", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(toHead, { toHead = it }, colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null; onEdit(m, if (toHead) playhead else m, note.trim()) }) { Text("Save", color = Pro.Amber) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel", color = Color.White) } },
        )
    }
}

/** Marker lists in and out: CSV with a timecode (HH:MM:SS:FF), seconds or a plain list of times. */
object MarkerList {
    fun tc(ms: Long, fps: Int): String {
        val f = fps.coerceAtLeast(1)
        val s = ms / 1000
        return String.format(java.util.Locale.US, "%02d:%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60, ((ms % 1000) * f / 1000))
    }

    fun parse(text: String, fps: Int): List<Pair<Long, String>> {
        val out = ArrayList<Pair<Long, String>>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val cells = splitCsv(line)
            val first = cells.firstOrNull()?.trim() ?: continue
            val ms = time(first, fps) ?: cells.getOrNull(1)?.let { time(it.trim(), fps) } ?: continue
            val note = cells.drop(1).lastOrNull { time(it.trim(), fps) == null }?.trim().orEmpty()
            out += ms to note
        }
        return out.distinctBy { it.first }
    }

    private fun time(s: String, fps: Int): Long? {
        val p = s.split(':', ';')
        return try {
            when (p.size) {
                4 -> ((p[0].toLong() * 3600 + p[1].toLong() * 60 + p[2].toLong()) * 1000) + p[3].toLong() * 1000 / fps.coerceAtLeast(1)
                3 -> ((p[0].toLong() * 3600 + p[1].toLong() * 60) * 1000) + (p[2].replace(',', '.').toDouble() * 1000).toLong()
                2 -> (p[0].toLong() * 60 * 1000) + (p[1].replace(',', '.').toDouble() * 1000).toLong()
                1 -> s.replace(',', '.').toDoubleOrNull()?.let { (it * 1000).toLong() }
                else -> null
            }
        } catch (_: Throwable) { null }
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> q = !q
                (ch == ',' || ch == '\t') && !q -> { out += sb.toString(); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
