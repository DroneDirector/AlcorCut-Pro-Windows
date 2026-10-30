package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import android.provider.MediaStore

/** A video in the video library grid. */
data class LibVideo(val uri: Uri, val name: String, val date: Long, val w: Int, val h: Int, val size: Long, val durationMs: Long)

/**
 * The video library: every video on the device with stars, flags, colour labels, keywords, albums, smart
 * collections, metadata presets and a calendar — kept in the app's files, the videos are never changed.
 */
object VideoLibrary {
    val meta = mutableStateMapOf<String, LibMeta>()
    val albums = mutableStateListOf<String>()
    val smart = mutableStateListOf<SmartAlbum>()
    private var loaded = false
    private data class Saved(val meta: Map<String, LibMeta>? = null, val albums: List<String>? = null, val smart: List<SmartAlbum>? = null)
    private fun file(c: Context) = java.io.File(c.filesDir, "video_library.json")

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(c); if (!f.exists()) return
            val s: Saved = ProjectStore.gson.fromJson(f.readText(), Saved::class.java) ?: return
            s.meta?.forEach { (k, v) -> meta[k] = v.copy(keywords = v.keywords ?: emptyList(), albums = v.albums ?: emptyList()) }
            s.albums?.let { albums.addAll(it) }
            s.smart?.let { smart.addAll(it) }
        } catch (_: Throwable) {}
    }
    fun save(c: Context) { try { file(c).writeText(ProjectStore.gson.toJson(Saved(meta.toMap(), albums.toList(), smart.toList()))) } catch (_: Throwable) {} }
    fun get(uri: String) = meta[uri] ?: LibMeta()
    fun update(c: Context, uris: Collection<String>, f: (LibMeta) -> LibMeta) { load(c); for (u in uris) meta[u] = f(get(u)); save(c) }

    val TYPES = listOf("Any", "4K and up", "HD and smaller", "Drone", "Vertical")

    /** Every video on the device (needs the video permission), newest first. */
    fun videos(c: Context): List<LibVideo> {
        val out = ArrayList<LibVideo>()
        val col = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT, MediaStore.MediaColumns.SIZE, MediaStore.Video.VideoColumns.DURATION) + (if (Build.VERSION.SDK_INT >= 29) arrayOf(MediaStore.MediaColumns.DATE_TAKEN) else emptyArray())
        try {
            c.contentResolver.query(col, proj, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cur ->
                val iTaken = if (Build.VERSION.SDK_INT >= 29) cur.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN) else -1
                while (cur.moveToNext()) {
                    val taken = if (iTaken >= 0 && !cur.isNull(iTaken)) cur.getLong(iTaken) else cur.getLong(2) * 1000
                    out += LibVideo(android.content.ContentUris.withAppendedId(col, cur.getLong(0)), cur.getString(1) ?: "", taken, cur.getInt(3), cur.getInt(4), cur.getLong(5), cur.getLong(6))
                }
            }
        } catch (_: Throwable) {}
        return out
    }

    fun matches(v: LibVideo, text: String): Boolean {
        if (text.isBlank()) return true
        val m = get(v.uri.toString())
        val hay = (listOf(v.name) + m.keywords + m.albums + listOfNotNull(m.meta?.caption, m.meta?.title)).joinToString(" ").lowercase()
        return text.lowercase().split(' ', ',').filter { it.isNotBlank() }.all { hay.contains(it) }
    }

    fun matchesSmart(v: LibVideo, s: SmartAlbum): Boolean {
        val m = get(v.uri.toString())
        if (m.rating < s.minRating) return false
        if (s.flag == 1 && m.flag != 1) return false
        if (s.flag == 2 && m.flag == -1) return false
        if (s.label != null && m.label != s.label) return false
        s.keyword?.takeIf { it.isNotBlank() }?.let { k -> if (m.keywords.none { it.equals(k.trim(), ignoreCase = true) }) return false }
        smartDateRange(s.dateMode)?.let { r -> if (v.date !in r) return false }
        val long = maxOf(v.w, v.h); val short = minOf(v.w, v.h)
        when (s.fileType) {
            1 -> if (short < 2000 && long < 3800) return false
            2 -> if (long >= 3800 || short >= 2000) return false
            3 -> if (!v.name.startsWith("DJI_", ignoreCase = true)) return false
            4 -> if (v.h <= v.w) return false
        }
        return matches(v, s.text)
    }
}
