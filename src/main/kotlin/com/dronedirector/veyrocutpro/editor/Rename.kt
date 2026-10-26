package com.dronedirector.veyrocutpro.editor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renames photo files from a template — {name} (the old name), {date} (when it was taken,
 * yyyy-MM-dd), {time}, {seq} (001, 002…) and any text. Works for photos in the phone's gallery
 * (Android asks once for permission to change them) and for files opened from a folder.
 */
object Rename {
    val TOKENS = listOf("{name}", "{date}", "{time}", "{seq}")

    sealed class Outcome {
        data class Done(val uri: Uri) : Outcome()
        object NeedsPermission : Outcome()
        object CantRename : Outcome()
    }

    fun displayName(context: Context, u: Uri): String? = try {
        context.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Throwable) { null }

    private fun taken(context: Context, u: Uri): Long {
        if (u.authority == MediaStore.AUTHORITY) try {
            context.contentResolver.query(u, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0) }
        } catch (_: Throwable) {}
        try {
            context.contentResolver.openInputStream(u)?.use { s ->
                val e = androidx.exifinterface.media.ExifInterface(s)
                e.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)?.let { t ->
                    SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(t)?.let { return it.time }
                }
            }
        } catch (_: Throwable) {}
        return System.currentTimeMillis()
    }

    /** The new file name for the [seq]th photo [u] (keeps the extension). */
    fun newName(context: Context, u: Uri, template: String, seq: Int): String {
        val old = displayName(context, u) ?: "photo.jpg"
        val dot = old.lastIndexOf('.')
        val base = if (dot > 0) old.substring(0, dot) else old
        val ext = if (dot > 0) old.substring(dot) else ".jpg"
        val t = Date(taken(context, u))
        val name = template.ifBlank { "{name}" }
            .replace("{name}", base)
            .replace("{date}", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(t))
            .replace("{time}", SimpleDateFormat("HHmmss", Locale.US).format(t))
            .replace("{seq}", "%03d".format(seq))
            .replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { base }
        return name + ext
    }

    /** Renames [u] to [name]. */
    fun rename(context: Context, u: Uri, name: String): Outcome {
        return try {
            when {
                DocumentsContract.isDocumentUri(context, u) ->
                    DocumentsContract.renameDocument(context.contentResolver, u, name)?.let { Outcome.Done(it) } ?: Outcome.CantRename
                u.authority == MediaStore.AUTHORITY && !u.path.orEmpty().contains("picker") -> {
                    val n = context.contentResolver.update(u, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }, null, null)
                    if (n > 0) Outcome.Done(u) else Outcome.CantRename
                }
                else -> Outcome.CantRename
            }
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= 30 && u.authority == MediaStore.AUTHORITY) Outcome.NeedsPermission else Outcome.CantRename
        } catch (_: Throwable) { Outcome.CantRename }
    }

    /** Android 11+: the system "allow AlcorCut Pro to change these photos?" request. */
    fun permissionRequest(context: Context, uris: List<Uri>): android.app.PendingIntent? =
        if (Build.VERSION.SDK_INT >= 30) try { MediaStore.createWriteRequest(context.contentResolver, uris) } catch (_: Throwable) { null } else null
}
