package com.dronedirector.veyrocutpro.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import java.io.File

/**
 * "Keep settings after uninstall": when on, the app saves a compact settings file in
 * Documents/AlcorCutPro/Settings whenever you leave it (only if something changed). That folder
 * stays when the app is uninstalled, so after reinstalling, "Restore my settings" brings it back.
 * Android lets a reinstalled app read that file only once you pick it, so restoring is one tap.
 * The fal.ai key is never in the file (it's encrypted for this installation only).
 */
object KeepSettings {
    private const val ON = "keep_settings"
    private const val URI = "keep_settings_uri"
    private const val PRINT = "keep_settings_print"
    const val FILE = "AlcorCutPro-settings.vcbackup"
    const val FOLDER = "Documents/AlcorCutPro/Settings"

    private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)
    fun isOn(c: Context) = prefs(c).getBoolean(ON, false)

    fun setOn(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(ON, on).remove(PRINT).apply()
        if (on) saveIfOn(c)
    }

    /** Saves in the background when on and something changed since the last save. */
    fun saveIfOn(c: Context) {
        val app = c.applicationContext
        if (!isOn(app)) return
        Thread {
            try {
                val print = Backup.settingsFingerprint(app)
                if (prefs(app).getString(PRINT, null) == print) return@Thread
                if (write(app)) prefs(app).edit().putString(PRINT, print).apply()
            } catch (_: Throwable) {}
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    private fun write(c: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            // Overwrite our own file; if it's gone (or belongs to an earlier install), make a new one.
            prefs(c).getString(URI, null)?.let { s ->
                try { c.contentResolver.openOutputStream(Uri.parse(s), "wt")?.use { Backup.exportSettings(c, it) }; return true } catch (_: Throwable) {}
            }
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER)
            }
            val u = c.contentResolver.insert(MediaStore.Files.getContentUri("external"), v) ?: return false
            c.contentResolver.openOutputStream(u)?.use { Backup.exportSettings(c, it) } ?: return false
            prefs(c).edit().putString(URI, u.toString()).apply()
            return true
        }
        @Suppress("DEPRECATION")
        val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "AlcorCutPro/Settings").apply { mkdirs() }
        File(dir, FILE).outputStream().use { Backup.exportSettings(c, it) }
        return true
    }

    /** Where the file picker should start: the settings folder. */
    val folderHint: Uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments%2FAlcorCutPro%2FSettings")
}

/** Gear → backup section: the "Keep settings after uninstall" switch. */
@Composable
fun KeepSettingsRow() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(KeepSettings.isOn(ctx)) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Keep settings after uninstall", color = Color.White, fontSize = 15.sp)
            Text("Saves your settings, presets and LUTs to Documents/AlcorCutPro/Settings whenever you leave the app. That folder stays if the app is uninstalled — after reinstalling, tap Restore my settings on the first screen and pick the file. Your fal.ai key isn't included.", color = Pro.Dim, fontSize = 12.sp)
        }
        Switch(on, { on = it; KeepSettings.setOn(ctx, it) }, colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
    }
}

/** First screen after (re)installing: restore settings saved by an earlier install. */
@Composable
fun RestorePreviousSettings() {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(object : androidx.activity.result.contract.ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: Context, input: Array<String>) = super.createIntent(context, input).apply {
            if (Build.VERSION.SDK_INT >= 26) putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, KeepSettings.folderHint)
        }
    }) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        msg = "Restoring…"
        Thread {
            val ok = Backup.restore(ctx, u)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (ok) { KeepSettings.setOn(ctx, true); android.widget.Toast.makeText(ctx, I18n.tr("Settings restored — restarting"), android.widget.Toast.LENGTH_LONG).show(); Backup.restart(ctx) }
                else msg = "That isn't a AlcorCut Pro settings file"
            }
        }.start()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 18.dp)) {
        Text("Reinstalled the app?", color = Pro.Dim, fontSize = 12.sp)
        Text("RESTORE MY SETTINGS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp),
            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(fgHere().copy(alpha = 0.06f))
                .clickable { pick.launch(arrayOf("*/*")) }.padding(horizontal = 16.dp, vertical = 9.dp))
        Text("Pick ${KeepSettings.FILE} in ${KeepSettings.FOLDER}.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        msg?.let { Text(it, color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
    }
}
