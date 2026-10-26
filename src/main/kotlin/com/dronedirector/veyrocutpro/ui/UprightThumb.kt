package com.dronedirector.veyrocutpro.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface

/**
 * The phone's thumbnails (and Android's decoder) turn JPEGs by their orientation tag but not DNG RAW
 * files, so an upright DNG shows sideways. This turns a DNG picture by its own tag; anything else is
 * returned unchanged.
 */
object UprightThumb {
    fun isDng(c: Context, uri: Uri): Boolean = try {
        val t = c.contentResolver.getType(uri) ?: ""
        if (t.contains("dng", true)) true else {
            val name = c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
                ?: uri.lastPathSegment ?: ""
            name.endsWith(".dng", true)
        }
    } catch (_: Throwable) { false }

    fun fix(c: Context, uri: Uri, b: Bitmap?): Bitmap? {
        if (b == null || !isDng(c, uri)) return b
        val rot = try { c.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0 } catch (_: Throwable) { 0 }
        return if (rot == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
    }
}
