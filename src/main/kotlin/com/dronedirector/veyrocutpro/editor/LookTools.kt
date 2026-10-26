package com.dronedirector.veyrocutpro.editor

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.hintedScroll

/** Picks one of your imported .cube LUTs for a grade; import / delete LUTs; strength; input or look. */
@Composable
fun LutSection(g: ProGrade, onChange: (ProGrade) -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<UserLut?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val l = LutLibrary.import(context, uri)
                onChange(g.copy(lut = l.id, lutAmount = 1f))
            } catch (t: Throwable) { error = t.message ?: "That file isn't a .cube LUT" }
        }
    }
    SectionTitle("MY LUTS (.CUBE)") { onChange(g.copy(lut = null, lutAmount = 1f, lutInput = false)) }
    Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PillChip("None", g.lut.isNullOrEmpty()) { onChange(g.copy(lut = null)) }
        LutLibrary.luts.forEach { l ->
            PillChip(l.name, g.lut == l.id, onLongClick = { confirmDelete = l }) { onChange(g.copy(lut = l.id, lutAmount = if (g.lutAmount <= 0f) 1f else g.lutAmount)) }
        }
        PillChip("+ IMPORT", false, accentText = true) { importer.launch(arrayOf("*/*")) }
    }
    if (!g.lut.isNullOrEmpty()) {
        EdSlider("Strength", g.lutAmount, 0f..1f, 1f, ::pct) { onChange(g.copy(lutAmount = it)) }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use as input conversion", color = Color.White, fontSize = 13.sp)
                Text("On: the LUT converts log footage (e.g. DJI D-Log → Rec.709) before your grade. Off: it's a creative look on top.", color = Pro.Dim, fontSize = 11.sp)
            }
            Switch(g.lutInput, { onChange(g.copy(lutInput = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
        }
    }
    Text("Import 3D .cube LUTs from your phone, Drive or SD card — they're kept in the app for photos, videos and the Capture monitor. Hold a LUT to delete it.", color = Pro.Dim, fontSize = 11.sp)
    error?.let { Text(it, color = Pro.Red, fontSize = 11.sp) }
    SectionTitle("EXPORT LOOK AS LUT")
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    PillChip(if (exporting) "SAVING…" else "EXPORT THIS LOOK AS .CUBE", false, accentText = true) {
        if (exporting) return@PillChip
        if (g.isIdentity) { exportMsg = "Make a look first — the grade is still neutral."; return@PillChip }
        exporting = true
        val name = "AlcorCut look " + java.text.SimpleDateFormat("d MMM HH.mm", java.util.Locale.getDefault()).format(java.util.Date())
        Thread {
            val where = LutLibrary.exportLook(context, g, name)
            exportMsg = if (where != null) "Saved to $where — and added to My LUTs." else "Couldn't save the LUT"
            exporting = false
        }.start()
    }
    Text("Saves the colour of this grade (tone, curves, colour, HSL, grading, LUT) as a standard 33-point .cube LUT for other photos, videos, the Capture monitor or other editing software. Sharpening, vignette and other effects that depend on the picture aren't part of a LUT.", color = Pro.Dim, fontSize = 11.sp)
    exportMsg?.let { Text(it, color = Pro.Amber, fontSize = 11.sp) }
    confirmDelete?.let { l ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null }, containerColor = Pro.PanelSolid,
            title = { Text("Delete LUT?", color = Color.White) },
            text = { Text("Remove \"${l.name}\" from the app? Edits that use it will no longer show it.", color = Pro.Dim) },
            confirmButton = { TextButton(onClick = { LutLibrary.delete(l.id); if (g.lut == l.id) onChange(g.copy(lut = null)); confirmDelete = null }) { Text("Yes", color = Pro.Amber) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("No", color = Color.White) } },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PillChip(text: String, selected: Boolean, accentText: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Text(
        text, color = if (selected) Color.Black else if (accentText) Pro.Amber else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Pro.Amber else Color.White.copy(alpha = 0.08f))
            .then(if (accentText) Modifier.border(1.dp, Pro.Amber.copy(alpha = 0.6f), RoundedCornerShape(50)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** A look you saved: colour grade + detail, usable in both editors. */
data class UserPreset(val id: String, val name: String, val grade: ProGrade, val detail: Detail)

/** Your own presets, kept on the phone and shared by the photo and video editors. */
object UserPresets {
    val list = mutableStateListOf<UserPreset>()
    private var app: Context? = null
    private val gson = com.google.gson.Gson()

    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        val json = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getString("user_presets", null) ?: return
        try {
            val arr = gson.fromJson(json, Array<UserPreset>::class.java)
            list.clear()
            // Fill anything missing in presets saved by older versions with defaults.
            arr.filterNotNull().forEach { p ->
                val g = p.grade ?: ProGrade(); val d = p.detail ?: Detail()
                list += UserPreset(p.id, p.name, g.copy(hsl = g.hsl ?: List(8) { HslBand() }), d)
            }
        } catch (_: Throwable) {}
    }

    private fun persist() {
        app?.getSharedPreferences("procam", Context.MODE_PRIVATE)?.edit()?.putString("user_presets", gson.toJson(list.toList()))?.apply()
    }

    fun add(name: String, grade: ProGrade, detail: Detail) {
        list += UserPreset("p_" + System.currentTimeMillis(), name, grade, detail.copy(aiDenoise = 0f))
        persist()
    }

    fun delete(id: String) { list.removeAll { it.id == id }; persist() }
}

/** "My presets": tap to apply, hold to delete, save the current look. */
@Composable
fun UserPresetSection(current: () -> Pair<ProGrade, Detail>, onApply: (UserPreset) -> Unit) {
    var naming by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<UserPreset?>(null) }
    SectionTitle("MY PRESETS")
    Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        UserPresets.list.forEach { p -> PillChip(p.name, false, onLongClick = { confirm = p }) { onApply(p) } }
        PillChip("+ SAVE CURRENT LOOK", false, accentText = true) { naming = true }
    }
    Text(
        if (UserPresets.list.isEmpty()) "Save your grade (light, colour, curve, HSL, grading, detail, LUT) as a preset — it works in the photo and the video editor."
        else "Tap a preset to apply it, hold it to delete it. Presets work in both editors.",
        color = Pro.Dim, fontSize = 11.sp,
    )
    if (naming) ProjectNameDialog(
        "My look ${UserPresets.list.size + 1}",
        onSave = { name -> val (g, d) = current(); UserPresets.add(name, g, d); naming = false },
        onDismiss = { naming = false },
        title = "Save preset", info = "Saves the current colour grade and detail settings (not crop or watermark).", fieldLabel = "Preset name",
    )
    confirm?.let { p ->
        AlertDialog(
            onDismissRequest = { confirm = null }, containerColor = Pro.PanelSolid,
            title = { Text("Delete preset?", color = Color.White) },
            text = { Text("Delete \"${p.name}\"?", color = Pro.Dim) },
            confirmButton = { TextButton(onClick = { UserPresets.delete(p.id); confirm = null }) { Text("Yes", color = Pro.Amber) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("No", color = Color.White) } },
        )
    }
}
