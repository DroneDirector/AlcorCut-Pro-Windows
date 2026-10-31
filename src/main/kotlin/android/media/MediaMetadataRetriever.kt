package android.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/** Video facts and frames, read with Windows' media engine. Own code. */
open class MediaMetadataRetriever : AutoCloseable {
    private var file: java.io.File? = null
    private var info: com.dronedirector.veyrocutpro.windows.MediaInfo? = null
    private var reader: com.dronedirector.veyrocutpro.windows.VideoFile? = null
    private var readerSize = 0

    fun setDataSource(path: String) { file = java.io.File(path); info = com.dronedirector.veyrocutpro.windows.VideoFile.info(file!!); if (info == null) throw IllegalArgumentException("Can't read $path") }
    fun setDataSource(context: Context, uri: Uri) {
        val f = com.dronedirector.veyrocutpro.windows.MediaFiles.local(uri) ?: throw IllegalArgumentException("Can't read $uri")
        setDataSource(f.path)
    }
    fun setDataSource(fd: java.io.FileDescriptor) { throw IllegalArgumentException("File descriptors aren't readable here") }
    fun extractMetadata(key: Int): String? {
        val i = info ?: return null
        val f = file ?: return null
        return when (key) {
            METADATA_KEY_DURATION -> (i.durationUs / 1000).toString()
            METADATA_KEY_VIDEO_WIDTH -> i.width.takeIf { i.hasVideo }?.toString()
            METADATA_KEY_VIDEO_HEIGHT -> i.height.takeIf { i.hasVideo }?.toString()
            METADATA_KEY_VIDEO_ROTATION -> i.rotation.toString()
            METADATA_KEY_HAS_AUDIO -> if (i.hasAudio) "yes" else null
            METADATA_KEY_HAS_VIDEO -> if (i.hasVideo) "yes" else null
            METADATA_KEY_CAPTURE_FRAMERATE -> i.fps.toString()
            METADATA_KEY_VIDEO_FRAME_COUNT -> (i.durationUs * i.fps / 1_000_000).toLong().toString()
            METADATA_KEY_MIMETYPE -> when (f.extension.lowercase()) { "mov" -> "video/quicktime"; "webm" -> "video/webm"; "mkv" -> "video/x-matroska"; "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "m4a", "aac" -> "audio/mp4"; else -> if (i.hasVideo) "video/mp4" else "audio/mp4" }
            METADATA_KEY_DATE -> java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(f.lastModified()))
            METADATA_KEY_BITRATE -> if (i.durationUs > 0) (f.length() * 8_000_000 / i.durationUs).toString() else null
            else -> null
        }
    }
    fun getFrameAtTime(us: Long, option: Int): Bitmap? = frame(us, 0, 0)
    fun getFrameAtTime(us: Long): Bitmap? = frame(us, 0, 0)
    fun getScaledFrameAtTime(us: Long, option: Int, w: Int, h: Int): Bitmap? = frame(us, w, h)
    val embeddedPicture: ByteArray? get() = null

    private fun frame(us: Long, w: Int, h: Int): Bitmap? = synchronized(this) {
        val f = file ?: return null
        if (info?.hasVideo != true) return null
        val key = w * 100_000 + h
        if (reader == null || readerSize != key) { reader?.close(); reader = try { com.dronedirector.veyrocutpro.windows.VideoFile(f, w, h) } catch (_: Throwable) { null }; readerSize = key }
        val r = reader ?: return null
        val fr = r.frameAt(if (us < 0) 0 else us) ?: return null
        var b = Bitmap.createBitmap(fr.px, fr.width, fr.height, Bitmap.Config.ARGB_8888)
        val rot = info?.rotation ?: 0
        if (rot != 0) b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, android.graphics.Matrix().apply { postRotate(rot.toFloat()) }, true)
        return b
    }

    fun release() = close()
    override fun close() { synchronized(this) { reader?.close(); reader = null } }

    companion object {
        const val OPTION_PREVIOUS_SYNC = 0; const val OPTION_NEXT_SYNC = 1; const val OPTION_CLOSEST_SYNC = 2; const val OPTION_CLOSEST = 3
        const val METADATA_KEY_DURATION = 9
        const val METADATA_KEY_VIDEO_WIDTH = 18
        const val METADATA_KEY_VIDEO_HEIGHT = 19
        const val METADATA_KEY_VIDEO_ROTATION = 24
        const val METADATA_KEY_HAS_AUDIO = 16
        const val METADATA_KEY_HAS_VIDEO = 17
        const val METADATA_KEY_MIMETYPE = 12
        const val METADATA_KEY_BITRATE = 20
        const val METADATA_KEY_CAPTURE_FRAMERATE = 25
        const val METADATA_KEY_DATE = 5
        const val METADATA_KEY_LOCATION = 23
        const val METADATA_KEY_TITLE = 7
        const val METADATA_KEY_ARTIST = 2
        const val METADATA_KEY_COLOR_TRANSFER = 36
        const val METADATA_KEY_COLOR_STANDARD = 35
        const val METADATA_KEY_VIDEO_FRAME_COUNT = 32
    }
}
