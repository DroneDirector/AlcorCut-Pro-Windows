package com.dronedirector.veyrocutpro.camera

import android.net.Uri

/** One photo or video saved by AlcorCut Pro. */
data class MediaItem(
    val uri: Uri,
    val isVideo: Boolean,
    val name: String,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val mime: String,
) {
    /** RAW photo (DNG, or another camera's RAW format). */
    val isRaw: Boolean get() = !isVideo && (mime.contains("dng", ignoreCase = true) || mime.contains("raw", ignoreCase = true) || name.endsWith(".dng", ignoreCase = true) ||
        name.substringAfterLast('.', "").lowercase() in com.dronedirector.veyrocutpro.editor.RawDng.OTHER_RAW_EXTENSIONS)
}
