package android.provider

import android.net.Uri

/** Names used by the shared saving code; on Windows they point at the Pictures / Videos folders. Own code. */
object MediaStore {
    const val AUTHORITY = "media"
    fun setRequireOriginal(u: Uri): Uri = u
    fun createWriteRequest(r: android.content.ContentResolver, uris: Collection<Uri>): android.app.PendingIntent = android.app.PendingIntent()
    fun createDeleteRequest(r: android.content.ContentResolver, uris: Collection<Uri>): android.app.PendingIntent = android.app.PendingIntent()
    fun createTrashRequest(r: android.content.ContentResolver, uris: Collection<Uri>, trash: Boolean): android.app.PendingIntent = android.app.PendingIntent()
    const val VOLUME_EXTERNAL_PRIMARY = "external_primary"
    const val VOLUME_EXTERNAL = "external"

    open class MediaColumns {
        companion object {
            const val DISPLAY_NAME = "_display_name"
            const val MIME_TYPE = "mime_type"
            const val RELATIVE_PATH = "relative_path"
            const val IS_PENDING = "is_pending"
            const val SIZE = "_size"
            const val DATE_ADDED = "date_added"
            const val DATE_MODIFIED = "date_modified"
            const val DATE_TAKEN = "datetaken"
            const val DATA = "_data"
            const val IS_TRASHED = "is_trashed"
            const val ORIENTATION = "orientation"
            const val DURATION = "duration"
            const val _ID = "_id"
            const val WIDTH = "width"
            const val HEIGHT = "height"
        }
    }

    object Images {
        object Media {
            const val DATE_TAKEN = "datetaken"
            const val DISPLAY_NAME = "_display_name"
            const val _ID = "_id"
            val EXTERNAL_CONTENT_URI: Uri = Uri.parse("content://media/external/images/media")
            fun getContentUri(v: String): Uri = EXTERNAL_CONTENT_URI
        }
    }
    object Video {
        object VideoColumns {
            const val DURATION = "duration"
            const val RESOLUTION = "resolution"
            const val DATE_TAKEN = "datetaken"
        }
        object Media {
            val EXTERNAL_CONTENT_URI: Uri = Uri.parse("content://media/external/video/media")
            fun getContentUri(v: String): Uri = EXTERNAL_CONTENT_URI
        }
    }
    object Audio {
        object Media {
            val EXTERNAL_CONTENT_URI: Uri = Uri.parse("content://media/external/audio/media")
            fun getContentUri(v: String): Uri = EXTERNAL_CONTENT_URI
        }
    }
    object Files {
        fun getContentUri(v: String): Uri = Uri.parse("content://media/external/file")
    }
    object Downloads {
        val EXTERNAL_CONTENT_URI: Uri = Uri.parse("content://media/external/downloads")
    }
}

object OpenableColumns {
    const val DISPLAY_NAME = "_display_name"
    const val SIZE = "_size"
}
