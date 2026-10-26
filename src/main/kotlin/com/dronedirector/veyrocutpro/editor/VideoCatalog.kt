package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** The video editor's catalogs (see [CatalogStore]); what each holds is kept by [MediaCatalog]. */
object VideoCatalogs : CatalogStore("video_catalogs", "video_catalogs", "My video catalog", false) {
    override fun saveOpen(context: Context) = MediaCatalog.save(context)
    override fun reloadOpen(context: Context) = MediaCatalog.reload(context)
    override fun enforce(context: Context) = MediaCatalog.enforceLimit(context)
    override fun copyAll(context: Context) = MediaCatalog.copyMissing(context)
    override fun countOf(info: CatalogInfo): Int = try {
        val t = object : com.google.gson.reflect.TypeToken<List<MediaCatalog.Entry>>() {}.type
        (ProjectStore.gson.fromJson<List<MediaCatalog.Entry>>(File(dir(info), "media.json").readText(), t) ?: emptyList()).size
    } catch (_: Throwable) { 0 }
}

/**
 * The open video catalog: every clip you use, with
 *  - its last settings (grade, detail, stabilisation, film look, desqueeze, audio), given back when
 *    the clip is added to a project again;
 *  - the slow work done for it — proxies, stabilisation analysis, waveforms, render cache, cloud AI
 *    results, transcripts — kept in the catalog's folders (not the cache Android may clear);
 *  - optionally a copy of the clip itself.
 * Own code. Everything is stored as is (video is already compressed) — nothing lossy.
 */
object MediaCatalog {
    /** A clip's settings worth giving back. */
    data class Look(
        val grade: ProGrade? = null, val detail: Detail? = null, val stab: Stabilize? = null,
        val film: FilmLook? = null, val desqueeze: Float? = null, val audio: AudioFx? = null,
    )

    data class Entry(
        val uri: String,
        val name: String = "",
        val used: Long = 0L,
        /** Files made for this clip, relative to the catalog folder. */
        val files: List<String> = emptyList(),
        val look: Look? = null,
        /** File name of the clip's copy (media/), if made. */
        val copy: String? = null,
    )

    private val items = LinkedHashMap<String, Entry>()
    private var loaded = false
    private var dirty = false
    /** Clips of the open project: never let go while they're in use. */
    @Volatile var inUse: Set<String> = emptySet()

    var recall by mutableStateOf(true)
        private set

    private fun file(context: Context) = File(VideoCatalogs.dir(context), "media.json")

    @Synchronized fun load(context: Context) {
        if (loaded) return
        loaded = true
        recall = context.getSharedPreferences("video_catalogs", Context.MODE_PRIVATE).getBoolean("recall", true)
        // Made before catalogs existed: moved into the catalog once, so nothing has to be made again.
        try {
            val root = VideoCatalogs.dir(context)
            for ((from, to) in listOf(File(context.cacheDir, "proxies") to "proxies", File(context.cacheDir, "stab") to "stab",
                File(context.cacheDir, "waves") to "waves", File(context.cacheDir, "rendercache") to "rendercache",
                File(context.filesDir, "transcripts") to "transcripts")) {
                if (!from.isDirectory) continue
                val d = File(root, to).apply { mkdirs() }
                from.listFiles()?.forEach { f -> val t = File(d, f.name); if (!f.renameTo(t)) { try { f.copyTo(t, true); f.delete() } catch (_: Throwable) {} } }
                from.delete()
            }
        } catch (_: Throwable) {}
        try {
            val f = file(context)
            if (!f.exists()) return
            val t = object : com.google.gson.reflect.TypeToken<List<Entry>>() {}.type
            val list: List<Entry> = ProjectStore.gson.fromJson(f.readText(), t) ?: emptyList()
            items.clear(); list.forEach { items[it.uri] = it }
        } catch (_: Throwable) {}
    }

    @Synchronized fun save(context: Context) {
        if (!loaded) return
        try { file(context).writeText(ProjectStore.gson.toJson(items.values.toList())); dirty = false } catch (_: Throwable) {}
    }

    @Synchronized fun reload(context: Context) { loaded = false; items.clear(); load(context) }

    fun setRecall(context: Context, on: Boolean) {
        recall = on
        context.getSharedPreferences("video_catalogs", Context.MODE_PRIVATE).edit().putBoolean("recall", on).apply()
    }

    /** The catalog folder [name] for files made for clips (proxies, analysis…). */
    fun sub(context: Context, name: String): File = VideoCatalogs.sub(context, name)

    /** Records that [f] was made for (or belongs to) the clip [uri], so it goes with the clip. */
    @Synchronized fun claim(context: Context, uri: Uri, f: File) {
        load(context)
        val key = uri.toString()
        val root = VideoCatalogs.dir(context)
        val rel = try { f.relativeTo(root).path } catch (_: Throwable) { return }
        if (rel.startsWith("..")) return
        val e = items[key] ?: Entry(key, used = System.currentTimeMillis())
        if (rel in e.files) { if (items[key] == null) items[key] = e; return }
        items[key] = e.copy(files = e.files + rel)
        dirty = true
        saveSoon(context)
    }

    private var saving = false
    private fun saveSoon(context: Context) {
        if (saving) return
        saving = true
        val app = context.applicationContext
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ saving = false; Thread { if (dirty) save(app) }.start() }, 3000)
    }

    /**
     * A clip as it's added to a project: registered in the catalog and, when [recall] is on and the
     * clip was used before, given its last settings.
     */
    fun added(context: Context, c: Clip): Clip {
        val look = synchronized(this) {
            load(context)
            val key = c.uri.toString()
            val e = items[key]
            items[key] = (e ?: Entry(key, c.name)).copy(name = c.name, used = System.currentTimeMillis())
            dirty = true
            e?.look
        }
        saveSoon(context)
        enforceLimit(context)
        copyMissing(context)
        if (!recall || look == null) return c
        return c.copy(
            grade = look.grade ?: c.grade, detail = look.detail ?: c.detail, stab = look.stab ?: c.stab,
            film = look.film ?: c.film, desqueeze = look.desqueeze ?: c.desqueeze, audio = look.audio ?: c.audio,
        )
    }

    /** Keeps each clip's current settings (called when the project is saved or autosaved). */
    fun remember(context: Context, clips: List<Clip>) {
        synchronized(this) {
            load(context)
            val now = System.currentTimeMillis()
            for (c in clips) {
                if (c.nested != null || c.uri == Uri.EMPTY) continue
                val key = c.uri.toString()
                val look = Look(c.grade, c.detail, c.stab, c.film, c.desqueeze, c.audio)
                val e = items[key] ?: Entry(key, c.name)
                if (e.look != look || now - e.used > 60_000) { items[key] = e.copy(look = look, used = now); dirty = true }
            }
            inUse = clips.map { it.uri.toString() }.toSet()
        }
        if (dirty) save(context)
    }

    /** A clip whose original can't be read any more opens from the catalog's copy, if there is one. */
    fun resolve(context: Context, c: Clip): Clip {
        load(context)
        val cp = synchronized(this) { items[c.uri.toString()]?.copy } ?: return c
        val f = File(sub(context, "media"), cp)
        if (!f.exists() || Catalogs.readable(context, c.uri)) return c
        return c.copy(uri = Uri.fromFile(f))
    }

    /** Lets go of the clips used longest ago while the catalog holds more than its limit. */
    fun enforceLimit(context: Context) {
        val lim = VideoCatalogs.limit
        val drop = synchronized(this) {
            load(context)
            if (lim <= 0 || items.size <= lim) return
            items.values.filter { it.uri !in inUse }.sortedBy { it.used }.take(items.size - lim)
        }
        val root = VideoCatalogs.dir(context)
        for (e in drop) {
            e.files.forEach { File(root, it).delete() }
            e.copy?.let { File(sub(context, "media"), it).delete() }
            synchronized(this) { items.remove(e.uri) }
        }
        save(context)
    }

    /** Copies clips that have no copy yet (in the background), when copies are on. */
    fun copyMissing(context: Context) {
        if (!VideoCatalogs.keepCopies) return
        val app = context.applicationContext
        val todo = synchronized(this) { items.values.filter { it.copy == null && Uri.parse(it.uri).scheme == "content" }.map { it.uri to it.name } }
        if (todo.isEmpty()) return
        Thread({
            for ((u, name) in todo) try {
                val f = VideoCatalogs.copyFile(app, u, name.ifBlank { "clip.mp4" }, into = "media")
                if (!f.exists()) {
                    val tmp = File(f.path + ".part")
                    app.contentResolver.openInputStream(Uri.parse(u))?.use { inp -> tmp.outputStream().use { inp.copyTo(it, 1 shl 16) } } ?: continue
                    tmp.renameTo(f)
                }
                synchronized(this) { items[u]?.let { items[u] = it.copy(copy = f.name) } }
                save(app)
            } catch (_: Throwable) {}
        }, "catalog-copy").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /** Removes everything that can be made again; settings and copies stay. Returns the bytes freed. */
    fun slimDown(context: Context): Long {
        load(context)
        var n = 0L
        for (d in listOf("proxies", "stab", "waves", "rendercache", "cloudai", "transcripts", "thumbs", "aiclean")) {
            val dir = File(VideoCatalogs.dir(context), d)
            dir.walkTopDown().filter { it.isFile }.forEach { n += it.length(); it.delete() }
        }
        synchronized(this) { items.keys.toList().forEach { k -> items[k] = items[k]!!.copy(files = emptyList()) } }
        save(context)
        return n
    }
}
