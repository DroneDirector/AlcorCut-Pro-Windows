package android.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/**
 * Video facts and frames. The Windows video engine comes with the video editor; until then videos
 * report nothing (photo work is unaffected). Own code.
 */
open class MediaMetadataRetriever : AutoCloseable {
    fun setDataSource(path: String) {}
    fun setDataSource(context: Context, uri: Uri) {}
    fun setDataSource(fd: java.io.FileDescriptor) {}
    fun extractMetadata(key: Int): String? = null
    fun getFrameAtTime(us: Long, option: Int): Bitmap? = null
    fun getFrameAtTime(us: Long): Bitmap? = null
    fun getScaledFrameAtTime(us: Long, option: Int, w: Int, h: Int): Bitmap? = null
    val embeddedPicture: ByteArray? get() = null
    fun release() {}
    override fun close() {}

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
