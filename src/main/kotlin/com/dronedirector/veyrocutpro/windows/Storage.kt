package com.dronedirector.veyrocutpro.camera

import android.content.Context
import java.io.File

/** Which part of the app is saving: each can have its own folder (the editors follow capture by default). */
enum class StorageArea(val key: String, val label: String) {
    CAPTURE("storage_volume", "Capture"),
    VIDEO("storage_video", "Video editor"),
    PHOTO("storage_photo", "Photo editor"),
}

/**
 * Where AlcorCut Pro saves on the PC: Pictures\AlcorCut Pro and Videos\AlcorCut Pro by default, or a
 * folder you choose (any drive). If the chosen folder is gone (drive unplugged), saving falls back
 * to the default folder.
 */
object Storage {
    private const val PREFS = "procam"
    enum class Kind { IMAGE, VIDEO, AUDIO }

    fun folder(context: Context, area: StorageArea, kind: Kind): File {
        val chosen = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(area.key + "_dir", null)
        chosen?.let { File(it) }?.takeIf { it.isDirectory && it.canWrite() }?.let { return it }
        val base = if (kind == Kind.IMAGE) Context.picturesDir() else Context.videosDir()
        return File(base, "AlcorCut Pro").apply { mkdirs() }
    }

    fun choose(context: Context, area: StorageArea, dir: File?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(area.key + "_dir", dir?.path).apply()
    }

    fun chosen(context: Context, area: StorageArea): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(area.key + "_dir", null)

    /** A new file [name] in the area's folder (never overwriting). */
    fun newFile(context: Context, area: StorageArea, kind: Kind, name: String): File {
        val dir = folder(context, area, kind)
        var f = File(dir, name)
        var i = 1
        val stem = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        while (f.exists()) { f = File(dir, "$stem ($i)$ext"); i++ }
        return f
    }
}
