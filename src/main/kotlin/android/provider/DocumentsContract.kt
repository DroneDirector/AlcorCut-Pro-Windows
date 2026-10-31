package android.provider

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File

/** Folders picked with the folder dialog are plain folders on Windows. Own code. */
object DocumentsContract {
    const val EXTRA_INITIAL_URI = "android.provider.extra.INITIAL_URI"
    fun getTreeDocumentId(tree: Uri): String = tree.toFile()?.path ?: tree.toString()
    fun getDocumentId(u: Uri): String = u.toFile()?.path ?: u.toString()
    fun buildDocumentUri(authority: String?, id: String): Uri = Uri.fromFile(java.io.File(id))
    fun buildDocumentUriUsingTree(tree: Uri, id: String): Uri = Uri.fromFile(File(id))
    fun buildChildDocumentsUriUsingTree(tree: Uri, id: String): Uri = Uri.fromFile(File(id))
    fun isDocumentUri(c: Context?, u: Uri?): Boolean = u?.scheme == "file"
    fun isTreeUri(u: Uri?): Boolean = u?.toFile()?.isDirectory == true

    /** Makes a new file in the folder (never overwriting). */
    fun createDocument(r: ContentResolver, parent: Uri, mime: String, name: String): Uri? {
        val dir = parent.toFile() ?: return null
        if (mime == Document.MIME_TYPE_DIR) return File(dir, name).apply { mkdirs() }.let { Uri.fromFile(it) }
        var f = File(dir, name); var i = 1
        val stem = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        while (f.exists()) { f = File(dir, "$stem ($i)$ext"); i++ }
        f.createNewFile()
        return Uri.fromFile(f)
    }

    fun renameDocument(r: ContentResolver, u: Uri, name: String): Uri? {
        val f = u.toFile() ?: return null
        val t = File(f.parentFile, name)
        return if (f.renameTo(t)) Uri.fromFile(t) else null
    }

    fun deleteDocument(r: ContentResolver, u: Uri): Boolean = u.toFile()?.delete() == true

    object Document {
        const val COLUMN_DOCUMENT_ID = "document_id"
        const val COLUMN_DISPLAY_NAME = "_display_name"
        const val COLUMN_MIME_TYPE = "mime_type"
        const val COLUMN_SIZE = "_size"
        const val COLUMN_LAST_MODIFIED = "last_modified"
        const val MIME_TYPE_DIR = "vnd.android.document/directory"
    }
}
