package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri

/** Your own text written into an exported photo: title, caption, author, copyright and keywords. */
data class PhotoMetaEdit(
    val title: String = "",
    val caption: String = "",
    val artist: String = "",
    val copyright: String = "",
    val keywords: String = "",
) {
    val isEmpty get() = title.isBlank() && caption.isBlank() && artist.isBlank() && copyright.isBlank() && keywords.isBlank()
}

object MetaEdit {
    /** Writes [m] into the saved photo [uri] (JPEG, PNG and WebP). Returns false when the format can't hold it. */
    fun write(context: Context, uri: Uri, m: PhotoMetaEdit): Boolean = try {
        if (m.isEmpty) true else context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            val e = androidx.exifinterface.media.ExifInterface(pfd.fileDescriptor)
            val desc = listOf(m.title.trim(), m.caption.trim()).filter { it.isNotEmpty() }.joinToString(" — ")
            if (desc.isNotEmpty()) e.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_DESCRIPTION, desc)
            if (m.artist.isNotBlank()) e.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ARTIST, m.artist.trim())
            if (m.copyright.isNotBlank()) e.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_COPYRIGHT, m.copyright.trim())
            if (m.keywords.isNotBlank()) e.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_USER_COMMENT, "Keywords: " + m.keywords.trim())
            e.saveAttributes()
            true
        } ?: false
    } catch (_: Throwable) { false }
}
