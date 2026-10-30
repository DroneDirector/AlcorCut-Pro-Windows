package com.dronedirector.veyrocutpro.editor

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * WebP, AVIF and JPEG XL saving on Windows with the encoders that come with the app (cwebp, avifenc,
 * cjxl — BSD licences), and HEIC with Windows' own HEIF encoder (needs Microsoft's HEVC Video
 * Extensions, which many PCs already have). Own code.
 */
object WinEncode {
    @Volatile var lastError: String? = null

    private val dir: File? by lazy {
        listOfNotNull(System.getProperty("compose.application.resources.dir"), System.getProperty("user.dir") + "/packaging/resources/windows")
            .map { File(it, "encoders") }.firstOrNull { File(it, "cjxl.exe").exists() || File(it, "avifenc.exe").exists() || File(it, "cwebp.exe").exists() }
    }
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    fun has(exe: String) = windows && dir?.let { File(it, exe).exists() } == true

    /** WebP (lossy) of [b]; null when it couldn't be made. */
    fun webp(b: Bitmap, quality: Int): ByteArray? = run("cwebp.exe", b, "webp") { i, o -> listOf("-q", "${quality.coerceIn(1, 100)}", "-mt", "-metadata", "none", i, "-o", o) }
    fun avif(b: Bitmap, quality: Int): ByteArray? = run("avifenc.exe", b, "avif") { i, o -> listOf("-q", "${quality.coerceIn(1, 100)}", "-s", "6", "-j", "all", i, o) }
    fun jxl(b: Bitmap, quality: Int): ByteArray? = run("cjxl.exe", b, "jxl") { i, o -> listOf(i, o, "-q", "${quality.coerceIn(1, 100)}", "-e", "7") }

    private fun run(exe: String, b: Bitmap, ext: String, args: (String, String) -> List<String>): ByteArray? {
        lastError = null
        val d = dir
        if (!windows || d == null || !File(d, exe).exists()) { lastError = "the encoder isn't installed with the app"; return null }
        val tmp = File(android.content.Context.app.cacheDir, "enc_${System.nanoTime()}")
        val inp = File(tmp.path + ".png"); val out = File(tmp.path + ".$ext")
        try {
            writePng(b, inp)
            val p = ProcessBuilder(listOf(File(d, exe).path) + args(inp.path, out.path)).directory(d).redirectErrorStream(true).start()
            val log = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(10, TimeUnit.MINUTES)) { p.destroyForcibly(); lastError = "took too long"; return null }
            if (p.exitValue() != 0 || !out.exists() || out.length() == 0L) { lastError = log.lines().lastOrNull { it.isNotBlank() } ?: "failed"; return null }
            return out.readBytes()
        } catch (t: Throwable) { lastError = t.message ?: t.javaClass.simpleName; return null }
        finally { inp.delete(); out.delete() }
    }

    /** HEIC with Windows' HEIF encoder (through PowerShell); throws with the reason when it can't. */
    fun heic(b: Bitmap, target: File) {
        if (!windows) throw UnsupportedOperationException("HEIC saving needs Windows")
        val tmp = File(android.content.Context.app.cacheDir, "heic_${System.nanoTime()}.bmp")
        try {
            val w = b.width; val h = b.height
            val img = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_3BYTE_BGR)
            val row = IntArray(w)
            for (y in 0 until h) { b.getPixels(row, 0, w, 0, y, w, 1); img.setRGB(0, y, w, 1, row, 0, w) }
            javax.imageio.ImageIO.write(img, "bmp", tmp)
            fun q(s: String) = "'" + s.replace("'", "''") + "'"
            val script = """
                ${'$'}ErrorActionPreference = 'Stop'
                Add-Type -AssemblyName PresentationCore
                ${'$'}in = [IO.File]::OpenRead(${q(tmp.path)})
                try {
                  ${'$'}dec = [Windows.Media.Imaging.BitmapDecoder]::Create(${'$'}in, [Windows.Media.Imaging.BitmapCreateOptions]::None, [Windows.Media.Imaging.BitmapCacheOption]::OnLoad)
                  ${'$'}enc = [Windows.Media.Imaging.BitmapEncoder]::Create([Guid]'e1e62521-6787-405b-a339-500715b5763f')
                  ${'$'}enc.Frames.Add([Windows.Media.Imaging.BitmapFrame]::Create(${'$'}dec.Frames[0]))
                  ${'$'}out = [IO.File]::Create(${q(target.path)})
                  try { ${'$'}enc.Save(${'$'}out) } finally { ${'$'}out.Close() }
                } finally { ${'$'}in.Close() }
            """.trimIndent()
            val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", "-").redirectErrorStream(true).start()
            p.outputStream.bufferedWriter().use { it.write(script) }
            val log = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(5, TimeUnit.MINUTES)) { p.destroyForcibly(); throw IllegalStateException("took too long") }
            if (p.exitValue() != 0 || !target.exists() || target.length() == 0L) {
                target.delete()
                throw IllegalStateException(
                    if ("0x88982F50" in log || "codec" in log.lowercase() || "component" in log.lowercase())
                        "Windows has no HEIC encoder on this PC — install \"HEVC Video Extensions\" and \"HEIF Image Extensions\" from the Microsoft Store"
                    else log.lines().lastOrNull { it.isNotBlank() }?.trim() ?: "failed")
            }
        } finally { tmp.delete() }
    }

    /** Quick PNG (light compression) for handing the picture to an encoder. */
    private fun writePng(b: Bitmap, f: File) {
        val w = b.width; val h = b.height
        val alpha = b.hasAlpha()
        val ch = if (alpha) 4 else 3
        val raw = ByteArrayOutputStream()
        DeflaterOutputStream(raw, Deflater(1), 1 shl 16).use { z ->
            val row = IntArray(w); val line = ByteArray(1 + w * ch)
            for (y in 0 until h) {
                b.getPixels(row, 0, w, 0, y, w, 1)
                var k = 1
                for (x in 0 until w) {
                    val c = row[x]
                    line[k++] = (c shr 16).toByte(); line[k++] = (c shr 8).toByte(); line[k++] = c.toByte()
                    if (alpha) line[k++] = (c ushr 24).toByte()
                }
                z.write(line)
            }
        }
        DataOutputStream(f.outputStream().buffered(1 shl 16)).use { o ->
            o.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
            fun chunk(type: String, data: ByteArray) {
                o.writeInt(data.size); val t = type.toByteArray(Charsets.ISO_8859_1); o.write(t); o.write(data)
                val crc = CRC32(); crc.update(t); crc.update(data); o.writeInt(crc.value.toInt())
            }
            chunk("IHDR", java.nio.ByteBuffer.allocate(13).putInt(w).putInt(h).put(8).put((if (alpha) 6 else 2).toByte()).put(0).put(0).put(0).array())
            chunk("IDAT", raw.toByteArray())
            chunk("IEND", ByteArray(0))
        }
    }
}
