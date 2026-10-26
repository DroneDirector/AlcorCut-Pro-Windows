package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.security.MessageDigest

/**
 * Photo catalogs. A catalog remembers every photo you work on in the photo editor: a copy of the
 * photo itself, its edit and settings, stars / flags / labels, and the results of the AI tools (so
 * AI noise reduction never has to run again for a photo in the catalog). Own code.
 *
 * Each catalog is one folder:
 *   session.json  the photos (the filmstrip) with ratings and when each was last opened
 *   projects/     each photo's edit
 *   photos/       copies of the photos
 *   ai/           AI results (file names start with the photo's id)
 *
 * You choose how many photos a catalog remembers (10 or more, or unlimited); when it's full, the
 * photo opened longest ago is let go first. Catalogs can be created, renamed, deleted, switched and
 * moved to another storage location.
 */
data class CatalogInfo(val id: String, val name: String, val root: String, val created: Long = System.currentTimeMillis())

/** Catalog management shared by the photo editor ([Catalogs]) and the video editor ([VideoCatalogs]). */
open class CatalogStore(
    private val prefsName: String,
    private val folder: String,
    private val firstName: String,
    private val copiesDefault: Boolean,
) {

    val list = mutableStateListOf<CatalogInfo>()
    var activeId by mutableStateOf("")
        private set
    /** Photos a catalog remembers; 0 = unlimited. */
    var limit by mutableIntStateOf(0)
        private set
    /** Keep a copy of each photo in the catalog (opens even if the original is moved or deleted). */
    var keepCopies by mutableStateOf(true)
        private set
    /** Bumped when another catalog is opened: the editor goes back to its start page. */
    var version by mutableIntStateOf(0)
        private set
    /** Saves the open photo's edit before the catalog changes (set by the editor while it's open). */
    @Volatile var beforeSwitch: (() -> Unit)? = null

    val MIN_LIMIT = 10
    private var ready = false

    private fun prefs(c: Context) = c.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    @Synchronized fun init(context: Context) {
        if (ready) return
        val c = context.applicationContext
        ready = true
        val p = prefs(c)
        try {
            val arr = ProjectStore.gson.fromJson(p.getString("list", "[]"), Array<CatalogInfo>::class.java)
            list.clear(); arr?.filterNotNull()?.let { list.addAll(it) }
        } catch (_: Throwable) {}
        limit = p.getInt("limit", 0).let { if (it in 1 until MIN_LIMIT) MIN_LIMIT else it.coerceAtLeast(0) }
        keepCopies = p.getBoolean("copies", copiesDefault)
        if (list.isEmpty()) {
            // First start: the existing filmstrip and its edits become the first catalog.
            val info = CatalogInfo(newId(), firstName, defaultRoot(c).path)
            list.add(info)
            activeId = info.id
            migrate(c, dir(info))
            store(c)
        } else activeId = p.getString("active", null)?.takeIf { id -> list.any { it.id == id } } ?: list.first().id
    }

    private fun store(c: Context) {
        prefs(c).edit().putString("list", ProjectStore.gson.toJson(list.toList())).putString("active", activeId)
            .putInt("limit", limit).putBoolean("copies", keepCopies).apply()
    }

    private fun newId() = java.util.UUID.randomUUID().toString().take(12)
    private fun defaultRoot(c: Context) = File(c.filesDir, folder)

    fun dir(info: CatalogInfo) = File(info.root, info.id).apply { mkdirs() }
    fun active(context: Context): CatalogInfo { init(context); return list.firstOrNull { it.id == activeId } ?: list.first() }
    fun dir(context: Context): File = dir(active(context))
    fun sub(context: Context, name: String) = File(dir(context), name).apply { mkdirs() }

    /** A short id for a photo (its uri), used to name its files in the catalog. */
    fun photoId(uri: String): String =
        MessageDigest.getInstance("SHA-1").digest(uri.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    // ---- settings

    fun setLimit(context: Context, n: Int) {
        init(context)
        limit = if (n <= 0) 0 else n.coerceAtLeast(MIN_LIMIT)
        store(context)
        enforce(context)
    }

    fun setKeepCopies(context: Context, on: Boolean) {
        init(context); keepCopies = on; store(context)
        if (on) copyAll(context)
    }

    // ---- catalogs

    fun create(context: Context, name: String): CatalogInfo {
        init(context)
        val info = CatalogInfo(newId(), name.ifBlank { "Catalog ${list.size + 1}" }, active(context).root)
        list.add(info); dir(info); store(context)
        return info
    }

    fun rename(context: Context, id: String, name: String) {
        val i = list.indexOfFirst { it.id == id }; if (i < 0 || name.isBlank()) return
        list[i] = list[i].copy(name = name.trim().take(60)); store(context)
    }

    /** Opens another catalog: the open photo's edit is saved to the current one first. */
    fun open(context: Context, id: String) {
        init(context)
        if (id == activeId || list.none { it.id == id }) return
        try { beforeSwitch?.invoke() } catch (_: Throwable) {}
        saveOpen(context)
        activeId = id; store(context)
        reloadOpen(context)
        version++
    }

    /** Deletes a catalog and everything in it (copies, edits, AI results). The last one can't be deleted. */
    fun delete(context: Context, id: String): Boolean {
        init(context)
        if (list.size <= 1) return false
        val info = list.firstOrNull { it.id == id } ?: return false
        if (id == activeId) open(context, list.first { it.id != id }.id)
        list.removeAll { it.id == id }; store(context)
        try { dir(info).deleteRecursively() } catch (_: Throwable) {}
        return true
    }

    /** Where catalogs can live: the app's private storage, the phone's shared storage, SD cards. */
    fun locations(context: Context): List<Pair<String, File>> {
        val out = mutableListOf("Phone — private app storage" to defaultRoot(context))
        context.getExternalFilesDirs(null).filterNotNull().forEachIndexed { i, d ->
            out += (if (i == 0) "Phone storage (Android/data folder)" else "SD card" + (if (i > 1) " $i" else "")) to File(d, folder)
        }
        return out
    }

    /** Moves a catalog's folder to [root] (copy, check, then remove the old one). Runs off the main thread. */
    fun move(context: Context, id: String, root: File, progress: (Int) -> Unit): Boolean {
        init(context)
        val info = list.firstOrNull { it.id == id } ?: return false
        if (File(info.root).canonicalPath == root.canonicalPath) return true
        val from = dir(info); val to = File(root, info.id)
        if (id == activeId) { try { beforeSwitch?.invoke() } catch (_: Throwable) {}; saveOpen(context) }
        return try {
            val files = from.walkTopDown().filter { it.isFile }.toList()
            val total = files.sumOf { it.length() }.coerceAtLeast(1L)
            if (root.mkdirs() || root.isDirectory) Unit
            if (root.usableSpace in 1 until total + 50L * 1024 * 1024) return false
            var done = 0L
            for (f in files) {
                val t = File(to, f.relativeTo(from).path); t.parentFile?.mkdirs()
                f.copyTo(t, overwrite = true)
                if (t.length() != f.length()) throw java.io.IOException("copy check failed")
                done += f.length(); progress((done * 100 / total).toInt())
            }
            val i = list.indexOfFirst { it.id == id }
            list[i] = info.copy(root = root.path); store(context)
            from.deleteRecursively()
            if (id == activeId) reloadOpen(context)
            true
        } catch (_: Throwable) { to.deleteRecursively(); false }
    }

    fun sizeOf(info: CatalogInfo): Long = try { dir(info).walkTopDown().filter { it.isFile }.sumOf { it.length() } } catch (_: Throwable) { 0L }
    open fun countOf(info: CatalogInfo): Int = try {
        val t = object : com.google.gson.reflect.TypeToken<List<SessionPhoto>>() {}.type
        (ProjectStore.gson.fromJson<List<SessionPhoto>>(File(dir(info), "session.json").readText(), t) ?: emptyList()).size
    } catch (_: Throwable) { 0 }

    // ---- first start: the old filmstrip moves into the catalog

    /** First start of the photo catalogs: the old filmstrip moves in (see [Catalogs]). */
    protected open fun migrate(c: Context, to: File) {}

    protected fun migratePhotos(c: Context, to: File) {
        try {
            val old = File(c.filesDir, "photo_session.json")
            if (!old.exists()) return
            val t = object : com.google.gson.reflect.TypeToken<List<SessionPhoto>>() {}.type
            val items: List<SessionPhoto> = ProjectStore.gson.fromJson(old.readText(), t) ?: emptyList()
            val proj = File(to, "projects").apply { mkdirs() }
            val legacy = File(c.filesDir, "projects/photo")
            for (sp in items) sp.projectId?.let { id ->
                for (ext in listOf("json", "jpg")) File(legacy, "$id.$ext").takeIf { it.exists() }?.let { f ->
                    f.copyTo(File(proj, f.name), overwrite = true); f.delete()
                }
            }
            old.copyTo(File(to, "session.json"), overwrite = true); old.delete()
            // AI results made before catalogs existed are kept (named without a photo id; still found by key).
            File(c.filesDir, "ai_denoise").takeIf { it.isDirectory }?.listFiles()?.forEach { f ->
                f.copyTo(File(File(to, "ai").apply { mkdirs() }, f.name), overwrite = true); f.delete()
            }
        } catch (_: Throwable) {}
    }

    /** A file in the catalog for a copy of [uri]. */
    fun copyFile(context: Context, uri: String, name: String, into: String = "photos"): File {
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 } ?: "jpg"
        return File(sub(context, into), photoId(uri) + "." + ext)
    }

    /**
     * Packs a RAW / TIFF copy losslessly (deflate) when that makes it at least 10 % smaller; the
     * unpacked file is byte-for-byte the original. Returns the file that was kept.
     */
    fun packIfWorthIt(f: File): File {
        val ext = f.extension.lowercase()
        if (ext !in listOf("dng", "tif", "tiff", "nef", "cr2", "cr3", "arw", "raf", "orf", "rw2", "pef", "srw")) return f
        return try {
            val gz = File(f.path + ".gz"); val tmp = File(gz.path + ".part")
            f.inputStream().use { inp -> java.util.zip.GZIPOutputStream(tmp.outputStream().buffered(1 shl 16), 1 shl 16).use { inp.copyTo(it, 1 shl 16) } }
            if (tmp.length() < f.length() * 0.9) { tmp.renameTo(gz); f.delete(); gz } else { tmp.delete(); f }
        } catch (_: Throwable) { f }
    }

    /** A packed copy, unpacked (once) into the app's cache so it opens at full original quality. */
    fun unpacked(context: Context, gz: File): File? = try {
        val out = File(File(context.cacheDir, "catalog_open").apply { mkdirs() }, gz.name.removeSuffix(".gz"))
        if (!out.exists() || out.lastModified() < gz.lastModified()) {
            val tmp = File(out.path + ".part")
            java.util.zip.GZIPInputStream(gz.inputStream().buffered(1 shl 16), 1 shl 16).use { inp -> tmp.outputStream().use { inp.copyTo(it, 1 shl 16) } }
            tmp.renameTo(out)
        } else out.setLastModified(System.currentTimeMillis())
        // Only the most recently opened few stay unpacked.
        out.parentFile?.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
        out
    } catch (_: Throwable) { null }

    /** True when [u] can be read (the original still exists and the app may open it). */
    fun readable(context: Context, u: Uri): Boolean = try {
        context.contentResolver.openInputStream(u)?.use { true } ?: false
    } catch (_: Throwable) { false }

    // What a catalog holds is up to the editor using it.
    protected open fun saveOpen(context: Context) {}
    protected open fun reloadOpen(context: Context) {}
    protected open fun enforce(context: Context) {}
    protected open fun copyAll(context: Context) {}
}

/** The photo editor's catalogs: the filmstrip ([PhotoSession]) with each photo's copy, edit and AI results. */
object Catalogs : CatalogStore("catalogs", "catalogs", "My catalog", true) {
    override fun saveOpen(context: Context) = PhotoSession.save(context)
    override fun reloadOpen(context: Context) = PhotoSession.reload(context)
    override fun enforce(context: Context) = PhotoSession.enforceLimit(context)
    override fun copyAll(context: Context) = PhotoSession.copyMissing(context)
    override fun migrate(c: Context, to: File) = migratePhotos(c, to)
}
