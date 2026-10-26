package com.dronedirector.veyrocutpro.editor

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.reflect.TypeToken
import java.io.File

/** What the library knows about one photo (by its uri text). */
data class LibMeta(
    val rating: Int = 0,          // 0–5
    val flag: Int = 0,            // 1 pick, −1 reject
    val label: Int? = null,       // colour label (ARGB)
    val keywords: List<String> = emptyList(),
    val albums: List<String> = emptyList(),
    /** What the AI saw in the photo (for search). */
    val ai: List<String>? = null,
    /** People: faces found (null = not looked yet). */
    val faces: Int? = null,
    /** Where it was taken: [lat, lon], empty = no location, null = not looked yet. */
    val place: List<Double>? = null,
)

/** A saved search: shows every photo that matches. */
data class SmartAlbum(
    val name: String,
    val minRating: Int = 0,
    val flag: Int = 0,           // 0 any, 1 picks only, 2 not rejected
    val label: Int? = null,
    val text: String = "",
)

/** A photo in the library grid. */
data class LibPhoto(val uri: Uri, val name: String, val date: Long, val w: Int, val h: Int, val size: Long, val mime: String)

/**
 * The photo library: ratings, flags, colour labels, keywords, albums, smart albums and AI labels
 * for photos on the phone — kept in the app's files, the photos themselves are never changed.
 */
object LibraryStore {
    val meta = mutableStateMapOf<String, LibMeta>()
    val albums = mutableStateListOf<String>()
    val smart = mutableStateListOf<SmartAlbum>()
    /** AI indexing progress (labelled / total) while it runs. */
    var indexing by mutableStateOf<Pair<Int, Int>?>(null)
    private var loaded = false

    private data class Saved(val meta: Map<String, LibMeta>? = null, val albums: List<String>? = null, val smart: List<SmartAlbum>? = null)
    private fun file(c: Context) = File(c.filesDir, "library.json")

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(c); if (!f.exists()) return
            val s: Saved = ProjectStore.gson.fromJson(f.readText(), Saved::class.java) ?: return
            s.meta?.forEach { (k, v) -> meta[k] = LibMeta(v.rating, v.flag, v.label, v.keywords ?: emptyList(), v.albums ?: emptyList(), v.ai, v.faces, v.place) }
            s.albums?.let { albums.addAll(it) }
            s.smart?.let { smart.addAll(it) }
        } catch (_: Throwable) {}
    }

    fun save(c: Context) {
        try { file(c).writeText(ProjectStore.gson.toJson(Saved(meta.toMap(), albums.toList(), smart.toList()))) } catch (_: Throwable) {}
    }

    fun get(uri: String) = meta[uri] ?: LibMeta()
    fun update(c: Context, uris: Collection<String>, f: (LibMeta) -> LibMeta) { for (u in uris) meta[u] = f(get(u)); save(c) }

    /** Every photo on the phone (needs the photo permission), newest first; else only AlcorCut Pro's own. */
    fun photos(c: Context): List<LibPhoto> {
        val out = ArrayList<LibPhoto>()
        val col = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE) + (if (Build.VERSION.SDK_INT >= 29) arrayOf(MediaStore.MediaColumns.DATE_TAKEN) else emptyArray())
        try {
            c.contentResolver.query(col, proj, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cur ->
                val iTaken = if (Build.VERSION.SDK_INT >= 29) cur.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN) else -1
                while (cur.moveToNext()) {
                    val id = cur.getLong(0)
                    val taken = if (iTaken >= 0 && !cur.isNull(iTaken)) cur.getLong(iTaken) else cur.getLong(2) * 1000
                    out += LibPhoto(ContentUris.withAppendedId(col, id), cur.getString(1) ?: "", taken, cur.getInt(3), cur.getInt(4), cur.getLong(5), cur.getString(6) ?: "")
                }
            }
        } catch (_: Throwable) {}
        return out
    }

    /** Matches the free-text search against the name, keywords, albums and AI labels. */
    fun matches(p: LibPhoto, text: String): Boolean {
        if (text.isBlank()) return true
        val m = get(p.uri.toString())
        val hay = (listOf(p.name) + m.keywords + m.albums + m.ai.orEmpty()).joinToString(" ").lowercase()
        return text.lowercase().split(' ', ',').filter { it.isNotBlank() }.all { hay.contains(it) }
    }

    fun matchesSmart(p: LibPhoto, s: SmartAlbum): Boolean {
        val m = get(p.uri.toString())
        if (m.rating < s.minRating) return false
        if (s.flag == 1 && m.flag != 1) return false
        if (s.flag == 2 && m.flag == -1) return false
        if (s.label != null && m.label != s.label) return false
        return matches(p, s.text)
    }

    /**
     * Labels photos that have no AI labels yet (what's in them: beach, dog, food, sky…) so they
     * can be found by searching. On the phone (ML Kit); runs in the background a few at a time.
     */
    fun index(c: Context, list: List<LibPhoto>, stop: () -> Boolean) {
        val todo = list.filter { meta[it.uri.toString()]?.ai == null }
        if (todo.isEmpty()) return
        val labeler = try { com.google.mlkit.vision.label.ImageLabeling.getClient(com.google.mlkit.vision.label.defaults.ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build()) } catch (_: Throwable) { return }
        try {
            var done = 0
            indexing = 0 to todo.size
            for (p in todo) {
                if (stop()) break
                val b: Bitmap = try { com.dronedirector.veyrocutpro.camera.GradeIO.decode(c, p.uri, 448) } catch (_: Throwable) { null } ?: run { done++; continue }
                val labels = try {
                    com.google.android.gms.tasks.Tasks.await(labeler.process(com.google.mlkit.vision.common.InputImage.fromBitmap(b, 0)), 20, java.util.concurrent.TimeUnit.SECONDS)
                        .sortedByDescending { it.confidence }.take(8).map { it.text.lowercase() }
                } catch (_: Throwable) { null }
                b.recycle()
                val k = p.uri.toString()
                meta[k] = get(k).copy(ai = labels ?: emptyList())
                done++
                indexing = done to todo.size
                if (done % 20 == 0) save(c)
            }
        } finally {
            try { labeler.close() } catch (_: Throwable) {}
            indexing = null
            save(c)
        }
    }

    /** Progress of the people / places scan (done / total) while it runs. */
    var scanning by mutableStateOf<Pair<Int, Int>?>(null)

    /** Counts the faces in photos not looked at yet (for the People view). On the phone. */
    fun indexFaces(c: Context, list: List<LibPhoto>, stop: () -> Boolean) {
        val todo = list.filter { meta[it.uri.toString()]?.faces == null }
        if (todo.isEmpty()) return
        val det = try { com.google.mlkit.vision.face.FaceDetection.getClient(com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
            .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_FAST).setMinFaceSize(0.05f).build()) } catch (_: Throwable) { return }
        try {
            scanning = 0 to todo.size
            for ((n, p) in todo.withIndex()) {
                if (stop()) break
                val b = try { com.dronedirector.veyrocutpro.camera.GradeIO.decode(c, p.uri, 640) } catch (_: Throwable) { null }
                val count = if (b == null) 0 else try {
                    com.google.android.gms.tasks.Tasks.await(det.process(com.google.mlkit.vision.common.InputImage.fromBitmap(b, 0)), 15, java.util.concurrent.TimeUnit.SECONDS)
                        .count { it.boundingBox.width() >= b.width * 0.04f }
                } catch (_: Throwable) { 0 } finally { b?.recycle() }
                val k = p.uri.toString(); meta[k] = get(k).copy(faces = count)
                scanning = n + 1 to todo.size
                if (n % 25 == 24) save(c)
            }
        } finally { try { det.close() } catch (_: Throwable) {}; scanning = null; save(c) }
    }

    /** Reads where each photo was taken (GPS in its camera details) for the Map view. */
    fun indexPlaces(c: Context, list: List<LibPhoto>, stop: () -> Boolean) {
        val todo = list.filter { meta[it.uri.toString()]?.place == null }
        if (todo.isEmpty()) return
        try {
            scanning = 0 to todo.size
            for ((n, p) in todo.withIndex()) {
                if (stop()) break
                var ll: DoubleArray? = null
                // Android hides photo locations unless the app asks for the original file (needs the "media location" permission).
                val uris = if (Build.VERSION.SDK_INT >= 29) listOf(try { MediaStore.setRequireOriginal(p.uri) } catch (_: Throwable) { p.uri }, p.uri) else listOf(p.uri)
                for (u in uris) {
                    ll = try { c.contentResolver.openInputStream(u)?.use { androidx.exifinterface.media.ExifInterface(it).latLong } } catch (_: Throwable) { null }
                    if (ll != null) break
                }
                val k = p.uri.toString()
                meta[k] = get(k).copy(place = ll?.takeIf { !(it[0] == 0.0 && it[1] == 0.0) }?.toList() ?: emptyList())
                scanning = n + 1 to todo.size
                if (n % 50 == 49) save(c)
            }
        } finally { scanning = null; save(c) }
    }

    /** Groups photos taken within [gapMs] of each other (bursts) — newest first, as [list] is. */
    fun stacks(list: List<LibPhoto>, gapMs: Long = 2500): List<List<LibPhoto>> {
        val out = ArrayList<MutableList<LibPhoto>>()
        for (p in list) {
            val g = out.lastOrNull()
            if (g != null && kotlin.math.abs(g.last().date - p.date) <= gapMs) g += p else out += mutableListOf(p)
        }
        return out
    }

    /** Camera details for the info panel. */
    fun exif(c: Context, p: LibPhoto): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        out += "File" to p.name
        out += "Size" to "${p.w} × ${p.h} · ${"%.1f".format(p.size / 1e6)} MB"
        out += "Date" to java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(p.date))
        try {
            c.contentResolver.openInputStream(p.uri)?.use { s ->
                val e = androidx.exifinterface.media.ExifInterface(s)
                fun t(tag: String) = e.getAttribute(tag)?.takeIf { it.isNotBlank() }
                listOfNotNull(t(androidx.exifinterface.media.ExifInterface.TAG_MAKE), t(androidx.exifinterface.media.ExifInterface.TAG_MODEL)).joinToString(" ").takeIf { it.isNotBlank() }?.let { out += "Camera" to it }
                t(androidx.exifinterface.media.ExifInterface.TAG_LENS_MODEL)?.let { out += "Lens" to it }
                val fl = e.getAttributeDouble(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                val f35 = e.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
                if (fl > 0) out += "Focal length" to ("%.1f mm".format(fl) + if (f35 > 0) " ($f35 mm equiv.)" else "")
                val fn = e.getAttributeDouble(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER, 0.0)
                if (fn > 0) out += "Aperture" to "f/%.1f".format(fn)
                val ex = e.getAttributeDouble(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                if (ex > 0) out += "Shutter" to (if (ex < 1) "1/${Math.round(1 / ex)} s" else "%.1f s".format(ex))
                t(androidx.exifinterface.media.ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { out += "ISO" to it }
                e.latLong?.let { out += "Location" to "%.5f, %.5f".format(it[0], it[1]) }
                t(androidx.exifinterface.media.ExifInterface.TAG_ARTIST)?.let { out += "Artist" to it }
                t(androidx.exifinterface.media.ExifInterface.TAG_COPYRIGHT)?.let { out += "Copyright" to it }
            }
        } catch (_: Throwable) {}
        return out
    }
}
