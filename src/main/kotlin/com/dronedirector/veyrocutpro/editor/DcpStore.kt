package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Imported DNG camera profiles (.dcp), kept in the app's own storage. A profile is offered for the
 * RAW photos of the camera it was made for (its UniqueCameraModel), or for any photo when it names
 * no camera. Own code; the .dcp format is the DNG specification's camera-profile file.
 */
object DcpStore {
    class Entry(val id: String, val name: String, val camera: String)

    private val cache = HashMap<String, RawDng.Dcp>()
    private fun dir(c: Context) = File(c.filesDir, "dcp").apply { mkdirs() }

    /** Copies a .dcp into the app; returns its entry, or throws with a reason. */
    fun import(c: Context, uri: Uri): Entry {
        val d = RawDng.readDcp(c, uri)
        val id = "dcp_" + System.currentTimeMillis()
        val f = File(dir(c), "$id.dcp")
        c.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } } ?: throw RawDng.UnsupportedRaw("Couldn't read the file")
        File(dir(c), "$id.txt").writeText(d.name.replace('\n', ' ') + "\n" + d.camera.replace('\n', ' '))
        synchronized(cache) { cache[id] = d }
        return Entry(id, d.name, d.camera)
    }

    fun list(c: Context): List<Entry> = dir(c).listFiles { f -> f.name.endsWith(".txt") }.orEmpty().sortedBy { it.name }.mapNotNull { f ->
        val l = try { f.readLines() } catch (_: Throwable) { return@mapNotNull null }
        Entry(f.name.removeSuffix(".txt"), l.getOrElse(0) { "Camera profile" }, l.getOrElse(1) { "" })
    }

    fun delete(c: Context, id: String) {
        File(dir(c), "$id.dcp").delete(); File(dir(c), "$id.txt").delete()
        synchronized(cache) { cache.remove(id) }
    }

    fun get(c: Context, id: String): RawDng.Dcp? {
        synchronized(cache) { cache[id]?.let { return it } }
        val f = File(dir(c), "$id.dcp")
        if (!f.exists()) return null
        val d = try { RawDng.readDcp(c, Uri.fromFile(f)) } catch (_: Throwable) { return null }
        synchronized(cache) { cache[id] = d }
        return d
    }

    /** Profiles that fit a photo from [camera] (the RAW's camera name); profiles without a camera fit all. */
    fun forCamera(c: Context, camera: String?): List<Entry> {
        val cam = camera?.lowercase()?.replace(Regex("[^a-z0-9]"), "") ?: ""
        return list(c).filter { e ->
            val k = e.camera.lowercase().replace(Regex("[^a-z0-9]"), "")
            k.isEmpty() || cam.isEmpty() || cam.contains(k) || k.contains(cam)
        }
    }
}
