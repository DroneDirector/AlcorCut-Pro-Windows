package com.dronedirector.veyrocutpro.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text

/**
 * Video timeline: hold a clip for its menu; "Select multiple" turns taps into picking clips,
 * and holding a clip again offers "Deselect all". Clips are kept by id (their places change). Own code.
 */
class ClipPick {
    var on by mutableStateOf(false)
    val ids = mutableStateListOf<String>()
    /** The clip whose menu is open (index on the timeline) and where it was held (px from the timeline's left). */
    var menuFor by mutableStateOf<Int?>(null)
    var menuX by mutableFloatStateOf(0f)
    var menuY by mutableFloatStateOf(0f)

    fun start(id: String) { ids.clear(); ids.add(id); on = true }
    fun toggle(id: String) { if (id in ids) ids.remove(id) else ids.add(id) }
    fun clear() { ids.clear(); on = false }
    /** A change to the clip on screen; while picking, the same change goes to every picked clip (its own media and trims stay). */
    fun applyTo(clips: MutableList<Clip>, selected: Int, f: (Clip) -> Clip) {
        val before = clips.getOrNull(selected) ?: return
        val after = f(before)
        clips[selected] = after
        if (!on || before == after) return
        for (k in clips.indices) if (k != selected && clips[k].id in ids) {
            clips[k] = try { EditDiff.apply(before, after, clips[k], OWN) } catch (_: Throwable) { clips[k] }
        }
    }

    companion object {
        /** What belongs to each clip itself and is never copied to the others. */
        val OWN = setOf("id", "versions", "uri", "name", "durationMs", "width", "height", "startMs", "endMs", "mime", "hdr", "tenBit",
            "chroma", "bitDepth", "decoder", "undecodable", "nested", "offline", "offlineOf", "volKeys")
    }
}

/** What the clip menu can do; the editor fills these in. */
class ClipActs(
    /** Makes clip [i] the one on screen (its colour is shown and edited). */
    val select: (Int) -> Unit,
    val splitHere: (Int) -> Unit,
    val duplicate: (List<Int>) -> Unit,
    val speed: (Int) -> Unit,
    val reverse: (Int) -> Unit,
    /** Boomerang: a backwards copy straight after the clip. */
    val boomerang: (Int) -> Unit = {},
    val mute: (List<Int>) -> Unit,
    val detach: (List<Int>) -> Unit,
    val nest: (from: Int, count: Int) -> Unit,
    val delete: (Int) -> Unit,
    val deleteMany: (List<Int>) -> Unit,
    /** Colour & details: the clip's Color page (exposure, contrast, highlights, shadows…). */
    val color: (Int) -> Unit = {},
    /** Reveal in project: the clip's file picked out in the media strip. */
    val revealLibrary: (Int) -> Unit = {},
    /** Reveal on device: where the clip's file is stored. */
    val revealDevice: (Int) -> Unit = {},
)

/** The menu shown where a clip was held. */
@Composable
fun ClipMenu(clips: List<Clip>, pick: ClipPick, act: ClipActs) {
    val i = pick.menuFor ?: return
    val c = clips.getOrNull(i) ?: run { pick.menuFor = null; return }
    val x = with(LocalDensity.current) { pick.menuX.toDp() }
    val y = with(LocalDensity.current) { pick.menuY.toDp() }
    fun close() { pick.menuFor = null }
    @Composable
    fun item(t: String, color: Color = Color.White, a: () -> Unit) =
        DropdownMenuItem(text = { Text(t, color = color, fontSize = 14.sp) }, onClick = { close(); a() })
    Box(Modifier.offset(x = x, y = y)) {
        DropdownMenu(true, onDismissRequest = { close() }, containerColor = Pro.PanelSolid) {
            if (!pick.on) {
                Text("${i + 1}  ${c.name}", color = Pro.Dim, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                item("Select multiple") { pick.start(c.id); act.select(i) }
                HorizontalDivider(color = Pro.Fg.copy(alpha = 0.1f))
                item("Split at the playhead") { act.splitHere(i) }
                item("Duplicate") { act.duplicate(listOf(i)) }
                item("Speed / Duration…") { act.speed(i) }
                item("Reverse") { act.reverse(i) }
                item("Boomerang (forwards, then back)") { act.boomerang(i) }
                HorizontalDivider(color = Pro.Fg.copy(alpha = 0.1f))
                item("Colour & details…", Pro.Amber) { act.color(i) }
                item("Reveal in project") { act.revealLibrary(i) }
                item("Reveal on device") { act.revealDevice(i) }
                HorizontalDivider(color = Pro.Fg.copy(alpha = 0.1f))
                item(if (c.muted) "Turn sound on" else "Mute sound") { act.mute(listOf(i)) }
                if (!c.muted) item("Detach audio") { act.detach(listOf(i)) }
                HorizontalDivider(color = Pro.Fg.copy(alpha = 0.1f))
                item("Delete", Pro.Red) { act.delete(i) }
            } else {
                val idx = clips.indices.filter { clips[it].id in pick.ids }
                Text("${idx.size} clip" + (if (idx.size == 1) "" else "s") + " selected", color = Pro.Amber, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                item("Deselect all") { pick.clear() }
                item("Select all") { pick.ids.clear(); pick.ids.addAll(clips.map { it.id }) }
                if (c.id !in pick.ids) item("Add this clip") { pick.ids.add(c.id) } else item("Leave this clip out") { pick.ids.remove(c.id) }
                if (idx.isNotEmpty()) {
                    HorizontalDivider(color = Pro.Fg.copy(alpha = 0.1f))
                    item("Duplicate") { act.duplicate(idx) }
                    item(if (idx.all { clips[it].muted }) "Turn sound on" else "Mute sound") { act.mute(idx) }
                    item("Detach audio") { act.detach(idx) }
                    // Nesting needs clips side by side.
                    if (idx.size >= 2 && idx.last() - idx.first() == idx.size - 1) item("Nest into one clip") { act.nest(idx.first(), idx.size); pick.clear() }
                    HorizontalDivider(color = Pro.Fg.copy(alpha = 0.1f))
                    item("Delete selected", Pro.Red) { act.deleteMany(idx); pick.clear() }
                }
            }
        }
    }
}
