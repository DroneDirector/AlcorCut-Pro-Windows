package com.dronedirector.veyrocutpro.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Backup & restore: every setting, preset, LUT, watermark, sound and saved project in one
 * .vcbackup file (a zip) you can keep anywhere — handy for a new phone or a reinstall. Your photos
 * and videos are not inside (they stay in your gallery); projects point at them.
 */
object Backup {
    /** Folders in the app's storage that are rebuilt by themselves, so not backed up. */
    private val SKIP = setOf("sound_library", "nested", "no_backup")

    fun export(context: Context, out: Uri): Boolean = try {
        context.contentResolver.openOutputStream(out)?.use { os ->
            ZipOutputStream(os.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("prefs.json"))
                zip.write(prefsJson(context).toString().toByteArray())
                zip.closeEntry()
                val root = context.filesDir
                root.walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = f.relativeTo(root).invariantSeparatorsPath
                    if (rel.substringBefore('/') in SKIP) return@forEach
                    zip.putNextEntry(ZipEntry("files/$rel"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("info.json"))
                zip.write(JSONObject().put("app", "AlcorCut Pro").put("version", 1).put("time", System.currentTimeMillis()).toString().toByteArray())
                zip.closeEntry()
            }
        } != null
    } catch (_: Throwable) { false }

    /**
     * The compact settings-only version (for "Keep settings after uninstall"): settings plus the app's
     * small files (presets, LUTs, watermarks…), but none of the big downloaded AI models.
     */
    fun exportSettings(context: Context, os: java.io.OutputStream) {
        ZipOutputStream(os.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("prefs.json"))
            zip.write(prefsJson(context).toString().toByteArray())
            zip.closeEntry()
            val root = context.filesDir
            var total = 0L
            root.walkTopDown().filter { it.isFile && it.length() < 2_000_000 }.forEach { f ->
                val rel = f.relativeTo(root).invariantSeparatorsPath
                val top = rel.substringBefore('/')
                if (top in SKIP || top == "models" || top == "whisper" || top == "stab" || total > 20_000_000) return@forEach
                // Catalogs: their settings and edits come along; photo / clip copies, AI results and caches don't.
                if ((top == "catalogs" || top == "video_catalogs") && listOf("/ai/", "/photos/", "/media/", "/proxies/", "/stab/", "/waves/", "/rendercache/", "/cloudai/").any { rel.contains(it) }) return@forEach
                total += f.length()
                zip.putNextEntry(ZipEntry("files/$rel"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("info.json"))
            zip.write(JSONObject().put("app", "AlcorCut Pro").put("version", 1).put("kind", "settings").put("time", System.currentTimeMillis()).toString().toByteArray())
            zip.closeEntry()
        }
    }

    /** A cheap fingerprint of the settings, to skip saving when nothing changed. */
    fun settingsFingerprint(context: Context): String {
        val sb = StringBuilder(prefsJson(context).toString())
        context.filesDir.walkTopDown().filter { it.isFile && it.length() < 2_000_000 }.forEach { sb.append(it.path).append(it.length()).append(it.lastModified()) }
        return sb.toString().hashCode().toString()
    }

    /** Restores a backup; the app then restarts. Returns false if the file isn't a AlcorCut Pro backup. */
    fun restore(context: Context, src: Uri): Boolean {
        val root = context.filesDir.canonicalFile
        var prefs: String? = null
        var ok = false
        try {
            context.contentResolver.openInputStream(src)?.use { ins ->
                ZipInputStream(ins.buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        when {
                            e.name == "prefs.json" -> prefs = zip.readBytes().toString(Charsets.UTF_8)
                            e.name == "info.json" -> ok = zip.readBytes().toString(Charsets.UTF_8).let { t -> t.contains("AlcorCut Pro") || t.contains("VeyroCut Pro") }
                            e.name.startsWith("files/") && !e.isDirectory -> {
                                val target = File(root, e.name.removePrefix("files/")).canonicalFile
                                // Never write outside the app's own folder.
                                if (!target.path.startsWith(root.path + File.separator)) continue
                                target.parentFile?.mkdirs()
                                target.outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                }
            } ?: return false
        } catch (_: Throwable) { return false }
        if (!ok) return false
        prefs?.let { restorePrefs(context, JSONObject(it)) }
        return true
    }

    /** Opens the app again so everything is loaded from the restored files. */
    fun restart(context: Context) {
        val i = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(i)
        Runtime.getRuntime().exit(0)
    }

    private fun prefsJson(context: Context): JSONObject {
        val out = JSONObject()
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        dir.listFiles { f -> f.name.endsWith(".xml") && !f.name.contains("secure") }.orEmpty().forEach { f ->
            val name = f.name.removeSuffix(".xml")
            val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            val o = JSONObject()
            for ((k, v) in all) {
                val (t, value) = when (v) {
                    is Boolean -> "b" to v
                    is Int -> "i" to v
                    is Long -> "l" to v
                    is Float -> "f" to v.toDouble()
                    is String -> "s" to v
                    is Set<*> -> "set" to JSONArray(v.map { it.toString() })
                    else -> continue
                }
                o.put(k, JSONObject().put("t", t).put("v", value))
            }
            out.put(name, o)
        }
        return out
    }

    private fun restorePrefs(context: Context, all: JSONObject) {
        for (name in all.keys()) {
            val o = all.getJSONObject(name)
            val ed = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for (k in o.keys()) {
                val e = o.getJSONObject(k)
                when (e.getString("t")) {
                    "b" -> ed.putBoolean(k, e.getBoolean("v"))
                    "i" -> ed.putInt(k, e.getInt("v"))
                    "l" -> ed.putLong(k, e.getLong("v"))
                    "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                    "s" -> ed.putString(k, e.getString("v"))
                    "set" -> { val a = e.getJSONArray("v"); ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
                }
            }
            ed.commit()
        }
    }
}

/** BACKUP & RESTORE rows for the settings screens. */
@androidx.compose.runtime.Composable
fun BackupSection() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var askRestore by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Uri?>(null) }
    fun toast(s: String) = android.widget.Toast.makeText(ctx, com.dronedirector.veyrocutpro.ui.I18n.tr(s), android.widget.Toast.LENGTH_LONG).show()
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            busy = "Saving backup…"
            scope.launch {
                val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Backup.export(ctx, uri) }
                busy = null
                toast(if (ok) "Backup saved" else "Couldn't save the backup")
            }
        }
    }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) askRestore = uri
    }
    askRestore?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askRestore = null },
            containerColor = panelHere(),
            title = { com.dronedirector.veyrocutpro.ui.Text("Restore this backup?", color = androidx.compose.ui.graphics.Color.White) },
            text = { com.dronedirector.veyrocutpro.ui.Text("Your current settings, presets, LUTs, sounds and projects are replaced by the ones in the backup, then the app restarts. Photos and videos are not touched.", color = Pro.Dim) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    askRestore = null
                    busy = "Restoring…"
                    scope.launch {
                        val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Backup.restore(ctx, uri) }
                        busy = null
                        if (ok) { toast("Backup restored — restarting"); Backup.restart(ctx) } else toast("That isn't a AlcorCut Pro backup")
                    }
                }) { com.dronedirector.veyrocutpro.ui.Text("Restore", color = Pro.Amber) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { askRestore = null }) { com.dronedirector.veyrocutpro.ui.Text("Cancel", color = Pro.Dim) } },
        )
    }
    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(vertical = 4.dp)) {
        com.dronedirector.veyrocutpro.ui.Text("BACKUP & RESTORE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp))
        androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.padding(vertical = 6.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            val b = @androidx.compose.runtime.Composable { label: String, onClick: () -> Unit ->
                com.dronedirector.veyrocutpro.ui.Text(
                    label, color = androidx.compose.ui.graphics.Color.Black, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = androidx.compose.ui.Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Pro.Amber)
                        .clickable(enabled = busy == null, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            b("SAVE BACKUP") {
                val name = "AlcorCutPro_backup_" + java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).format(java.util.Date()) + ".vcbackup"
                save.launch(name)
            }
            b("RESTORE") { open.launch(arrayOf("*/*")) }
        }
        com.dronedirector.veyrocutpro.ui.Text(
            busy ?: "One file with all settings, presets, LUTs, watermarks, your sounds and saved projects — for a new phone or after reinstalling. Photos and videos stay in your gallery.",
            color = if (busy != null) Pro.Amber else Pro.Dim, fontSize = 12.sp,
        )
        KeepSettingsRow()
    }
}
