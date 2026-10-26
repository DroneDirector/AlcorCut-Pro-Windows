package android.content

import android.net.Uri

object ContentUris {
    fun withAppendedId(u: Uri, id: Long): Uri = Uri.parse(u.toString().trimEnd('/') + "/" + id)
    fun parseId(u: Uri): Long = u.lastPathSegment?.toLongOrNull() ?: -1L
}
