package com.dronedirector.veyrocutpro.editor

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro

/** Everything that can be copied from a photo or clip. Missing parts are null. */
data class CopiedSettings(
    val fromVideo: Boolean = false,
    val grade: ProGrade? = null,
    val detail: Detail? = null,
    val geometry: Geometry? = null,     // photo crop / rotate
    val reframe: Reframe? = null,       // video crop / zoom
    val stab: Stabilize? = null,
    val audio: AudioFx? = null,
    val volume: Float? = null,
    val muted: Boolean? = null,
    val speed: Float? = null,
    val watermark: Layer? = null,
)

enum class PastePart(val label: String, val video: Boolean, val photo: Boolean, val default: Boolean) {
    GRADE("Colour grade (light, colour, curves, HSL, looks)", true, true, true),
    DETAIL("Detail & effects", true, true, true),
    CROP("Crop & zoom", true, true, false),
    STAB("Stabilization", true, false, true),
    AUDIO("Audio (volume, EQ, dynamics)", true, false, true),
    SPEED("Speed", true, false, false),
    WATERMARK("Watermark", false, true, true),
}

/** The copied settings, kept between editors and app restarts. */
object EditClipboard {
    var current by mutableStateOf<CopiedSettings?>(null)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        current = try {
            context.getSharedPreferences("procam", Context.MODE_PRIVATE).getString("edit_clipboard", null)
                ?.let { ProjectStore.gson.fromJson(it, CopiedSettings::class.java) }
        } catch (_: Throwable) { null }
    }

    fun copy(context: Context, s: CopiedSettings) {
        current = s
        try {
            context.getSharedPreferences("procam", Context.MODE_PRIVATE).edit().putString("edit_clipboard", ProjectStore.gson.toJson(s)).apply()
        } catch (_: Throwable) {}
    }

    /** What the clipboard holds that a [video] / photo target can use. */
    fun available(video: Boolean): List<PastePart> {
        val c = current ?: return emptyList()
        return PastePart.entries.filter { p ->
            (if (video) p.video else p.photo) && when (p) {
                PastePart.GRADE -> c.grade != null
                PastePart.DETAIL -> c.detail != null
                PastePart.CROP -> if (video) c.reframe != null else c.geometry != null
                PastePart.STAB -> c.stab != null
                PastePart.AUDIO -> c.audio != null
                PastePart.SPEED -> c.speed != null
                PastePart.WATERMARK -> c.watermark != null
            }
        }
    }

    fun pasteOnClip(c: Clip, parts: Set<PastePart>): Clip {
        val s = current ?: return c
        var out = c
        if (PastePart.GRADE in parts) s.grade?.let { out = out.copy(grade = it) }
        if (PastePart.DETAIL in parts) s.detail?.let { out = out.copy(detail = it) }
        if (PastePart.CROP in parts) s.reframe?.let { out = out.copy(reframe = it) }
        if (PastePart.STAB in parts) s.stab?.let { out = out.copy(stab = it) }
        if (PastePart.AUDIO in parts) s.audio?.let { out = out.copy(audio = it, volume = s.volume ?: out.volume, muted = s.muted ?: out.muted) }
        if (PastePart.SPEED in parts) s.speed?.let { out = out.copy(speed = it) }
        return out
    }
}

/** COPY / PASTE row shown above an editor's controls. */
@Composable
fun CopyPasteBar(video: Boolean, onCopy: () -> Unit, onPaste: (Set<PastePart>, Boolean) -> Unit) {
    val context = LocalContext.current
    remember { EditClipboard.load(context); 0 }
    val avail = EditClipboard.available(video)
    var asking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton("COPY SETTINGS", true, Modifier.weight(1f), onCopy)
        PillButton("PASTE SETTINGS", avail.isNotEmpty(), Modifier.weight(1f)) { asking = true }
    }
    if (asking) {
        val chosen = remember { mutableStateListOf<PastePart>().apply { addAll(avail.filter { it.default }) } }
        var all by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { asking = false },
            containerColor = Pro.PanelSolid,
            title = { Text("Paste settings", color = Color.White) },
            text = {
                Column {
                    val from = if (EditClipboard.current?.fromVideo == true) "a video clip" else "a photo"
                    Text("Copied from $from. Choose what to paste:", color = Pro.Dim, fontSize = 12.sp)
                    avail.forEach { p ->
                        Row(Modifier.fillMaxWidth().clickable { if (p in chosen) chosen.remove(p) else chosen.add(p) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(p in chosen, { if (it) chosen.add(p) else chosen.remove(p) },
                                colors = CheckboxDefaults.colors(checkedColor = Pro.Amber, checkmarkColor = Color.Black))
                            Text(p.label, color = Color.White, fontSize = 14.sp)
                        }
                    }
                    if (video) Row(Modifier.fillMaxWidth().padding(top = 6.dp).clickable { all = !all }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(all, { all = it }, colors = CheckboxDefaults.colors(checkedColor = Pro.Amber, checkmarkColor = Color.Black))
                        Text("Paste on every clip in the timeline", color = Pro.Amber, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { asking = false; if (chosen.isNotEmpty()) onPaste(chosen.toSet(), all) }, enabled = chosen.isNotEmpty()) {
                    Text("Paste", color = Pro.Amber)
                }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel", color = Color.White) } },
        )
    }
}

@Composable
private fun PillButton(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label, style = Pro.Label.copy(color = if (enabled) Pro.Amber else Pro.Dim, fontSize = 11.sp),
        modifier = modifier.clip(RoundedCornerShape(50)).background(Ed.Raised).clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp).fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
