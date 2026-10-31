package com.dronedirector.veyrocutpro.windows

import java.io.File

/** The app's own camera library for Windows (alcorcut_mf.dll, Media Foundation). Own code. */
object MfNative {
    /** Loaded and usable (false off Windows or when the library is missing). */
    val ok: Boolean by lazy {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return@lazy false
        val places = listOfNotNull(
            System.getProperty("compose.application.resources.dir")?.let { File(it, "native/alcorcut_mf.dll") },
            File(System.getProperty("user.dir"), "packaging/resources/windows/native/alcorcut_mf.dll"),
            File(System.getProperty("user.dir"), "native-build/Release/alcorcut_mf.dll"),
        )
        for (f in places) if (f.isFile) try { System.load(f.absolutePath); return@lazy true } catch (_: Throwable) {}
        try { System.loadLibrary("alcorcut_mf"); true } catch (_: Throwable) { false }
    }

    @JvmStatic external fun lastError(): String
    @JvmStatic external fun list(): Array<String>
    @JvmStatic external fun open(link: String): Long
    @JvmStatic external fun formats(h: Long): Array<String>
    @JvmStatic external fun setFormat(h: Long, index: Int): Int
    @JvmStatic external fun read(h: Long, dst: IntArray): Long
    @JvmStatic external fun controls(h: Long): Array<String>
    @JvmStatic external fun setControl(h: Long, kind: Int, id: Int, value: Int, auto: Boolean): Boolean
    @JvmStatic external fun close(h: Long)
    @JvmStatic external fun recOpen(path: String, w: Int, h: Int, fpsNum: Int, fpsDen: Int, bitrate: Int, hevc: Boolean, rate: Int, channels: Int): Long
    @JvmStatic external fun recVideo(h: Long, px: IntArray, ts: Long, dur: Long): Boolean
    @JvmStatic external fun recAudio(h: Long, pcm: ByteArray, n: Int, ts: Long, dur: Long): Boolean
    @JvmStatic external fun recClose(h: Long): Boolean
    @JvmStatic external fun encOpen(w: Int, h: Int, fps: Int, bitrate: Int, audioBitrate: Int): Long
    @JvmStatic external fun encVideo(h: Long, px: IntArray, ts: Long, dur: Long): ByteArray?
    @JvmStatic external fun encHeader(h: Long): ByteArray?
    @JvmStatic external fun encAudio(h: Long, pcm: ByteArray, n: Int, ts: Long, dur: Long): ByteArray?
    @JvmStatic external fun encClose(h: Long)
    @JvmStatic external fun decOpen(): Long
    @JvmStatic external fun decFrame(h: Long, data: ByteArray, ts: Long, dst: IntArray): Long
    @JvmStatic external fun decClose(h: Long)
    @JvmStatic external fun accel(): FloatArray?
    @JvmStatic external fun ptpList(): Array<String>
    @JvmStatic external fun ptpOpen(id: String): Long
    @JvmStatic external fun ptpCommand(h: Long, op: Int, params: IntArray?, data: ByteArray?, read: Boolean): ByteArray?
    @JvmStatic external fun ptpClose(h: Long)

    fun error(): String = try { lastError() } catch (_: Throwable) { "" }
}
