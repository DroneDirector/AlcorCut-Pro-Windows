package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Still photos in the video editor: a photo stays a photo — it shows still (no zoom unless you add one),
 * for as long as you choose, and is marked PHOTO in the media strip and on the timeline. Under the hood
 * it plays as a still clip made from the photo at full quality (up to 4K).
 */
object Stills {
    val PHOTO_EXT = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "bmp", "gif", "dng", "tif", "tiff")
    val SECONDS = listOf(1, 2, 3, 4, 5, 6, 8, 10, 15, 20, 30, 45, 60)

    fun isPhoto(c: Context, u: Uri): Boolean {
        val t = try { c.contentResolver.getType(u) } catch (_: Throwable) { null }
        if (t != null) return t.startsWith("image/")
        return (u.lastPathSegment ?: "").substringAfterLast('.', "").lowercase() in PHOTO_EXT
    }

    private fun nameOf(c: Context, u: Uri): String = try {
        c.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Throwable) { null } ?: (u.lastPathSegment?.substringAfterLast('/') ?: "Photo")

    /** A still clip of [photo] lasting [seconds]; null if the photo can't be read. */
    suspend fun make(c: Context, photo: Uri, seconds: Int): Clip? {
        try { c.contentResolver.takePersistableUriPermission(photo, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
        val video = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Uri?> { cont -> Generators.photoClip(c, photo, seconds.coerceIn(1, 60)) { u, _ -> if (cont.isActive) cont.resume(u) } }
        } ?: return null
        val clip = withContext(Dispatchers.IO) { ClipLoader.load(c, video) } ?: return null
        return clip.copy(still = photo.toString(), name = withContext(Dispatchers.IO) { nameOf(c, photo) })
    }

    /** Videos load as they are; photos become still clips (in the same order). */
    suspend fun loadAll(c: Context, uris: List<Uri>, seconds: Int): List<Clip> {
        val out = ArrayList<Clip>()
        for (u in uris) {
            val clip = if (isPhoto(c, u)) make(c, u, seconds) else withContext(Dispatchers.IO) {
                try { c.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
                ClipLoader.load(c, u)
            }
            if (clip != null) out += clip
        }
        return out
    }
}

/** Round trip: while the video editor has a photo open in the photo editor, its export comes back here. */
object PhotoRoundTrip {
    @Volatile var onExported: ((Uri) -> Unit)? = null
}

/** Edit page, for a photo on the timeline: how long it shows, and an optional slow zoom. */
@Composable
fun StillSection(c: Clip, busy: Boolean, onSeconds: (Int) -> Unit, onZoom: (Boolean) -> Unit, onEditPhoto: () -> Unit = {}) {
    SectionTitle("PHOTO")
    PillChip("✎ EDIT IN THE PHOTO EDITOR", false, accentText = true) { onEditPhoto() }
    Text("Opens this photo in the photo editor; when you export it there, the edited photo replaces it on the timeline.", color = Pro.Dim, fontSize = 11.sp)
    Text("A still photo. Choose how long it shows; trimming shortens it too.", color = Pro.Dim, fontSize = 11.sp)
    val now = ((c.endMs - c.startMs) / 1000f)
    Row(Modifier.hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Stills.SECONDS.forEach { s -> PillChip("${s} s", kotlin.math.abs(now - s) < 0.05f) { if (!busy) onSeconds(s) } }
    }
    if (busy) Text("Making the photo longer…", color = Pro.Amber, fontSize = 11.sp)
    Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PillChip("Still", !c.reframe.animate) { onZoom(false) }
        PillChip("Slow zoom", c.reframe.animate) { onZoom(true) }
    }
}
