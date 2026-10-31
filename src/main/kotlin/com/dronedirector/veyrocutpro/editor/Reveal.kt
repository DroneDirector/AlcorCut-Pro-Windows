package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.dronedirector.veyrocutpro.ui.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where a media file is on the phone, in words, and a way to open its folder. Own code. */
object Reveal {
    class Place(val name: String, val folder: String, val size: Long, val modified: Long, val folderUri: Uri?, val note: String?)

    fun find(c: Context, u: Uri): Place {
        var name = u.lastPathSegment?.substringAfterLast('/') ?: "?"
        var size = 0L; var modified = 0L
        var folder = "?"; var folderUri: Uri? = null; var note: String? = null
        try {
            c.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cur ->
                if (cur.moveToFirst()) { cur.getString(0)?.let { name = it }; if (!cur.isNull(1)) size = cur.getLong(1) }
            }
        } catch (_: Throwable) {}
        when {
            u.scheme == "file" -> {
                val f = java.io.File(u.path ?: "")
                name = f.name; size = f.length(); modified = f.lastModified()
                val inApp = f.absolutePath.startsWith(c.filesDir.absolutePath) || f.absolutePath.startsWith(c.cacheDir.absolutePath)
                folder = if (inApp) "Inside AlcorCut Pro" else f.parent ?: "?"
                note = when {
                    f.absolutePath.contains("/nas/") -> "A copy made when it was added from the NAS"
                    f.absolutePath.contains("/team/") -> "A copy of the team project's media"
                    f.absolutePath.contains("/generated/") -> "Made by AlcorCut Pro (generator, gap or offline stand-in)"
                    inApp -> "Kept in the app's own storage"
                    else -> null
                }
            }
            DocumentsContract.isDocumentUri(c, u) -> {
                val id = DocumentsContract.getDocumentId(u)
                val vol = id.substringBefore(':', "")
                val path = id.substringAfter(':', id)
                folder = volumeName(vol) + "/" + path.substringBeforeLast('/', "")
                if (u.authority == "com.android.externalstorage.documents" && path.contains('/'))
                    folderUri = DocumentsContract.buildDocumentUri(u.authority, vol + ":" + path.substringBeforeLast('/'))
                try {
                    c.contentResolver.query(u, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { cur -> if (cur.moveToFirst() && !cur.isNull(0)) modified = cur.getLong(0) }
                } catch (_: Throwable) {}
                if (u.authority?.contains("media") == true) note = "From the phone's gallery"
            }
            u.authority == MediaStore.AUTHORITY -> {
                try {
                    val cols = if (android.os.Build.VERSION.SDK_INT >= 29) arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.VOLUME_NAME, MediaStore.MediaColumns.DATE_MODIFIED)
                    else arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.DATE_MODIFIED)
                    c.contentResolver.query(u, cols, null, null, null)?.use { cur ->
                        if (cur.moveToFirst()) {
                            if (android.os.Build.VERSION.SDK_INT >= 29) {
                                val rel = cur.getString(0)?.trimEnd('/') ?: ""
                                val vol = cur.getString(1) ?: "external_primary"
                                folder = (if (vol == "external_primary") "Internal storage" else "SD card / USB ($vol)") + "/" + rel
                                if (vol == "external_primary" && rel.isNotEmpty()) folderUri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$rel")
                            } else folder = cur.getString(0)?.substringBeforeLast('/') ?: "?"
                            modified = cur.getLong(2) * 1000
                        }
                    }
                } catch (_: Throwable) {}
                note = "In the phone's gallery"
            }
            else -> { folder = u.authority ?: "?"; note = "Shared by another app — its own place, not a folder" }
        }
        return Place(name, folder, size, modified, folderUri, note)
    }

    private fun volumeName(v: String) = when {
        v == "primary" -> "Internal storage"
        v.isEmpty() -> "Phone"
        else -> "SD card / USB ($v)"
    }

    /** Opens the folder in the phone's Files app, when it can. */
    fun openFolder(c: Context, folder: Uri): Boolean = try {
        c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        true
    } catch (_: Throwable) {
        try {
            c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(folder, "resource/folder").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (_: Throwable) { false }
    }
}

/** Tools bar → "Reveal in Project": the selected clip in the media strip, or where its file is on the phone. */
@Composable
fun RevealMenu(clip: Clip?, onRevealLibrary: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var showFile by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Pro.Fg.copy(alpha = 0.08f)).clickable { open = true }.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Reveal in Project", color = Color.White, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1)
            Icon(Icons.Filled.ArrowDropDown, null, tint = Pro.Amber, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Pro.PanelSolid) {
            DropdownMenuItem(text = { Text("Reveal in library", color = if (clip != null) Color.White else Pro.Dim) }, enabled = clip != null, onClick = { open = false; onRevealLibrary() })
            DropdownMenuItem(text = { Text("Reveal on device", color = if (clip != null) Color.White else Pro.Dim) }, enabled = clip != null, onClick = { open = false; showFile = true })
            if (clip == null) Text("Select a clip on the timeline first", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        }
    }
    if (showFile && clip != null) RevealFileDialog(clip) { showFile = false }
}

@Composable
internal fun RevealFileDialog(clip: Clip, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val target = clip.offlineOf?.let { Uri.parse(it) } ?: clip.uri
    val place by produceState<Reveal.Place?>(null, target) { value = withContext(Dispatchers.IO) { try { Reveal.find(context, target) } catch (_: Throwable) { null } } }
    var note by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pro.PanelSolid,
        title = { Text("Where the file is", color = Color.White) },
        text = {
            val p = place
            if (p == null) Text("Looking…", color = Pro.Dim)
            else Column {
                Text(p.name, color = Color.White, fontSize = 15.sp)
                Text("Folder", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 10.dp))
                Text(p.folder, color = Color.White, fontSize = 13.sp)
                p.note?.let { Text(it, color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                if (clip.offline == true) Text("This clip is offline — this is the file it stood for.", color = Pro.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                Text(
                    (if (p.size > 0) String.format(java.util.Locale.US, "%.1f MB", p.size / 1048576f) else "") +
                        (if (p.modified > 0) "  ·  " + java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(p.modified)) else ""),
                    color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )
                note?.let { Text(it, color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            val p = place
            Row {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("path", (p?.folder ?: "") + "/" + (p?.name ?: "")))
                    note = "Copied"
                }) { Text("Copy path", color = Pro.Amber) }
                if (p?.folderUri != null) TextButton(onClick = { if (!Reveal.openFolder(context, p.folderUri)) note = "The Files app couldn't open that folder — use Copy path" }) { Text("Open folder", color = Pro.Amber) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = Color.White) } },
    )
}
