package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Slate & shot log (Settings → SLATE & SHOT LOG): scene / shot / take and notes for each clip and
 * photo, shown on the viewfinder, written into photos (EXIF description) and kept in a log you can
 * export as a spreadsheet (.csv). The take number goes up by itself after each recording.
 */
object Slate {
    private fun logFile(c: Context) = File(c.filesDir, "shotlog.csv")
    private const val HEADER = "Date,Time,File,Type,Scene,Shot,Take,Notes,Mode,Resolution,FPS,ISO,Shutter,White balance,Lens\n"

    fun label(): String {
        val x = CaptureExtras
        return buildList {
            if (x.slateScene.isNotBlank()) add("SC ${x.slateScene.trim()}")
            if (x.slateShot.isNotBlank()) add("SH ${x.slateShot.trim()}")
            add("TK ${x.slateTake}")
        }.joinToString(" · ")
    }

    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun nameOf(c: Context, uri: Uri): String = try {
        c.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cur ->
            if (cur.moveToFirst()) cur.getString(0) else null
        } ?: uri.lastPathSegment ?: ""
    } catch (_: Throwable) { uri.lastPathSegment ?: "" }

    /** Adds a line for a finished clip or photo; after a video the take number goes up. */
    fun log(c: Context, uri: Uri, s: CameraUiState, video: Boolean) {
        val x = CaptureExtras
        if (!x.slateOn) return
        try {
            val f = logFile(c)
            if (!f.exists()) f.writeText(HEADER)
            val now = java.util.Date()
            val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(now)
            val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(now)
            val shutter = if (s.manualShutter) "1/${(1_000_000_000L / s.shutterNs.coerceAtLeast(1))}" else "auto"
            val line = listOf(
                d, t, nameOf(c, uri), if (video) "Video" else "Photo", x.slateScene, x.slateShot, x.slateTake.toString(), x.slateNotes,
                s.mode.label, if (video) s.videoRes.label else (s.photoSize?.label ?: ""), if (video) s.fps.toString() else "",
                if (s.manualIso) s.iso.toString() else "auto", shutter, s.whiteBalance.label, s.lens?.label ?: "",
            ).joinToString(",") { csv(it) }
            f.appendText(line + "\n")
        } catch (_: Throwable) {}
        if (video && x.slateAutoTake) { x.slateTake = (x.slateTake + 1).coerceAtMost(999); x.save(c) }
    }

    /** Scene / shot / take and notes in the photo's description (EXIF). Call off the main thread. */
    fun stampPhoto(c: Context, uri: Uri) {
        val x = CaptureExtras
        if (!x.slateOn) return
        try {
            c.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                val e = androidx.exifinterface.media.ExifInterface(pfd.fileDescriptor)
                e.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_DESCRIPTION, label() + if (x.slateNotes.isNotBlank()) " — ${x.slateNotes.trim()}" else "")
                e.saveAttributes()
            }
        } catch (_: Throwable) {}
    }

    fun export(c: Context, out: Uri): Boolean = try {
        val f = logFile(c)
        c.contentResolver.openOutputStream(out, "wt")?.use { o -> if (f.exists()) f.inputStream().use { it.copyTo(o) } else o.write(HEADER.toByteArray()) }
        true
    } catch (_: Throwable) { false }

    fun count(c: Context): Int = try { (logFile(c).takeIf { it.exists() }?.readLines()?.size ?: 1) - 1 } catch (_: Throwable) { 0 }

    fun clear(c: Context) { logFile(c).delete() }
}
