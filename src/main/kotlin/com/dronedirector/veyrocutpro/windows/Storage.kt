package com.dronedirector.veyrocutpro.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import java.io.File

/** Which part of the app is saving: each can have its own folder (the editors follow capture by default). */
enum class StorageArea(val key: String, val label: String) {
    CAPTURE("storage_volume", "Capture"),
    VIDEO("storage_video", "Video editor"),
    PHOTO("storage_photo", "Photo editor"),
}

/** A place to save (a drive). */
data class StorageOption(val volume: String, val label: String, val removable: Boolean)

/**
 * Where AlcorCut Pro saves on the PC: Pictures\AlcorCut Pro and Videos\AlcorCut Pro by default, or a
 * folder you choose (any drive). If the chosen folder is gone (drive unplugged), saving falls back
 * to the default folder. A "place" is the folder's path.
 */
object Storage {
    private const val PREFS = "procam"
    const val INTERNAL = "external_primary"
    const val SAME = "same"
    enum class Kind { IMAGE, VIDEO, AUDIO }

    /** A place's name for the screen. */
    fun label(context: Context, place: String): String = when (place) {
        SAME -> "Same as capture"
        INTERNAL -> "This PC"
        else -> File(place).let { f -> f.name.ifBlank { f.path } }
    }

    fun folder(context: Context, area: StorageArea, kind: Kind): File {
        chosen(context, area)?.let { File(it) }?.takeIf { it.isDirectory && it.canWrite() }?.let { return it }
        val base = if (kind == Kind.IMAGE) Context.picturesDir() else Context.videosDir()
        return File(base, "AlcorCut Pro").apply { mkdirs() }
    }

    fun choose(context: Context, area: StorageArea, dir: File?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(area.key + "_dir", dir?.path).apply()
    }

    fun chosen(context: Context, area: StorageArea): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(area.key + "_dir", null)

    /** A new file [name] in the area's folder (never overwriting). */
    fun newFile(context: Context, area: StorageArea, kind: Kind, name: String): File = unique(folder(context, area, kind), name)

    private fun unique(dir: File, name: String): File {
        dir.mkdirs()
        var f = File(dir, name)
        var i = 1
        val stem = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        while (f.exists()) { f = File(dir, "$stem ($i)$ext"); i++ }
        return f
    }

    // Same calls as the phone's storage code.

    fun options(context: Context): List<StorageOption> = File.listRoots().filter { it.canWrite() }.map { StorageOption(it.path, it.path, false) }
    fun choice(context: Context, area: StorageArea): String = chosen(context, area) ?: INTERNAL
    fun load(context: Context, area: StorageArea = StorageArea.CAPTURE): String = chosen(context, area) ?: INTERNAL
    fun isTree(place: String) = false
    fun trees(context: Context): List<Uri> = emptyList()
    fun resolve(context: Context, place: String): String = place
    fun createInTree(context: Context, place: String, mime: String, name: String): Uri? = null

    /** A new, empty file for [values] (name and type) in [place] (or the default folder). */
    fun insert(context: Context, place: String, values: ContentValues, kind: Kind): Uri? = try {
        val name = values.getAsString("_display_name") ?: "AlcorCut_${System.currentTimeMillis()}"
        val dir = place.takeIf { it != INTERNAL && it != SAME }?.let { File(it) }?.takeIf { it.isDirectory && it.canWrite() }
            ?: File(if (kind == Kind.IMAGE) Context.picturesDir() else Context.videosDir(), "AlcorCut Pro")
        unique(dir, name).let { it.createNewFile(); Uri.fromFile(it) }
    } catch (_: Throwable) { null }

    fun publish(context: Context, uri: Uri) {}
    fun discard(context: Context, uri: Uri): Boolean = try { uri.toFile()?.delete() == true } catch (_: Throwable) { false }
}
