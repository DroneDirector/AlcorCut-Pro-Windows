package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Watched folders: folders you choose (a camera's upload folder, a synced NAS folder, Downloads…) are
 * checked when the app starts and every minute while a library or the video editor is open. New photos and
 * videos in them are copied in (Pictures / Movies → AlcorCut Pro Import), with a metadata preset and, for
 * photos, a develop preset. New videos also go into the video editor's media strip when it's open.
 */
object WatchFolders {
    /** [kind] 0 photos and videos, 1 photos, 2 videos. */
    data class Folder(val tree: String, val name: String, val kind: Int = 0, val meta: String? = null, val develop: String? = null, val seen: List<String> = emptyList())

    val folders = mutableStateListOf<Folder>()
    var last by mutableStateOf<String?>(null)
    /** Set by the video editor while it's open: new videos go into its media strip. */
    @Volatile var onVideos: ((List<Uri>) -> Unit)? = null
    private var loaded = false
    private val lock = Any()

    private fun file(c: Context) = java.io.File(c.filesDir, "watched_folders.json")
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(c); if (!f.exists()) return
            val arr = ProjectStore.gson.fromJson(f.readText(), Array<Folder>::class.java) ?: return
            folders.clear(); folders.addAll(arr.map { it.copy(seen = it.seen ?: emptyList()) })
        } catch (_: Throwable) {}
    }
    fun save(c: Context) { try { file(c).writeText(ProjectStore.gson.toJson(folders.toList())) } catch (_: Throwable) {} }

    fun label(c: Context, tree: Uri): String = try {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        c.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Throwable) { null } ?: (tree.lastPathSegment ?: "folder")

    /** Adds a folder; what's in it now is imported too only when [importExisting]. */
    fun add(c: Context, tree: Uri, kind: Int, importExisting: Boolean) {
        load(c)
        if (folders.any { it.tree == tree.toString() }) return
        val seen = if (importExisting) emptyList() else ProImport.scanTree(c, tree).map { it.key }
        folders.add(Folder(tree.toString(), label(c, tree), kind, seen = seen))
        save(c)
    }

    /** Checks every folder once (off the main thread); returns how many files came in. */
    fun check(c: Context): Int = synchronized(lock) {
        load(c)
        if (folders.isEmpty()) return 0
        MetaPresets.load(c)
        var total = 0
        val videos = ArrayList<Uri>()
        for ((i, f) in folders.toList().withIndex()) {
            val tree = Uri.parse(f.tree)
            val items = try { ProImport.scanTree(c, tree) } catch (_: Throwable) { continue }
            val seen = f.seen.toHashSet()
            val new = items.filter { it.key !in seen && when (f.kind) { 1 -> !it.video; 2 -> it.video; else -> true } }
            if (new.isEmpty()) continue
            val meta = MetaPresets.list.firstOrNull { it.name == f.meta }
            val res = ProImport.run(c, new.sortedBy { it.date }, ProImport.Options(0, "", true, null, meta, f.develop), open = { it.doc?.let { d -> c.contentResolver.openInputStream(d) } }, progress = { _, _, _ -> }, stop = { false })
            total += res.size
            videos += res.filter { it.second }.map { it.first }
            // Everything listed now counts as seen (what failed is tried again next time only if it changed).
            folders[i] = f.copy(seen = (f.seen + new.map { it.key }).distinct().takeLast(20000))
        }
        save(c)
        if (total > 0) {
            last = "$total new file(s) imported from watched folders"
            com.dronedirector.veyrocutpro.ui.AppLog.i("Watched folders: $total new file(s) imported")
            if (videos.isNotEmpty()) onVideos?.let { cb -> android.os.Handler(android.os.Looper.getMainLooper()).post { cb(videos) } }
        }
        total
    }
}

/** The list of watched folders: add, set what they bring in, remove, check now. */
@Composable
fun WatchFoldersDialog(onDismiss: () -> Unit, onImported: (Int) -> Unit = {}) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    WatchFolders.load(c); MetaPresets.load(c); UserPresets.init(c)
    var addKind by remember { mutableStateOf(0) }
    var addExisting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        try { c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
        busy = "Adding the folder…"
        scope.launch {
            withContext(Dispatchers.IO) { WatchFolders.add(c, u, addKind, addExisting) }
            val n = if (addExisting) withContext(Dispatchers.IO) { WatchFolders.check(c) } else 0
            busy = null; if (n > 0) onImported(n)
        }
    }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Ed.Panel,
        title = { Text("Watched folders", color = Pro.Fg) },
        text = {
            Column(Modifier.hintedScroll(horizontal = false)) {
                Text("New photos and videos that land in these folders (a camera upload folder, a synced NAS folder…) are copied in by themselves — when the app starts and every minute while a library or the video editor is open.", color = Pro.Dim, fontSize = 12.sp)
                WatchFolders.folders.toList().forEachIndexed { i, f ->
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(8.dp)) {
                        Text("📁 ${f.name}", color = Pro.Fg, fontSize = 14.sp)
                        Row(Modifier.hintedScroll(horizontal = true).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Photos & videos", "Photos", "Videos").forEachIndexed { k, n -> PillChip(n, f.kind == k) { WatchFolders.folders[i] = f.copy(kind = k); WatchFolders.save(c) } }
                        }
                        Row(Modifier.hintedScroll(horizontal = true).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Metadata", color = Pro.Dim, fontSize = 11.sp)
                            PillChip("None", f.meta == null) { WatchFolders.folders[i] = f.copy(meta = null); WatchFolders.save(c) }
                            MetaPresets.list.forEach { p -> PillChip(p.name, f.meta == p.name) { WatchFolders.folders[i] = f.copy(meta = p.name); WatchFolders.save(c) } }
                        }
                        if (f.kind != 2) Row(Modifier.hintedScroll(horizontal = true).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Develop", color = Pro.Dim, fontSize = 11.sp)
                            PillChip("None", f.develop == null) { WatchFolders.folders[i] = f.copy(develop = null); WatchFolders.save(c) }
                            UserPresets.list.forEach { p -> PillChip(p.name, f.develop == p.id) { WatchFolders.folders[i] = f.copy(develop = p.id); WatchFolders.save(c) } }
                        }
                        Text("STOP WATCHING", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp),
                            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable { WatchFolders.folders.removeAt(i); WatchFolders.save(c) }.padding(4.dp))
                    }
                }
                Text("Add a folder", color = Pro.Fg, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Photos & videos", "Photos", "Videos").forEachIndexed { k, n -> PillChip(n, addKind == k) { addKind = k } }
                }
                Row(Modifier.hintedScroll(horizontal = true).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PillChip("Only new files", !addExisting) { addExisting = false }
                    PillChip("Also what's there now", addExisting) { addExisting = true }
                    PillChip("+ Choose folder…", false, accentText = true) { try { pick.launch(null) } catch (_: Throwable) {} }
                }
                (busy ?: WatchFolders.last)?.let { Text(it, color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                busy = "Checking…"
                scope.launch { val n = withContext(Dispatchers.IO) { WatchFolders.check(c) }; busy = if (n == 0) "Nothing new" else null; if (n > 0) onImported(n) }
            }) { Text("CHECK NOW", color = Pro.Amber) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("DONE", color = Pro.Dim) } },
    )
}

