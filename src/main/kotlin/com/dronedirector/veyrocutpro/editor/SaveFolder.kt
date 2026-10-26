package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.dronedirector.veyrocutpro.camera.Storage
import com.dronedirector.veyrocutpro.camera.StorageArea
import java.io.OutputStream

/**
 * Editor exports into a picked folder (Settings → SAVE LOCATION, see [Storage]). Returns null when the
 * editor saves to a media volume (internal / SD card / USB drive) or the folder isn't there right now,
 * so the caller saves through MediaStore instead (which falls back to internal storage).
 */
object SaveFolder {
    private fun area(video: Boolean) = if (video) StorageArea.VIDEO else StorageArea.PHOTO

    fun get(context: Context, video: Boolean): Uri? =
        Storage.load(context, area(video)).takeIf { Storage.isTree(it) }?.let { Uri.parse(it.removePrefix("tree:")) }

    fun label(tree: Uri?): String {
        if (tree == null) return "DCIM/AlcorCutPro"
        return try {
            val id = DocumentsContract.getTreeDocumentId(tree)
            val (vol, path) = id.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val v = if (vol == "primary") "Internal storage" else "External drive"
            if (path.isEmpty()) v else "$v/$path"
        } catch (_: Throwable) { tree.lastPathSegment ?: "Chosen folder" }
    }

    fun write(context: Context, video: Boolean, name: String, mime: String, body: (OutputStream) -> Unit): Uri? {
        val place = Storage.resolve(context, Storage.load(context, area(video)))
        if (!Storage.isTree(place)) return null
        val doc = Storage.createInTree(context, place, mime, name) ?: return null
        return try {
            context.contentResolver.openOutputStream(doc)?.use(body) ?: throw IllegalStateException("no stream")
            doc
        } catch (_: Throwable) {
            Storage.discard(context, doc)
            null
        }
    }
}
