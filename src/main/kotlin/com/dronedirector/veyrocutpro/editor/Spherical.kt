package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * 360° video: finds out whether a clip is a 360° (equirectangular) video, and marks an exported MP4 as 360°
 * (Google Spherical Video V1 metadata in the video track), so players, YouTube and Facebook show it as a sphere.
 * Own code.
 */
object Spherical {
    private val MARKERS = listOf("sv3d", "GSpherical", "equirectangular").map { it.toByteArray(Charsets.US_ASCII) }

    /** True when the file carries 360° metadata (looked for at its start and end, where the MP4 header is). */
    fun is360(c: Context, uri: Uri): Boolean = try {
        c.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            java.io.FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                val size = ch.size()
                val win = 4L * 1024 * 1024
                fun scan(from: Long): Boolean {
                    val n = minOf(win, size - from).toInt().coerceAtLeast(0)
                    if (n <= 0) return false
                    val bb = ByteBuffer.allocate(n); ch.read(bb, from)
                    val a = bb.array()
                    return MARKERS.any { m -> indexOf(a, m) >= 0 }
                }
                scan(0L) || (size > win && scan(size - win))
            }
        } ?: false
    } catch (_: Throwable) { false }

    private fun indexOf(a: ByteArray, m: ByteArray): Int {
        outer@ for (i in 0..a.size - m.size) { for (j in m.indices) if (a[i + j] != m[j]) continue@outer; return i }
        return -1
    }

    private val UUID = byteArrayOf(0xff.toByte(), 0xcc.toByte(), 0x82.toByte(), 0x63, 0xf8.toByte(), 0x55, 0x4a, 0x93.toByte(), 0x88.toByte(), 0x14, 0x58, 0x7a, 0x02, 0x52, 0x1f, 0xdd.toByte())
    private const val XML = "<?xml version=\"1.0\"?><rdf:SphericalVideo xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" " +
        "xmlns:GSpherical=\"http://ns.google.com/videos/1.0/spherical/\"><GSpherical:Spherical>true</GSpherical:Spherical>" +
        "<GSpherical:Stitched>true</GSpherical:Stitched><GSpherical:StitchingSoftware>AlcorCut Pro</GSpherical:StitchingSoftware>" +
        "<GSpherical:ProjectionType>equirectangular</GSpherical:ProjectionType></rdf:SphericalVideo>"

    private fun type(b: ByteArray, at: Int) = String(b, at + 4, 4, Charsets.US_ASCII)
    private fun u32(b: ByteArray, at: Int) = ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)
    private fun put32(b: ByteArray, at: Int, v: Long) { b[at] = (v shr 24).toByte(); b[at + 1] = (v shr 16).toByte(); b[at + 2] = (v shr 8).toByte(); b[at + 3] = v.toByte() }

    /** Child boxes (offset, size) of the box at [start] with header [head], inside [b]. */
    private fun children(b: ByteArray, start: Int, end: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var p = start
        while (p + 8 <= end) {
            val s = u32(b, p).toInt()
            if (s < 8 || p + s > end) break
            out += p to s
            p += s
        }
        return out
    }

    /** Adds [delta] to every chunk offset (stco / co64) under the box at [at]. */
    private fun shiftOffsets(b: ByteArray, at: Int, size: Int, delta: Long) {
        for ((p, s) in children(b, at + 8, at + size)) {
            when (type(b, p)) {
                "trak", "mdia", "minf", "stbl", "edts" -> shiftOffsets(b, p, s, delta)
                "stco" -> { val n = u32(b, p + 12).toInt(); for (i in 0 until n) { val q = p + 16 + i * 4; put32(b, q, u32(b, q) + delta) } }
                "co64" -> {
                    val n = u32(b, p + 12).toInt()
                    for (i in 0 until n) { val q = p + 16 + i * 8; val v = (u32(b, q) shl 32) or u32(b, q + 4); val nv = v + delta; put32(b, q, nv ushr 32); put32(b, q + 4, nv and 0xFFFFFFFFL) }
                }
            }
        }
    }

    private fun isVideoTrak(b: ByteArray, at: Int, size: Int): Boolean {
        val mdia = children(b, at + 8, at + size).firstOrNull { type(b, it.first) == "mdia" } ?: return false
        val hdlr = children(b, mdia.first + 8, mdia.first + mdia.second).firstOrNull { type(b, it.first) == "hdlr" } ?: return false
        return String(b, hdlr.first + 16, 4, Charsets.US_ASCII) == "vide"
    }

    /** Marks the MP4 [f] as a 360° equirectangular video. Returns false (file unchanged) when it can't. */
    fun tag(f: File): Boolean = try {
        RandomAccessFile(f, "rw").use { raf ->
            val len = raf.length()
            // Top-level boxes: find moov and whether it comes before mdat.
            var p = 0L; var moovAt = -1L; var moovSize = 0L; var mdatAt = -1L
            val h = ByteArray(16)
            while (p + 8 <= len) {
                raf.seek(p); raf.readFully(h, 0, 8)
                var s = u32(h, 0); val t = String(h, 4, 4, Charsets.US_ASCII)
                if (s == 1L) { raf.readFully(h, 8, 8); s = (u32(h, 8) shl 32) or u32(h, 12) } else if (s == 0L) s = len - p
                if (s < 8) return false
                if (t == "moov") { moovAt = p; moovSize = s }
                if (t == "mdat" && mdatAt < 0) mdatAt = p
                p += s
            }
            if (moovAt < 0 || moovSize > 64L * 1024 * 1024) return false
            val moov = ByteArray(moovSize.toInt()); raf.seek(moovAt); raf.readFully(moov)
            val trak = children(moov, 8, moov.size).firstOrNull { type(moov, it.first) == "trak" && isVideoTrak(moov, it.first, it.second) } ?: return false
            // Already marked?
            if (children(moov, trak.first + 8, trak.first + trak.second).any { type(moov, it.first) == "uuid" && moov.copyOfRange(it.first + 8, it.first + 24).contentEquals(UUID) }) return true
            val xml = XML.toByteArray(Charsets.UTF_8)
            val box = ByteArray(8 + 16 + xml.size)
            put32(box, 0, box.size.toLong()); "uuid".toByteArray(Charsets.US_ASCII).copyInto(box, 4); UUID.copyInto(box, 8); xml.copyInto(box, 24)
            // New moov: the uuid box at the end of the video track, sizes updated.
            val end = trak.first + trak.second
            val nm = ByteArray(moov.size + box.size)
            moov.copyInto(nm, 0, 0, end); box.copyInto(nm, end); moov.copyInto(nm, end + box.size, end, moov.size)
            put32(nm, 0, nm.size.toLong()); put32(nm, trak.first, (trak.second + box.size).toLong())
            // moov before the media data: everything after it moves, so the chunk offsets do too.
            if (mdatAt > moovAt) shiftOffsets(nm, 0, nm.size, box.size.toLong())
            if (moovAt + moovSize == len) {
                // moov is last (the usual case for exports): rewrite it in place.
                raf.seek(moovAt); raf.write(nm); raf.setLength(moovAt + nm.size)
            } else {
                // Rewrite the file: before moov, new moov, after moov.
                val tmp = File(f.parentFile, f.name + ".360")
                RandomAccessFile(tmp, "rw").use { out ->
                    fun copy(from: Long, n: Long) {
                        val buf = ByteArray(1 shl 20); var left = n; raf.seek(from)
                        while (left > 0) { val k = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (k <= 0) break; out.write(buf, 0, k); left -= k }
                    }
                    copy(0, moovAt); out.write(nm); copy(moovAt + moovSize, len - moovAt - moovSize)
                }
                raf.close()
                if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
            }
            true
        }
    } catch (_: Throwable) { false }

    /** After an export: when the edit starts with a 360° clip and the output is 2:1, mark the file as 360°. */
    fun tagIfNeeded(c: Context, clips: List<Clip>, w: Int, h: Int, f: File) {
        val first = clips.firstOrNull() ?: return
        if (w <= 0 || h <= 0 || kotlin.math.abs(w.toFloat() / h - 2f) > 0.02f) return
        if (!is360(c, first.uri)) return
        tag(f)
    }
}
