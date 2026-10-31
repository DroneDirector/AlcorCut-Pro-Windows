package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reading photos upright and saving results (Windows). */
object GradeIO {
    /** Upright decode, at most [maxPx] on the long side (0 = full size). */
    fun decode(context: Context, uri: Uri, maxPx: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        if (maxPx > 0) while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        var bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        if (bmp != null && maxPx > 0 && maxOf(bmp.width, bmp.height) > maxPx) {
            val f = maxPx.toFloat() / maxOf(bmp.width, bmp.height)
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true)
        }
        val o = context.contentResolver.openInputStream(uri)?.use { Exif.orientation(it) } ?: 1
        if (bmp != null && o != 1) Exif.upright(bmp, o) else bmp
    } catch (_: Throwable) { null }

    /** A video frame (the video engine comes with the Windows video editor). */
    fun videoFrame(context: Context, uri: Uri, timeUs: Long, maxPx: Int): Bitmap? = null

    /** The graded photo as a new JPEG next to the others. */
    fun savePhoto(context: Context, uri: Uri, grade: Grade, area: StorageArea = StorageArea.CAPTURE): Uri? {
        val src = decode(context, uri, 0) ?: return null
        var out = GradeLut(grade).apply(src)
        if (grade.hasDetail) { val d = GradeDetail.apply(out, grade); if (d !== out) out = d }
        val values = android.content.ContentValues().apply { put("_display_name", gradedName("PRO", "jpg")); put("mime_type", "image/jpeg") }
        val target = Storage.insert(context, Storage.load(context, area), values, Storage.Kind.IMAGE) ?: return null
        return try {
            context.contentResolver.openOutputStream(target)?.use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            target
        } catch (_: Throwable) { Storage.discard(context, target); null }
    }

    fun gradedName(prefix: String, ext: String) =
        prefix + "_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "." + ext

    /** A finished video file moved into the save folder. */
    fun publishVideo(context: Context, file: File, area: StorageArea = StorageArea.CAPTURE): Uri? = try {
        val f = Storage.newFile(context, area, Storage.Kind.VIDEO, gradedName("PRO", "mp4"))
        if (!file.renameTo(f)) { file.copyTo(f, true); file.delete() }
        Uri.fromFile(f)
    } catch (_: Throwable) { null }
}

/** Reads the EXIF orientation of a JPEG (own code). */
object Exif {
    fun orientation(inp: java.io.InputStream): Int = try {
        val d = java.io.DataInputStream(java.io.BufferedInputStream(inp))
        if (d.readUnsignedShort() != 0xFFD8) 1 else {
            var res = 1
            while (true) {
                val marker = d.readUnsignedShort()
                if (marker and 0xFF00 != 0xFF00 || marker == 0xFFDA) break
                val len = d.readUnsignedShort() - 2
                if (marker == 0xFFE1 && len > 14) {
                    val b = ByteArray(len); d.readFully(b)
                    if (String(b, 0, 4, Charsets.ISO_8859_1) == "Exif") { res = tiffOrientation(b, 6); break }
                } else d.skipBytes(len)
            }
            res
        }
    } catch (_: Throwable) { 1 }

    private fun tiffOrientation(b: ByteArray, o: Int): Int {
        val le = b[o] == 'I'.code.toByte()
        fun u16(i: Int) = if (le) (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) else ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
        fun u32(i: Int) = if (le) u16(i) or (u16(i + 2) shl 16) else (u16(i) shl 16) or u16(i + 2)
        val ifd = o + u32(o + 4)
        val n = u16(ifd)
        for (k in 0 until n) {
            val e = ifd + 2 + k * 12
            if (u16(e) == 0x0112) return u16(e + 8).coerceIn(1, 8)
        }
        return 1
    }

    /** Turns/flips [b] so EXIF orientation [o] shows upright. */
    fun upright(b: Bitmap, o: Int): Bitmap {
        val m = Matrix()
        when (o) {
            2 -> m.setScale(-1f, 1f)
            3 -> m.setRotate(180f)
            4 -> m.setScale(1f, -1f)
            5 -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            6 -> m.setRotate(90f)
            7 -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            8 -> m.setRotate(-90f)
            else -> return b
        }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }
}
