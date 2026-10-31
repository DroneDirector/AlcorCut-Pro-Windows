package com.dronedirector.veyrocutpro.editor

import java.io.File
import java.io.RandomAccessFile

/**
 * Writes your details (title, author, copyright, description, keywords) into an exported MP4 as standard
 * moov/udta/meta/ilst tags (read by most players and by photo / video libraries). Own code.
 */
object Mp4Meta {
    private fun u32(b: ByteArray, at: Int) = ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)
    private fun put32(b: ByteArray, at: Int, v: Long) { b[at] = (v shr 24).toByte(); b[at + 1] = (v shr 16).toByte(); b[at + 2] = (v shr 8).toByte(); b[at + 3] = v.toByte() }
    private fun type(b: ByteArray, at: Int) = String(b, at + 4, 4, Charsets.ISO_8859_1)

    private fun box(t: String, vararg parts: ByteArray): ByteArray {
        val n = 8 + parts.sumOf { it.size }
        val o = java.io.ByteArrayOutputStream(n)
        val h = ByteArray(8); put32(h, 0, n.toLong()); t.toByteArray(Charsets.ISO_8859_1).copyInto(h, 4)
        o.write(h); parts.forEach { o.write(it) }
        return o.toByteArray()
    }
    private fun text(t: String, v: String): ByteArray {
        val head = ByteArray(8); put32(head, 0, 1)   // type 1 = UTF-8, locale 0
        return box(t, box("data", head, v.toByteArray(Charsets.UTF_8)))
    }
    private fun children(b: ByteArray, start: Int, end: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>(); var p = start
        while (p + 8 <= end) { val s = u32(b, p).toInt(); if (s < 8 || p + s > end) break; out += p to s; p += s }
        return out
    }
    private fun shiftOffsets(b: ByteArray, at: Int, size: Int, delta: Long) {
        for ((p, s) in children(b, at + 8, at + size)) when (type(b, p)) {
            "trak", "mdia", "minf", "stbl", "edts" -> shiftOffsets(b, p, s, delta)
            "stco" -> { val n = u32(b, p + 12).toInt(); for (i in 0 until n) { val q = p + 16 + i * 4; put32(b, q, u32(b, q) + delta) } }
            "co64" -> { val n = u32(b, p + 12).toInt(); for (i in 0 until n) { val q = p + 16 + i * 8; val v = (u32(b, q) shl 32) or u32(b, q + 4); val nv = v + delta; put32(b, q, nv ushr 32); put32(b, q + 4, nv and 0xFFFFFFFFL) } }
        }
    }

    /** Adds the tags of [m] to [f]. Returns false (file unchanged) when it can't. */
    fun write(f: File, m: PhotoMetaEdit): Boolean = try {
        if (m.isEmpty) true else RandomAccessFile(f, "rw").use { raf ->
            val len = raf.length()
            var p = 0L; var moovAt = -1L; var moovSize = 0L; var mdatAt = -1L
            val h = ByteArray(16)
            while (p + 8 <= len) {
                raf.seek(p); raf.readFully(h, 0, 8)
                var s = u32(h, 0); val t = String(h, 4, 4, Charsets.ISO_8859_1)
                if (s == 1L) { raf.readFully(h, 8, 8); s = (u32(h, 8) shl 32) or u32(h, 12) } else if (s == 0L) s = len - p
                if (s < 8) return false
                if (t == "moov") { moovAt = p; moovSize = s }
                if (t == "mdat" && mdatAt < 0) mdatAt = p
                p += s
            }
            if (moovAt < 0 || moovSize > 64L * 1024 * 1024) return false
            val moov = ByteArray(moovSize.toInt()); raf.seek(moovAt); raf.readFully(moov)
            // The tags: ilst inside meta (with its 'mdir' handler) inside udta.
            val items = ArrayList<ByteArray>()
            if (m.title.isNotBlank()) items += text("©nam", m.title.trim())
            if (m.artist.isNotBlank()) items += text("©ART", m.artist.trim())
            if (m.copyright.isNotBlank()) items += text("cprt", m.copyright.trim())
            if (m.caption.isNotBlank()) { items += text("desc", m.caption.trim()); items += text("©des", m.caption.trim()) }
            if (m.keywords.isNotBlank()) items += text("©cmt", "Keywords: " + m.keywords.trim())
            items += text("©too", "AlcorCut Pro")
            val hdlr = box("hdlr", ByteArray(8), "mdirappl".toByteArray(Charsets.ISO_8859_1), ByteArray(9))
            val meta = box("meta", ByteArray(4), hdlr, box("ilst", *items.toTypedArray()))
            val udtaOld = children(moov, 8, moov.size).firstOrNull { type(moov, it.first) == "udta" }
            val nm: ByteArray
            val grow: Int
            if (udtaOld == null) {
                val udta = box("udta", meta)
                nm = moov + udta; grow = udta.size
            } else {
                // Our meta at the end of the existing udta.
                val end = udtaOld.first + udtaOld.second
                nm = ByteArray(moov.size + meta.size)
                moov.copyInto(nm, 0, 0, end); meta.copyInto(nm, end); moov.copyInto(nm, end + meta.size, end, moov.size)
                put32(nm, udtaOld.first, (udtaOld.second + meta.size).toLong())
                grow = meta.size
            }
            put32(nm, 0, nm.size.toLong())
            if (mdatAt > moovAt) shiftOffsets(nm, 0, nm.size, grow.toLong())
            if (moovAt + moovSize == len) { raf.seek(moovAt); raf.write(nm); raf.setLength(moovAt + nm.size) }
            else {
                val tmp = File(f.parentFile, f.name + ".meta")
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
}
