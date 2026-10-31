package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Helpers for voice / music separation: the clip's sound out of the video, and stems added together. Own code. */
object StemMix {
    /** Copies the first audio track of [uri] into an .m4a in [dir] (no re-encoding); null when it can't. */
    fun extractAudio(context: Context, uri: Uri, dir: File): File? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            val t = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            val fmt = ex.getTrackFormat(t)
            if (fmt.getString(MediaFormat.KEY_MIME) != MediaFormat.MIMETYPE_AUDIO_AAC) return null
            ex.selectTrack(t)
            val out = File(dir, "sound.m4a")
            val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val track = mux.addTrack(fmt)
            mux.start()
            val buf = ByteBuffer.allocate(1 shl 20)
            val info = android.media.MediaCodec.BufferInfo()
            while (true) {
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                info.set(0, n, ex.sampleTime, if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                mux.writeSampleData(track, buf, info)
                ex.advance()
            }
            mux.stop(); mux.release()
            return out
        } catch (_: Throwable) { return null } finally { ex.release() }
    }

    private class Wav(val f: File, val channels: Int, val rate: Int, val bits: Int, val float: Boolean, val dataAt: Long, val dataLen: Long)

    private fun open(f: File): Wav? = RandomAccessFile(f, "r").use { r ->
        val head = ByteArray(12); r.readFully(head)
        if (String(head, 0, 4) != "RIFF" || String(head, 8, 4) != "WAVE") return null
        var ch = 0; var rate = 0; var bits = 0; var fmtTag = 0
        while (r.filePointer + 8 <= r.length()) {
            val id = ByteArray(4); r.readFully(id)
            val len = Integer.reverseBytes(r.readInt()).toLong() and 0xFFFFFFFFL
            val at = r.filePointer
            when (String(id)) {
                "fmt " -> {
                    fmtTag = java.lang.Short.reverseBytes(r.readShort()).toInt() and 0xFFFF
                    ch = java.lang.Short.reverseBytes(r.readShort()).toInt()
                    rate = Integer.reverseBytes(r.readInt())
                    r.skipBytes(6)
                    bits = java.lang.Short.reverseBytes(r.readShort()).toInt()
                    if (fmtTag == 0xFFFE && len >= 26) { r.skipBytes(8); fmtTag = java.lang.Short.reverseBytes(r.readShort()).toInt() and 0xFFFF }
                }
                "data" -> return Wav(f, ch, rate, bits, fmtTag == 3, at, minOf(len, r.length() - at))
            }
            r.seek(at + len + (len and 1))
        }
        null
    }

    /** Adds the WAV files [parts] together into a 16-bit WAV [out] (same rate and channels as the first). */
    fun sum(parts: List<File>, out: File): Boolean {
        val ws = parts.mapNotNull { open(it) }
        if (ws.isEmpty()) return false
        val ch = ws[0].channels; val rate = ws[0].rate
        val use = ws.filter { it.channels == ch && it.rate == rate && (it.bits == 16 || (it.float && it.bits == 32) || it.bits == 24) }
        if (use.isEmpty()) return false
        val frames = use.minOf { it.dataLen / (it.bits / 8 * ch) }
        val ins = use.map { w -> DataInputStream(java.io.BufferedInputStream(java.io.FileInputStream(w.f).also { it.channel.position(w.dataAt) }, 1 shl 16)) }
        try {
            java.io.BufferedOutputStream(out.outputStream(), 1 shl 16).use { os ->
                val dataBytes = frames * ch * 2
                val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                h.put("RIFF".toByteArray()); h.putInt((36 + dataBytes).toInt()); h.put("WAVE".toByteArray())
                h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(ch.toShort()); h.putInt(rate); h.putInt(rate * ch * 2); h.putShort((ch * 2).toShort()); h.putShort(16)
                h.put("data".toByteArray()); h.putInt(dataBytes.toInt())
                os.write(h.array())
                val sample = ByteArray(4); val outS = ByteArray(2)
                for (i in 0 until frames * ch) {
                    var s = 0f
                    for ((k, w) in use.withIndex()) {
                        val d = ins[k]
                        s += when {
                            w.float -> { d.readFully(sample, 0, 4); java.lang.Float.intBitsToFloat((sample[0].toInt() and 255) or ((sample[1].toInt() and 255) shl 8) or ((sample[2].toInt() and 255) shl 16) or (sample[3].toInt() shl 24)) }
                            w.bits == 24 -> { d.readFully(sample, 0, 3); ((sample[0].toInt() and 255) or ((sample[1].toInt() and 255) shl 8) or (sample[2].toInt() shl 16)) / 8388608f }
                            else -> { d.readFully(sample, 0, 2); ((sample[0].toInt() and 255) or (sample[1].toInt() shl 8)) / 32768f }
                        }
                    }
                    val v = (s.coerceIn(-1f, 1f) * 32767f).toInt()
                    outS[0] = (v and 255).toByte(); outS[1] = (v shr 8).toByte()
                    os.write(outS)
                }
            }
        } finally { ins.forEach { try { it.close() } catch (_: Throwable) {} } }
        return true
    }
}
