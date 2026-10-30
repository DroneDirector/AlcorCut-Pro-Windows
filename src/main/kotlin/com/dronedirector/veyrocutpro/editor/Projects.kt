package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

data class VideoProjectData(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val savedAt: Long = 0L,
    val clips: List<Clip> = emptyList(),
    val layers: List<Layer> = emptyList(),
    val music: MusicTrack? = null,
    val extraAudio: List<MusicTrack> = emptyList(),
    val audioPool: List<PoolAudio> = emptyList(),
    val deliver: DeliverSettings = DeliverSettings(),
    /** Main video track switches on the timeline. */
    val videoMuted: Boolean = false,
    val videoLocked: Boolean = false,
    val videoHidden: Boolean = false,
    /** Timeline markers (ms). */
    val markers: List<Long> = emptyList(),
    /** Notes on markers, keyed by the marker's time (ms) as text. */
    val markerNotes: Map<String, String>? = null,
    /** Media in the Proxy section, by stable media ID ([MediaId]). */
    val proxyMedia: List<String>? = null,
    /** Media pool bins: file (uri text) → bin name, and the bins in order. */
    val bins: Map<String, String>? = null,
    val binNames: List<String>? = null,
    /** The media strip: every clip imported into the project, used on the timeline or not. */
    val library: List<Clip>? = null,
    /** Media strip ratings: file (uri text) → 1–5 stars, or −1 rejected. */
    val ratings: Map<String, Int>? = null,
    /** True for automatic saves (gear → AUTOSAVE); listed apart from your own projects. */
    val autosave: Boolean? = null,
    /** Other timelines in this project (the open one is the fields above), and the open one's name. */
    val timelines: List<SavedTimeline>? = null,
    val timelineName: String? = null,
)

/** A timeline kept in a project next to the open one (Media → TIMELINES). */
data class SavedTimeline(
    val name: String,
    val clips: List<Clip> = emptyList(),
    val layers: List<Layer> = emptyList(),
    val music: MusicTrack? = null,
    val extraAudio: List<MusicTrack> = emptyList(),
    val markers: List<Long> = emptyList(),
    val markerNotes: Map<String, String>? = null,
    val deliver: DeliverSettings = DeliverSettings(),
    val videoMuted: Boolean = false,
    val videoHidden: Boolean = false,
)

data class PhotoProjectData(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val savedAt: Long = 0L,
    val uri: Uri = Uri.EMPTY,
    val grade: ProGrade = ProGrade(),
    val detail: Detail = Detail(),
    val geometry: Geometry = Geometry(),
    val watermark: Layer? = null,
    val format: Int = 0,
    val quality: Float = 0.95f,
    val sizeIdx: Int = 0,
    val masks: List<PhotoMask> = emptyList(),
    val spots: List<Spot> = emptyList(),
    val lens: LensFix = LensFix(),
    val texts: List<Layer>? = null,
    val sky: SkySwap? = null,
    val bg: PhotoBackground = PhotoBackground(),
    /** Photo after AI erase (a copy kept in the app's files), used instead of the original. */
    val baseFile: String? = null,
    /** RAW (DNG) develop choices. */
    val raw: RawDng.Dev? = null,
    /** True for the hidden per-photo edits of the filmstrip (not shown in the project list). */
    val session: Boolean? = null,
    /** Lens Blur (depth of field from the AI depth map). */
    val lensBlur: LensBlur? = null,
    val retouch: Retouch? = null,
    /** Title, caption, author, copyright and keywords written into exports. */
    val meta: PhotoMetaEdit? = null,
    /** True for automatic saves (gear → AUTOSAVE); listed apart from your own projects. */
    val autosave: Boolean? = null,
)

/** A saved project as shown in the list. */
data class ProjectEntry(val id: String, val name: String, val savedAt: Long, val thumb: File?, val detail: String)

/** Saves editor projects (settings + links to the media, never copies of it) in the app's private storage. */
object ProjectStore {
    internal val gson: Gson = GsonBuilder()
        .registerTypeHierarchyAdapter(Uri::class.java, JsonSerializer<Uri> { src, _, _ -> JsonPrimitive(src.toString()) })
        .registerTypeHierarchyAdapter(Uri::class.java, JsonDeserializer { json, _, _ -> Uri.parse(json.asString) })
        .create()

    private fun dir(context: Context, video: Boolean) = File(context.filesDir, if (video) "projects/video" else "projects/photo").apply { mkdirs() }

    fun defaultName(video: Boolean): String =
        (if (video) "Video " else "Photo ") + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date())

    private fun keepAccess(context: Context, uris: List<Uri?>) {
        for (u in uris) if (u != null && u.scheme == "content") {
            try { context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
        }
    }

    private fun saveThumb(context: Context, video: Boolean, id: String, bmp: Bitmap?, into: File? = null) {
        bmp ?: return
        try {
            val s = 320f / maxOf(bmp.width, bmp.height)
            val t = if (s < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true) else bmp
            File(into ?: dir(context, video), "$id.jpg").outputStream().use { t.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (_: Throwable) {}
    }

    fun saveVideo(context: Context, p: VideoProjectData, thumb: Bitmap?): VideoProjectData {
        val out = p.copy(savedAt = System.currentTimeMillis())
        keepAccess(context, out.clips.map { it.uri } + out.layers.flatMap { listOf(it.imageUri, it.videoUri) } + listOf(out.music?.uri) + out.extraAudio.map { it.uri } + out.audioPool.map { it.uri })
        val json = gson.toJson(out)
        File(dir(context, true), "${out.id}.json").writeText(json)
        ProTools.saveVersion(context, out, json)
        saveThumb(context, true, out.id, thumb)
        return out
    }

    fun savePhoto(context: Context, p: PhotoProjectData, thumb: Bitmap?): PhotoProjectData {
        val out = p.copy(savedAt = System.currentTimeMillis())
        keepAccess(context, listOf(out.uri, out.watermark?.imageUri))
        // A filmstrip photo's edit lives in the open catalog; named projects in the project list.
        val d = if (out.session == true) Catalogs.sub(context, "projects") else dir(context, false)
        File(d, "${out.id}.json").writeText(gson.toJson(out))
        saveThumb(context, false, out.id, thumb, d)
        return out
    }

    // ---- auto-save (crash recovery): one hidden file, never shown in the project list

    private fun autosaveFile(context: Context) = File(dir(context, true), "_autosave.json.tmp")

    fun writeAutosave(context: Context, json: String) {
        try {
            val f = autosaveFile(context)
            val tmp = File(f.parentFile, f.name + ".new")
            tmp.writeText(json)
            tmp.renameTo(f)
        } catch (_: Throwable) {}
    }

    /** The edit that was open when the app last closed without leaving the editor, or null. */
    fun loadAutosave(context: Context): VideoProjectData? {
        val f = autosaveFile(context)
        if (!f.exists()) return null
        return try { fixVideo(gson.fromJson(f.readText(), VideoProjectData::class.java)) } catch (_: Throwable) { null }
    }

    fun autosaveTime(context: Context): Long = autosaveFile(context).let { if (it.exists()) it.lastModified() else 0L }

    fun clearAutosave(context: Context) { try { autosaveFile(context).delete() } catch (_: Throwable) {} }

    /** Keeps read access to the media of an auto-saved edit, so it can still be opened after a restart. */
    fun keepAutosaveAccess(context: Context, p: VideoProjectData) =
        keepAccess(context, p.clips.map { it.uri } + p.layers.flatMap { listOf(it.imageUri, it.videoUri) } + listOf(p.music?.uri) + p.extraAudio.map { it.uri } + p.audioPool.map { it.uri })

    /**
     * Makes a recovered edit safe to open: media that can no longer be read (deleted, moved, or
     * access lost) is left out instead of being loaded. Returns the cleaned edit and how much was left out.
     */
    fun sanitizeRecovered(context: Context, p: VideoProjectData): Pair<VideoProjectData, Int> {
        val ok = HashMap<String, Boolean>()
        fun readable(u: Uri?): Boolean {
            if (u == null || u == Uri.EMPTY || u.toString().isBlank()) return false
            return ok.getOrPut(u.toString()) {
                try {
                    if (u.scheme == "file") File(u.path ?: "").canRead()
                    else context.contentResolver.openFileDescriptor(u, "r")?.use { true } ?: false
                } catch (_: Throwable) { false }
            }
        }
        val clips = p.clips.filter { c -> readable(c.uri) && (c.nested?.all { readable(it.uri) } ?: true) && c.durationMs > 0 && c.width > 0 && c.height > 0 }
        val layers = p.layers.filter { l -> (l.imageUri == null || readable(l.imageUri)) && (l.videoUri == null || readable(l.videoUri)) }
        val music = p.music?.takeIf { readable(it.uri) }
        val extra = p.extraAudio.filter { readable(it.uri) }
        val pool = p.audioPool.filter { readable(it.uri) }
        val dropped = (p.clips.size - clips.size) + (p.layers.size - layers.size) + (if (p.music != null && music == null) 1 else 0) +
            (p.extraAudio.size - extra.size) + (p.audioPool.size - pool.size)
        return p.copy(clips = clips, layers = layers, music = music, extraAudio = extra, audioPool = pool) to dropped
    }

    private fun fixVideo(p: VideoProjectData): VideoProjectData {
        Defaults.fix(p, VideoProjectData())
        p.clips.forEach { Defaults.fix(it, Clip(uri = Uri.EMPTY, name = "", durationMs = 1, width = 1, height = 1)) }
        // Clips inside nested (compound) clips.
        p.clips.forEach { c -> c.nested?.forEach { Defaults.fix(it, Clip(uri = Uri.EMPTY, name = "", durationMs = 1, width = 1, height = 1)) } }
        p.library?.forEach { Defaults.fix(it, Clip(uri = Uri.EMPTY, name = "", durationMs = 1, width = 1, height = 1)) }
        p.layers.forEach { Defaults.fix(it, Layer()) }
        p.music?.let { Defaults.fix(it, MusicTrack(Uri.EMPTY, "", 1)) }
        p.extraAudio.forEach { Defaults.fix(it, MusicTrack(Uri.EMPTY, "", 1)) }
        return p
    }

    /** A video project from its JSON (missing newer settings filled with defaults). */
    fun parseVideo(json: String): VideoProjectData? = try { fixVideo(gson.fromJson(json, VideoProjectData::class.java)) } catch (_: Throwable) { null }

    fun loadVideo(context: Context, id: String): VideoProjectData? = try {
        val p = gson.fromJson(File(dir(context, true), "$id.json").readText(), VideoProjectData::class.java)
        Defaults.fix(p, VideoProjectData())
        p.clips.forEach { Defaults.fix(it, Clip(uri = Uri.EMPTY, name = "", durationMs = 1, width = 1, height = 1)) }
        p.layers.forEach { Defaults.fix(it, Layer()) }
        p.music?.let { Defaults.fix(it, MusicTrack(Uri.EMPTY, "", 1)) }
        p.extraAudio.forEach { Defaults.fix(it, MusicTrack(Uri.EMPTY, "", 1)) }
        p
    } catch (_: Throwable) { null }

    /** A photo edit's file: in the open catalog, else in the project list. */
    private fun photoFile(context: Context, id: String, ext: String): File =
        File(Catalogs.sub(context, "projects"), "$id.$ext").takeIf { it.exists() } ?: File(dir(context, false), "$id.$ext")

    fun loadPhoto(context: Context, id: String): PhotoProjectData? = try {
        val p = gson.fromJson(photoFile(context, id, "json").readText(), PhotoProjectData::class.java)
        Defaults.fix(p, PhotoProjectData())
        p.watermark?.let { Defaults.fix(it, Layer()) }
        p
    } catch (_: Throwable) { null }

    fun list(context: Context, video: Boolean, autosaves: Boolean = false): List<ProjectEntry> {
        val d = dir(context, video)
        return d.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { f ->
            val id = f.name.removeSuffix(".json")
            try {
                val o = com.google.gson.JsonParser.parseString(f.readText()).asJsonObject
                if (!video && o.get("session")?.asBoolean == true) return@mapNotNull null
                if ((o.get("autosave")?.asBoolean == true) != autosaves) return@mapNotNull null
                val detail = if (video) {
                    val n = o.getAsJsonArray("clips")?.size() ?: 0
                    "$n clip" + (if (n == 1) "" else "s")
                } else "Photo"
                ProjectEntry(id, o.get("name")?.asString ?: "Project", o.get("savedAt")?.asLong ?: f.lastModified(), File(d, "$id.jpg").takeIf { it.exists() }, detail)
            } catch (_: Throwable) { null }
        }.sortedByDescending { it.savedAt }
    }

    /** A photo project's thumbnail, if one was saved. */
    fun thumbFile(context: Context, id: String): File? = photoFile(context, id, "jpg").takeIf { it.exists() }

    fun delete(context: Context, video: Boolean, id: String) {
        File(dir(context, video), "$id.json").delete()
        File(dir(context, video), "$id.jpg").delete()
        if (!video) { File(Catalogs.sub(context, "projects"), "$id.json").delete(); File(Catalogs.sub(context, "projects"), "$id.jpg").delete() }
    }
}

/**
 * Projects saved by an older version may lack newer settings; the JSON reader leaves those empty.
 * This fills every missing value (recursively) from a default instance.
 */
internal object Defaults {
    fun fix(target: Any, template: Any) {
        var cls: Class<*>? = target.javaClass
        while (cls != null && cls != Any::class.java) {
            for (f in cls.declaredFields) {
                if (java.lang.reflect.Modifier.isStatic(f.modifiers)) continue
                try {
                    f.isAccessible = true
                    val v = f.get(target)
                    val t = f.get(template)
                    if (v == null && t != null) f.set(target, t)
                    else if (v != null && t != null && v.javaClass == t.javaClass && v.javaClass.name.startsWith("com.dronedirector")) {
                        if (!v.javaClass.isEnum) fix(v, t)
                    } else if (v is List<*> && t is List<*> && t.isNotEmpty()) {
                        val proto = t.first() ?: continue
                        v.forEach { e -> if (e != null && e.javaClass == proto.javaClass && !e.javaClass.isEnum) fix(e, proto) }
                    }
                } catch (_: Throwable) {}
            }
            cls = cls.superclass
        }
    }
}

/**
 * Timed automatic saves of the open edit (gear → AUTOSAVE): every [minutes] (0 = off), keeping
 * the newest [keep] copies (1–5) side by side; older ones are removed. Separate for the photo and
 * the video editor.
 */
object AutoSave {
    val INTERVALS = listOf(0, 1, 2, 5, 10, 15, 30)
    private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)
    fun minutes(c: Context, video: Boolean) = prefs(c).getInt(if (video) "as_min_v" else "as_min_p", 5)
    /** Bumped on every settings change so open editors pick up the new interval. */
    var changes by androidx.compose.runtime.mutableIntStateOf(0)
    fun setMinutes(c: Context, video: Boolean, m: Int) { prefs(c).edit().putInt(if (video) "as_min_v" else "as_min_p", m).apply(); changes++ }
    fun keep(c: Context, video: Boolean) = prefs(c).getInt(if (video) "as_keep_v" else "as_keep_p", 3).coerceIn(1, 5)
    fun setKeep(c: Context, video: Boolean, n: Int) { prefs(c).edit().putInt(if (video) "as_keep_v" else "as_keep_p", n.coerceIn(1, 5)).apply(); prune(c, video) }

    fun nameFor(projectName: String): String =
        "Autosave · " + projectName.ifBlank { "Untitled" } + " · " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date())

    fun saveVideo(c: Context, p: VideoProjectData, thumb: Bitmap?) {
        ProjectStore.saveVideo(c, p.copy(id = "auto_" + UUID.randomUUID(), autosave = true), thumb); prune(c, true)
    }
    fun savePhoto(c: Context, p: PhotoProjectData, thumb: Bitmap?) {
        ProjectStore.savePhoto(c, p.copy(id = "auto_" + UUID.randomUUID(), autosave = true, session = null), thumb); prune(c, false)
    }

    /** Keeps only the newest autosaves. */
    fun prune(c: Context, video: Boolean) {
        val all = ProjectStore.list(c, video, autosaves = true)
        all.drop(keep(c, video)).forEach { ProjectStore.delete(c, video, it.id) }
    }
}

/**
 * Applies only the settings that changed between [prev] and [now] to [target], down to single values inside
 * groups (one slider moved = only that value), so each picked photo or clip keeps the rest of its own edit.
 * [prev] / [now] may be another type with the same field names (the photo editor's edit vs a saved photo).
 */
object EditDiff {
    fun <T : Any> apply(prev: Any, now: Any, target: T, skip: Set<String> = emptySet()): T {
        if (prev == now) return target
        val g = ProjectStore.gson
        val t = g.toJsonTree(target).asJsonObject
        if (!merge(g.toJsonTree(prev).asJsonObject, g.toJsonTree(now).asJsonObject, t, skip)) return target
        return g.fromJson(t, target.javaClass)
    }

    private fun merge(a: com.google.gson.JsonObject, b: com.google.gson.JsonObject, t: com.google.gson.JsonObject, skip: Set<String>): Boolean {
        var changed = false
        for (k in a.keySet() + b.keySet()) {
            if (k in skip) continue
            val av = a.get(k); val bv = b.get(k)
            if (av == bv) continue
            val tv = t.get(k)
            if (av is com.google.gson.JsonObject && bv is com.google.gson.JsonObject && tv is com.google.gson.JsonObject) {
                val copy = tv.deepCopy()
                if (merge(av, bv, copy, emptySet())) { t.add(k, copy); changed = true }
            } else {
                if (bv == null) t.remove(k) else t.add(k, bv.deepCopy())
                changed = true
            }
        }
        return changed
    }
}
