package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.net.Uri
import android.system.Os
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.util.Calendar

/**
 * Adds a timecode track (QuickTime 'tmcd', time of day when recording started) to a finished MP4,
 * so editing programs line up clips from several cameras by time. Own code.
 *
 * The phone writes the movie header (moov) at the end of the file; the new track is added to it and
 * its one sample (the start frame number) is stored in a small box after it. Nothing else moves.
 */
object Timecode {
    private fun u32(b: ByteArray, at: Int) = ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)
    private fun u64(b: ByteArray, at: Int) = (u32(b, at) shl 32) or u32(b, at + 4)
    private fun put32(b: ByteArray, at: Int, v: Long) { b[at] = (v shr 24).toByte(); b[at + 1] = (v shr 16).toByte(); b[at + 2] = (v shr 8).toByte(); b[at + 3] = v.toByte() }
    private fun type(b: ByteArray, at: Int) = String(b, at + 4, 4, Charsets.ISO_8859_1)
    private fun children(b: ByteArray, start: Int, end: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>(); var p = start
        while (p + 8 <= end) { val s = u32(b, p).toInt(); if (s < 8 || p + s > end) break; out += p to s; p += s }
        return out
    }

    private class W {
        val o = ByteArrayOutputStream()
        fun i32(v: Long) = apply { o.write((v shr 24).toInt() and 0xFF); o.write((v shr 16).toInt() and 0xFF); o.write((v shr 8).toInt() and 0xFF); o.write(v.toInt() and 0xFF) }
        fun i16(v: Int) = apply { o.write((v shr 8) and 0xFF); o.write(v and 0xFF) }
        fun i8(v: Int) = apply { o.write(v and 0xFF) }
        fun zeros(n: Int) = apply { repeat(n) { o.write(0) } }
        fun str(s: String) = apply { o.write(s.toByteArray(Charsets.ISO_8859_1)) }
        fun bytes(b: ByteArray) = apply { o.write(b) }
        fun get(): ByteArray = o.toByteArray()
    }
    private fun box(t: String, vararg parts: ByteArray): ByteArray {
        val w = W().i32(8L + parts.sumOf { it.size }).str(t)
        parts.forEach { w.bytes(it) }
        return w.get()
    }
    private val matrix = W().i32(0x10000).i32(0).i32(0).i32(0).i32(0x10000).i32(0).i32(0).i32(0).i32(0x40000000).get()

    private fun readAt(fd: FileDescriptor, buf: ByteArray, pos: Long, n: Int = buf.size): Boolean {
        var done = 0
        while (done < n) { val k = Os.pread(fd, buf, done, n - done, pos + done); if (k <= 0) return false; done += k }
        return true
    }
    private fun writeAt(fd: FileDescriptor, buf: ByteArray, pos: Long) {
        var done = 0
        while (done < buf.size) { val k = Os.pwrite(fd, buf, done, buf.size - done, pos + done); if (k <= 0) throw java.io.IOException("write"); done += k }
    }

    /** Frame number since midnight for [wallMs] at [fps]. */
    fun frameOfDay(wallMs: Long, fps: Int): Long {
        val c = Calendar.getInstance().apply { timeInMillis = wallMs }
        val sec = c.get(Calendar.HOUR_OF_DAY) * 3600L + c.get(Calendar.MINUTE) * 60L + c.get(Calendar.SECOND)
        return sec * fps + (c.get(Calendar.MILLISECOND) * fps / 1000L)
    }

    /** "hh:mm:ss:ff" for [wallMs] at [fps]. */
    fun label(wallMs: Long, fps: Int): String {
        val f = frameOfDay(wallMs, fps); val s = f / fps
        return "%02d:%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60, f % fps)
    }

    /** Adds the timecode track to the video at [uri]. Returns false (file unchanged) when it can't. */
    fun add(context: Context, uri: Uri, startWallMs: Long, fpsIn: Int): Boolean = try {
        val fps = fpsIn.coerceIn(1, 120)
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd -> addTo(pfd.fileDescriptor, startWallMs, fps) } ?: false
    } catch (_: Throwable) { false }

    private fun addTo(fd: FileDescriptor, startWallMs: Long, fps: Int): Boolean {
        val len = Os.fstat(fd).st_size
        var p = 0L; var moovAt = -1L; var moovSize = 0L
        val h = ByteArray(16)
        while (p + 8 <= len) {
            if (!readAt(fd, h, p, 8)) return false
            var s = u32(h, 0); val t = String(h, 4, 4, Charsets.ISO_8859_1)
            if (s == 1L) { if (!readAt(fd, h, p, 16)) return false; s = u64(h, 8) } else if (s == 0L) s = len - p
            if (s < 8) return false
            if (t == "moov") { moovAt = p; moovSize = s }
            p += s
        }
        // Only a header at the very end can grow without moving the picture data.
        if (moovAt < 0 || moovAt + moovSize != len || moovSize > 64L * 1024 * 1024) return false
        val moov = ByteArray(moovSize.toInt())
        if (!readAt(fd, moov, moovAt)) return false
        val top = children(moov, 8, moov.size)
        val mvhd = top.firstOrNull { type(moov, it.first) == "mvhd" } ?: return false
        val traks = top.filter { type(moov, it.first) == "trak" }
        // Already has one?
        fun handler(trak: Pair<Int, Int>): String? {
            val mdia = children(moov, trak.first + 8, trak.first + trak.second).firstOrNull { type(moov, it.first) == "mdia" } ?: return null
            val hdlr = children(moov, mdia.first + 8, mdia.first + mdia.second).firstOrNull { type(moov, it.first) == "hdlr" } ?: return null
            return String(moov, hdlr.first + 16, 4, Charsets.ISO_8859_1)
        }
        if (traks.any { handler(it) == "tmcd" }) return true
        val video = traks.firstOrNull { handler(it) == "vide" } ?: return false

        val mv = mvhd.first
        val v1 = moov[mv + 8].toInt() == 1
        val movieScale = u32(moov, mv + if (v1) 28 else 20)
        val movieDur = if (v1) u64(moov, mv + 32) else u32(moov, mv + 24)
        val nextIdAt = mv + if (v1) 116 else 104
        val trackId = u32(moov, nextIdAt).takeIf { it in 1..0xFFFF } ?: (traks.size + 1L)
        put32(moov, nextIdAt, trackId + 1)

        val scale = fps * 1000L
        val durMedia = if (movieScale > 0) movieDur * scale / movieScale else 0L
        val durMovie = movieDur.coerceAtMost(0xFFFFFFFFL)
        val tkhd = box("tkhd", W().i32(0x0000000F).i32(0).i32(0).i32(trackId).i32(0).i32(durMovie).zeros(8)
            .i16(0).i16(0).i16(0).i16(0).bytes(matrix).i32(0).i32(0).get())
        val mdhd = box("mdhd", W().i32(0).i32(0).i32(0).i32(scale).i32(durMedia.coerceAtMost(0xFFFFFFFFL)).i16(0x55C4).i16(0).get())
        val hdlr = box("hdlr", W().i32(0).str("mhlr").str("tmcd").zeros(12).i8(15).str("TimeCodeHandler").get())
        val tcmi = box("tcmi", W().i32(0).i16(0).i16(0).i16(12).i16(0).zeros(6).zeros(6).i8(9).str("Helvetica").get())
        val gmhd = box("gmhd", box("gmin", W().i32(0).i16(0x40).i16(0x8000).i16(0x8000).i16(0x8000).i16(0).i16(0).get()), box("tmcd", tcmi))
        val dinf = box("dinf", box("dref", W().i32(0).i32(1).get(), box("url ", W().i32(1).get())))
        val entry = box("tmcd", W().zeros(6).i16(1).i32(0).i32(0).i32(scale).i32(1000).i8(fps).i8(0).get())
        val stsd = box("stsd", W().i32(0).i32(1).get(), entry)
        val stts = box("stts", W().i32(0).i32(1).i32(1).i32(durMedia.coerceIn(1, 0xFFFFFFFFL)).get())
        val stsc = box("stsc", W().i32(0).i32(1).i32(1).i32(1).i32(1).get())
        val stsz = box("stsz", W().i32(0).i32(4).i32(1).get())
        // The sample sits in a 'free' box straight after the new header: offset filled in below.
        val stco = box("stco", W().i32(0).i32(1).i32(0).get())
        val stbl = box("stbl", stsd, stts, stsc, stsz, stco)
        val minf = box("minf", gmhd, dinf, stbl)
        val trak = box("trak", tkhd, box("mdia", mdhd, hdlr, minf))
        // The video track points at its timecode.
        val tref = box("tref", box("tmcd", W().i32(trackId).get()))

        val vEnd = video.first + video.second
        val vTkhd = children(moov, video.first + 8, vEnd).firstOrNull { type(moov, it.first) == "tkhd" } ?: return false
        val insertAt = vTkhd.first + vTkhd.second
        val out = W()
        out.bytes(moov.copyOfRange(0, insertAt)).bytes(tref).bytes(moov.copyOfRange(insertAt, moov.size)).bytes(trak)
        val nm = out.get()
        put32(nm, 0, nm.size.toLong())
        put32(nm, video.first, (video.second + tref.size).toLong())
        // stco value: last 4 bytes of the stco box, which ends 'trak' -> 'mdia' -> 'minf' -> 'stbl'.
        val sampleAt = moovAt + nm.size + 8
        if (sampleAt > 0xFFFFFFFFL) return false
        put32(nm, nm.size - 4, sampleAt)
        val free = W().i32(12).str("free").i32(frameOfDay(startWallMs, fps)).get()
        writeAt(fd, nm + free, moovAt)
        return true
    }
}
