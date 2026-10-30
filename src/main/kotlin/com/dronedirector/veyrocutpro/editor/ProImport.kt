package com.dronedirector.veyrocutpro.editor

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One file found on the card / drive / camera. */
data class ImportItem(val key: String, val name: String, val size: Long, val date: Long, val video: Boolean, val doc: Uri? = null, val mtpHandle: Int? = null)

/**
 * Pro import (photo and video library → IMPORT): from an SD card, a USB drive or a camera on a USB cable.
 * Files are copied (never moved) into Pictures / Movies → AlcorCut Pro Import → the shooting day, renamed by a
 * pattern, optionally backed up to a second place, with a metadata preset (and for photos a develop preset)
 * attached. Files imported before are recognised and skipped.
 */
object ProImport {
    val PHOTO_EXT = setOf("jpg", "jpeg", "heic", "heif", "png", "dng", "cr2", "cr3", "nef", "arw", "raf", "orf", "rw2", "tif", "tiff")
    val VIDEO_EXT = setOf("mp4", "mov", "m4v", "mts", "m2ts", "3gp", "insv", "mkv")
    val RENAME = listOf("Keep the name", "Date_time", "Date_name", "Prefix + number")

    private fun prefs(c: Context) = c.getSharedPreferences("pro_import", Context.MODE_PRIVATE)
    fun seen(c: Context): Set<String> = prefs(c).getStringSet("seen", emptySet()) ?: emptySet()
    private fun markSeen(c: Context, keys: Collection<String>) { val s = seen(c).toMutableSet(); s += keys; prefs(c).edit().putStringSet("seen", s).apply() }

    fun kindOf(name: String): Boolean? = name.substringAfterLast('.', "").lowercase().let { e -> if (e in PHOTO_EXT) false else if (e in VIDEO_EXT) true else null }

    /** Every photo / video under the folder [tree] (a card, drive or any folder), newest first. */
    fun scanTree(c: Context, tree: Uri, stop: () -> Boolean = { false }): List<ImportItem> {
        val out = ArrayList<ImportItem>()
        val root = DocumentsContract.getTreeDocumentId(tree)
        val todo = ArrayDeque<String>().apply { add(root) }
        while (todo.isNotEmpty() && !stop()) {
            val id = todo.removeFirst()
            val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            try {
                c.contentResolver.query(kids, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { cur ->
                    while (cur.moveToNext()) {
                        val did = cur.getString(0); val name = cur.getString(1) ?: continue; val mime = cur.getString(2) ?: ""
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) { if (!name.startsWith(".")) todo.add(did); continue }
                        val v = kindOf(name) ?: continue
                        val size = cur.getLong(3); val date = cur.getLong(4)
                        out += ImportItem("$name|$size", name, size, date, v, doc = DocumentsContract.buildDocumentUriUsingTree(tree, did))
                    }
                }
            } catch (_: Throwable) {}
        }
        return out.sortedByDescending { it.date }
    }

    private val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    private val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)

    fun newName(it: ImportItem, pattern: Int, prefix: String, n: Int): String {
        val ext = it.name.substringAfterLast('.', "")
        val date = java.util.Date(if (it.date > 0) it.date else System.currentTimeMillis())
        val base = when (pattern) {
            1 -> stamp.format(date)
            2 -> stamp.format(date).substring(0, 8) + "_" + it.name.substringBeforeLast('.')
            3 -> (prefix.ifBlank { "IMPORT" }.replace(Regex("[^A-Za-z0-9_\\-]"), "_")) + "_" + n.toString().padStart(4, '0')
            else -> it.name.substringBeforeLast('.')
        }
        return if (ext.isEmpty()) base else "$base.$ext"
    }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"; "heic", "heif" -> "image/heif"; "png" -> "image/png"; "dng" -> "image/x-adobe-dng"; "tif", "tiff" -> "image/tiff"
        "mp4", "m4v" -> "video/mp4"; "mov" -> "video/quicktime"; "mts", "m2ts" -> "video/mp2t"; "3gp" -> "video/3gpp"; "mkv" -> "video/x-matroska"
        else -> if (kindOf(name) == true) "video/*" else "image/*"
    }

    /** A folder [name] inside [dirDoc] of the tree [tree] (made when missing). */
    private fun subDir(c: Context, tree: Uri, dirDoc: String, name: String): String? = try {
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirDoc)
        var found: String? = null
        c.contentResolver.query(kids, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cur ->
            while (cur.moveToNext()) if (cur.getString(1) == name) { found = cur.getString(0); break }
        }
        found ?: DocumentsContract.createDocument(c.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, dirDoc), DocumentsContract.Document.MIME_TYPE_DIR, name)?.let { DocumentsContract.getDocumentId(it) }
    } catch (_: Throwable) { null }

    class Options(val pattern: Int, val prefix: String, val byDay: Boolean, val backup: Uri?, val meta: MetaPreset?, val develop: String?)

    /**
     * Copies [items] in; [open] gives each file's data. Returns the new files (MediaStore uris).
     * [progress] gets (done, total, file name).
     */
    fun run(c: Context, items: List<ImportItem>, o: Options, open: (ImportItem) -> java.io.InputStream?, progress: (Int, Int, String) -> Unit, stop: () -> Boolean): List<Pair<Uri, Boolean>> {
        val out = ArrayList<Pair<Uri, Boolean>>()
        val done = ArrayList<String>()
        val backupRoot = o.backup?.let { DocumentsContract.getTreeDocumentId(it) }
        items.forEachIndexed { i, it ->
            if (stop()) return@forEachIndexed
            progress(i, items.size, it.name)
            val name = newName(it, o.pattern, o.prefix, i + 1)
            val folder = (if (it.video) "Movies" else "Pictures") + "/AlcorCut Pro Import" + (if (o.byDay) "/" + day.format(java.util.Date(if (it.date > 0) it.date else System.currentTimeMillis())) else "")
            val col = if (it.video) (if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                else (if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(it.name))
                if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.MediaColumns.RELATIVE_PATH, folder); put(MediaStore.MediaColumns.IS_PENDING, 1) }
                if (it.date > 0 && Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.DATE_TAKEN, it.date)
            }
            val dst = try { c.contentResolver.insert(col, cv) } catch (_: Throwable) { null } ?: return@forEachIndexed
            val ok = try {
                open(it)?.use { ins -> c.contentResolver.openOutputStream(dst)?.use { os -> ins.copyTo(os, 1 shl 20) } } != null
            } catch (_: Throwable) { false }
            if (!ok) { try { c.contentResolver.delete(dst, null, null) } catch (_: Throwable) {}; return@forEachIndexed }
            if (Build.VERSION.SDK_INT >= 29) try { c.contentResolver.update(dst, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) } catch (_: Throwable) {}
            // Backup copy: the same name, in the same day folder, at the second place.
            if (o.backup != null && backupRoot != null) try {
                var dir = subDir(c, o.backup, backupRoot, "AlcorCut Pro Import")
                if (dir != null && o.byDay) dir = subDir(c, o.backup, dir, day.format(java.util.Date(if (it.date > 0) it.date else System.currentTimeMillis())))
                if (dir != null) {
                    val b = DocumentsContract.createDocument(c.contentResolver, DocumentsContract.buildDocumentUriUsingTree(o.backup, dir), mimeOf(it.name), name)
                    if (b != null) c.contentResolver.openInputStream(dst)?.use { ins -> c.contentResolver.openOutputStream(b)?.use { os -> ins.copyTo(os, 1 shl 20) } }
                }
            } catch (_: Throwable) { com.dronedirector.veyrocutpro.ui.AppLog.w("Import: backup copy of ${it.name} failed") }
            // Details for the library.
            val k = dst.toString()
            if (it.video) { if (o.meta != null) VideoLibrary.update(c, listOf(k)) { m -> m.copy(meta = o.meta.asEdit(), keywords = (m.keywords + o.meta.keywordList).distinct()) } }
            else if (o.meta != null || o.develop != null) LibraryStore.update(c, listOf(k)) { m -> m.copy(meta = o.meta?.asEdit() ?: m.meta, keywords = (m.keywords + (o.meta?.keywordList ?: emptyList())).distinct(), develop = o.develop ?: m.develop) }
            out += dst to it.video
            done += it.key
        }
        markSeen(c, done)
        progress(items.size, items.size, "")
        com.dronedirector.veyrocutpro.ui.AppLog.i("Import: ${out.size} of ${items.size} file(s) copied" + (if (o.backup != null) " (with backup)" else ""))
        return out
    }
}

/**
 * The import screen. [video] opens it with videos picked (from the video editor), else photos.
 * [onDone] gets the imported files (uri, is video).
 */
@Composable
fun ProImportScreen(video: Boolean, onClose: () -> Unit, onDone: (List<Pair<Uri, Boolean>>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    androidx.activity.compose.BackHandler(onBack = onClose)
    MetaPresets.load(context); UserPresets.init(context)
    var items by remember { mutableStateOf<List<ImportItem>>(emptyList()) }
    var scanning by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<Uri?>(null) }
    var mtp by remember { mutableStateOf<MtpSource?>(null) }
    val picked = remember { mutableStateListOf<String>() }
    var kinds by remember { mutableStateOf(if (video) 1 else 0) }   // 0 photos, 1 videos, 2 both
    var newOnly by remember { mutableStateOf(true) }
    var pattern by remember { mutableStateOf(0) }
    var prefix by remember { mutableStateOf("") }
    var byDay by remember { mutableStateOf(true) }
    var backup by remember { mutableStateOf<Uri?>(null) }
    var meta by remember { mutableStateOf<MetaPreset?>(null) }
    var develop by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var stop by remember { mutableStateOf(false) }
    val seen = remember(items) { ProImport.seen(context) }
    fun pick(list: List<ImportItem>) { picked.clear(); picked.addAll(list.filter { !newOnly || it.key !in seen }.map { it.key }) }
    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        try { context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
        source = u; mtp?.close(); mtp = null; scanning = "Looking for photos and videos…"
        scope.launch { val list = withContext(Dispatchers.IO) { ProImport.scanTree(context, u) }; items = list; scanning = null; pick(list) }
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u != null) { try { context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Throwable) {}; backup = u }
    }
    val shown = items.filter { when (kinds) { 0 -> !it.video; 1 -> it.video; else -> true } }
    Column(Modifier.fillMaxSize().background(Ed.Bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Import", color = Color.White, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text("CLOSE", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
        }
        Text("Copies photos and videos in from a memory card, a USB drive or a camera — the originals stay where they are.", color = Pro.Dim, fontSize = 12.sp)
        Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PillChip("SD card / USB drive / folder…", source != null && mtp == null, accentText = true) { try { openTree.launch(null) } catch (_: Throwable) {} }
            PillChip("Camera (USB cable)", mtp != null, accentText = true) {
                scanning = "Connecting to the camera…"
                MtpSource.connect(context) { s, err ->
                    if (s == null) { scanning = null; busy = err ?: "No camera found — connect it with its USB cable and set it to PTP / MTP (or 'PC connection')"; return@connect }
                    mtp?.close(); mtp = s; source = null; scanning = "Reading the camera…"
                    scope.launch { val list = withContext(Dispatchers.IO) { s.scan() }; items = list; scanning = null; pick(list) }
                }
            }
        }
        scanning?.let { Text(it, color = Pro.Amber, fontSize = 12.sp) }
        busy?.let { Text(it, color = Pro.Amber, fontSize = 12.sp) }
        if (items.isNotEmpty()) {
            Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Photos", "Videos", "Both").forEachIndexed { i, n -> PillChip(n, kinds == i) { kinds = i } }
                PillChip("New only", newOnly) { newOnly = !newOnly; pick(items) }
                PillChip("Select all", false) { picked.clear(); picked.addAll(shown.map { it.key }) }
                PillChip("None", false) { picked.clear() }
            }
            val sel = shown.filter { it.key in picked }
            Text("${sel.size} of ${shown.size} selected · ${String.format(java.util.Locale.US, "%.1f", sel.sumOf { it.size } / 1e9)} GB", color = Color.White, fontSize = 12.sp)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp)) {
                val df = java.text.SimpleDateFormat("EEE d MMM yyyy", java.util.Locale.getDefault())
                val groups = shown.groupBy { df.format(java.util.Date(it.date)) }
                groups.forEach { (d, list) ->
                    item(key = "h$d") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(d, style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.weight(1f))
                            Text("ALL", style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp), modifier = Modifier.clickable { list.forEach { if (it.key !in picked) picked.add(it.key) } }.padding(6.dp))
                        }
                    }
                    items(list, key = { it.key }) { f ->
                        val on = f.key in picked
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(6.dp)).background(if (on) Pro.Amber.copy(alpha = 0.14f) else Ed.Raised)
                            .clickable { if (on) picked.remove(f.key) else picked.add(f.key) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (on) "☑" else "☐", color = Pro.Amber, fontSize = 15.sp)
                            Text((if (f.video) "▶ " else "") + f.name, color = Color.White, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            if (f.key in seen) Text("imported", style = Pro.Label.copy(color = Pro.Dim, fontSize = 9.sp), modifier = Modifier.padding(end = 6.dp))
                            Text(String.format(java.util.Locale.US, "%.1f MB", f.size / 1048576f), color = Pro.Dim, fontSize = 11.sp)
                        }
                    }
                }
            }
            Text("Rename", color = Color.White, fontSize = 13.sp)
            Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(6.dp)) { ProImport.RENAME.forEachIndexed { i, n -> PillChip(n, pattern == i) { pattern = i } } }
            if (pattern == 3) OutlinedTextField(prefix, { prefix = it }, label = { Text("Prefix (e.g. Faroe_Trip)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Pro.Fg, unfocusedTextColor = Pro.Fg, focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber))
            Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillChip("A folder per day", byDay) { byDay = !byDay }
                PillChip(if (backup == null) "+ Backup copy to…" else "Backup ✓ (tap to remove)", backup != null) { if (backup == null) try { pickBackup.launch(null) } catch (_: Throwable) {} else backup = null }
            }
            Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Metadata", color = Pro.Dim, fontSize = 12.sp)
                PillChip("None", meta == null) { meta = null }
                MetaPresets.list.forEach { p -> PillChip(p.name, meta == p) { meta = p } }
            }
            if (kinds != 1 && UserPresets.list.isNotEmpty()) Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Develop (photos)", color = Pro.Dim, fontSize = 12.sp)
                PillChip("None", develop == null) { develop = null }
                UserPresets.list.forEach { p -> PillChip(p.name, develop == p.id) { develop = p.id } }
            }
            Text(if (busy != null && stop.not() && busy!!.startsWith("Copying")) "STOP" else "IMPORT ${sel.size}", style = Pro.Label.copy(color = Color.Black, fontSize = 13.sp),
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Pro.Amber).clickable {
                    if (busy?.startsWith("Copying") == true) { stop = true; return@clickable }
                    if (sel.isEmpty()) return@clickable
                    stop = false
                    val o = ProImport.Options(pattern, prefix, byDay, backup, meta, develop)
                    val src = mtp
                    scope.launch {
                        val res = withContext(Dispatchers.IO) {
                            ProImport.run(context, sel, o, open = { f -> if (src != null && f.mtpHandle != null) src.open(context, f) else f.doc?.let { d -> context.contentResolver.openInputStream(d) } },
                                progress = { d, t, n -> busy = if (d >= t) null else "Copying ${d + 1} of $t · $n" }, stop = { stop })
                        }
                        busy = "${res.size} file(s) imported" + (if (res.size < sel.size) " — ${sel.size - res.size} couldn't be copied" else "")
                        items = items.toList()
                        onDone(res)
                    }
                }.padding(14.dp))
        } else if (scanning == null) Text("Choose where to import from. For a memory card or USB drive, pick its top folder (or DCIM). For a camera, connect it with its USB cable and set it to PTP / MTP.",
            color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** A camera connected by USB cable (PTP / MTP), read with Android's own MTP support. */
class MtpSource private constructor(private val dev: android.mtp.MtpDevice) {
    fun close() { try { dev.close() } catch (_: Throwable) {} }

    fun scan(): List<ImportItem> {
        val out = ArrayList<ImportItem>()
        for (sid in dev.storageIds ?: intArrayOf()) {
            val handles = try { dev.getObjectHandles(sid, 0, 0) } catch (_: Throwable) { null } ?: continue
            for (h in handles) {
                val info = try { dev.getObjectInfo(h) } catch (_: Throwable) { null } ?: continue
                if (info.format == android.mtp.MtpConstants.FORMAT_ASSOCIATION) continue
                val name = info.name ?: continue
                val v = ProImport.kindOf(name) ?: continue
                val size = info.compressedSizeLong
                out += ImportItem("$name|$size", name, size, info.dateCreated.takeIf { it > 0 } ?: info.dateModified, v, mtpHandle = h)
            }
        }
        return out.sortedByDescending { it.date }
    }

    /** Every file handle on the camera (fast: no details). */
    fun handles(): Set<Int> {
        val out = HashSet<Int>()
        for (sid in dev.storageIds ?: intArrayOf()) (try { dev.getObjectHandles(sid, 0, 0) } catch (_: Throwable) { null })?.forEach { out += it }
        return out
    }

    /** One file's details, or null for folders and files that aren't photos or videos. */
    fun info(h: Int): ImportItem? {
        val info = dev.getObjectInfo(h) ?: return null
        if (info.format == android.mtp.MtpConstants.FORMAT_ASSOCIATION) return null
        val name = info.name ?: return null
        val v = ProImport.kindOf(name) ?: return null
        val size = info.compressedSizeLong
        return ImportItem("$name|$size", name, size, info.dateCreated.takeIf { it > 0 } ?: info.dateModified, v, mtpHandle = h)
    }

    /** The file's data (copied to a temporary file first: cameras send whole objects). */
    fun open(c: Context, f: ImportItem): java.io.InputStream? {
        val h = f.mtpHandle ?: return null
        val tmp = File(c.cacheDir, "mtp_import_tmp")
        tmp.delete()
        if (!dev.importFile(h, tmp.path)) return null
        return object : java.io.FileInputStream(tmp) { override fun close() { super.close(); tmp.delete() } }
    }

    companion object {
        private const val ACTION = "com.dronedirector.veyrocutpro.USB_CAMERA"

        /** Finds a camera on USB, asks for permission and opens it; [done] on the main thread. */
        fun connect(c: Context, done: (MtpSource?, String?) -> Unit) {
            val um = c.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
            val dev = um.deviceList.values.firstOrNull { d -> (0 until d.interfaceCount).any { d.getInterface(it).interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_STILL_IMAGE } }
                ?: um.deviceList.values.firstOrNull()
                ?: run { done(null, null); return }
            fun open() {
                val conn = um.openDevice(dev) ?: run { done(null, "Couldn't open the camera"); return }
                val m = android.mtp.MtpDevice(dev)
                if (!m.open(conn)) { conn.close(); done(null, "The camera didn't answer — set its USB mode to PTP / MTP and try again"); return }
                done(MtpSource(m), null)
            }
            if (um.hasPermission(dev)) { open(); return }
            val app = c.applicationContext
            val rx = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: Context, i: Intent) {
                    try { app.unregisterReceiver(this) } catch (_: Throwable) {}
                    if (i.getBooleanExtra(android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, false)) open() else done(null, "No permission to use the camera")
                }
            }
            val filter = android.content.IntentFilter(ACTION)
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(rx, filter, Context.RECEIVER_NOT_EXPORTED) else @Suppress("UnspecifiedRegisterReceiverFlag") app.registerReceiver(rx, filter)
            val pi = android.app.PendingIntent.getBroadcast(app, 7, Intent(ACTION).setPackage(app.packageName),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0))
            um.requestPermission(dev, pi)
        }
    }
}
