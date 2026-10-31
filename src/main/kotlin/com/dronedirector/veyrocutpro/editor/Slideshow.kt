package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Slideshow to video (Photo editor → Library → select photos → Slideshow video): the photos become clips on a
 * new video timeline, each with a slow zoom and a transition between them, ready to add music and titles.
 */
object Slideshow {
    data class Request(val photos: List<Uri>, val seconds: Int, val transition: TransitionType, val zoom: Boolean)

    /** Waiting to be built when the video editor opens. */
    @Volatile var request: Request? = null

    /** Opens the video editor (set by the app's main screen). */
    var openVideoEditor: (() -> Unit)? = null

    val TRANSITIONS = listOf(TransitionType.CROSSFADE, TransitionType.DIP_BLACK, TransitionType.DIP_WHITE, TransitionType.NONE)

    /** The clips for [r], in order; [progress] gets (done, total). Photos that can't be read are left out. */
    @OptIn(UnstableApi::class)
    suspend fun build(context: Context, r: Request, progress: (Int, Int) -> Unit): List<Clip> {
        val out = ArrayList<Clip>()
        val trMs = if (r.transition == TransitionType.NONE) 0L else minOf(1000L, r.seconds * 250L)
        for ((i, u) in r.photos.withIndex()) {
            progress(i, r.photos.size)
            val video = suspendCancellableCoroutine<Uri?> { cont ->
                Generators.photoClip(context, u, r.seconds + (if (trMs > 0) 1 else 0)) { clip, _ -> if (cont.isActive) cont.resume(clip) }
            } ?: continue
            val c = withContext(Dispatchers.IO) { ClipLoader.load(context, video) } ?: continue
            // A slow zoom in, drifting a little to one side and then the other.
            val side = if (out.size % 2 == 0) 1f else -1f
            val frame = if (r.zoom) Reframe(start = Frame(1f), end = Frame(1.12f, x = 0.03f * side, y = -0.02f), animate = true) else c.reframe
            out += c.copy(reframe = frame, transition = Transition(type = r.transition, durationMs = trMs.coerceAtLeast(1L)))
        }
        progress(r.photos.size, r.photos.size)
        // No transition after the last photo.
        if (out.isNotEmpty()) out[out.lastIndex] = out.last().copy(transition = Transition())
        return out
    }
}
