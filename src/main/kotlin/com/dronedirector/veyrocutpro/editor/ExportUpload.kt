package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll

/**
 * Upload after export: every finished photo / video is also copied to a folder you choose — Google Drive,
 * OneDrive, Dropbox, a Synology / NAS share, a USB drive or any folder Android's file picker can open.
 * Photos and videos each have their own setting.
 */
object ExportUpload {
    private fun prefs(c: Context) = c.getSharedPreferences("export_upload", Context.MODE_PRIVATE)
    fun target(c: Context, video: Boolean): Uri? = prefs(c).getString(if (video) "video" else "photo", null)?.let(Uri::parse)
    fun setTarget(c: Context, video: Boolean, u: Uri?) { prefs(c).edit().apply { if (u == null) remove(if (video) "video" else "photo") else putString(if (video) "video" else "photo", u.toString()) }.apply() }
    /** The status of the last upload (shown under the setting). */
    var last by mutableStateOf<String?>(null)

    fun label(c: Context, u: Uri): String = try {
        val doc = DocumentsContract.buildDocumentUriUsingTree(u, DocumentsContract.getTreeDocumentId(u))
        c.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Throwable) { null } ?: (u.lastPathSegment ?: "folder")

    /** Copies [file] to the chosen place in the background (nothing happens when none is set). */
    fun after(c: Context, file: Uri?, video: Boolean) {
        val t = target(c, video) ?: return
        val src = file ?: return
        val app = c.applicationContext
        Thread {
            val name = try { app.contentResolver.query(src, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } } catch (_: Throwable) { null }
                ?: src.lastPathSegment?.substringAfterLast('/') ?: "export_${System.currentTimeMillis()}"
            val mime = app.contentResolver.getType(src) ?: if (video) "video/mp4" else "image/jpeg"
            val ok = try {
                val dir = DocumentsContract.buildDocumentUriUsingTree(t, DocumentsContract.getTreeDocumentId(t))
                val dst = DocumentsContract.createDocument(app.contentResolver, dir, mime, name) ?: throw IllegalStateException("no file")
                app.contentResolver.openInputStream(src)?.use { ins -> app.contentResolver.openOutputStream(dst)?.use { os -> ins.copyTo(os, 1 shl 20) } } != null
            } catch (t2: Throwable) { com.dronedirector.veyrocutpro.ui.AppLog.w("Upload after export failed: ${t2.message}"); false }
            last = if (ok) "Uploaded $name" else "Couldn't upload $name — check the folder is still reachable"
            if (ok) com.dronedirector.veyrocutpro.ui.AppLog.i("Upload after export: $name copied")
        }.start()
    }
}

/** The setting: where finished exports are also copied to. */
@Composable
fun ExportUploadSection(video: Boolean) {
    val c = LocalContext.current
    var t by remember { mutableStateOf(ExportUpload.target(c, video)) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u != null) {
            try { c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Throwable) {}
            ExportUpload.setTarget(c, video, u); t = u
        }
    }
    SectionTitle("UPLOAD AFTER EXPORT")
    Text("Each finished ${if (video) "video" else "photo"} is also copied to a folder: Google Drive, OneDrive, Dropbox, your NAS (with its app), a USB drive — anything the file picker shows.", color = Pro.Dim, fontSize = 11.sp)
    Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PillChip(t?.let { "→ " + ExportUpload.label(c, it) } ?: "Choose a folder…", t != null, accentText = t == null) { try { pick.launch(null) } catch (_: Throwable) {} }
        if (t != null) PillChip("Off", false) { ExportUpload.setTarget(c, video, null); t = null }
    }
    ExportUpload.last?.let { Text(it, color = Pro.Amber, fontSize = 11.sp) }
}
