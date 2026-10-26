package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Recent photos and videos on the PC: the AlcorCut Pro folders and the top of Pictures / Videos,
 * newest first (the phone lists its gallery here).
 */
object MediaLibrary {
    const val FOLDER = "Pictures/AlcorCut Pro"
    private val imageExt = setOf("jpg", "jpeg", "png", "tif", "tiff", "bmp", "gif", "webp", "heic", "heif", "dng", "avif")
    private val videoExt = setOf("mp4", "mov", "m4v", "mkv", "webm", "avi", "mts", "m2ts")

    fun query(context: Context): List<MediaItem> {
        val roots = listOf(
            Storage.folder(context, StorageArea.PHOTO, Storage.Kind.IMAGE), Storage.folder(context, StorageArea.VIDEO, Storage.Kind.VIDEO),
            Context.picturesDir(), Context.videosDir(), File(System.getProperty("user.home"), "Desktop"), File(System.getProperty("user.home"), "Downloads"),
        ).distinct()
        val out = ArrayList<MediaItem>()
        val seen = HashSet<String>()
        for (r in roots) {
            val files = (r.listFiles() ?: emptyArray()).flatMap { if (it.isDirectory) (it.listFiles()?.toList() ?: emptyList()) else listOf(it) }
            for (f in files) {
                if (!f.isFile || !seen.add(f.path)) continue
                val ext = f.extension.lowercase()
                val video = ext in videoExt
                if (!video && ext !in imageExt && ext !in com.dronedirector.veyrocutpro.editor.RawDng.OTHER_RAW_EXTENSIONS) continue
                out += MediaItem(Uri.fromFile(f), video, f.name, f.lastModified(), f.length(), 0, 0, 0L, if (video) "video/$ext" else "image/$ext")
            }
        }
        return out.sortedByDescending { it.dateTaken }.take(500)
    }

    fun queryTrash(context: Context): List<MediaItem> = emptyList()
    fun setTrashed(context: Context, uris: List<Uri>, trashed: Boolean): Boolean = false
}
