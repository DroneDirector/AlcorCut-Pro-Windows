package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Live portrait preview: the background of the viewfinder is blurred while you frame, at the
 * strength on the slider (or AUTO). People are found on every frame; any other subject — pets,
 * flowers, rocks, branches — a few times a second. Everything runs on the phone (never the cloud).
 * The subject itself is never covered, so it stays live and sharp; only the blur can lag a little.
 */
object PortraitLive {
    /** Long side of the frames used for the live preview. */
    const val SIDE = 360

    /** UI read-outs (set on the main thread). 0 = nothing found, 1 = person, 2 = other subject. */
    var found by mutableStateOf(0)
    var autoF by mutableStateOf(0f)

    data class Frame(val overlay: Bitmap?, val kind: Int, val f: Float)

    private val slow = Executors.newSingleThreadExecutor { r -> Thread(r, "portrait-live") }
    @Volatile private var busy = false
    @Volatile private var subj: FloatArray? = null
    @Volatile private var subjW = 0
    @Volatile private var subjH = 0
    @Volatile private var subjAt = 0L
    @Volatile private var askedAt = 0L

    fun reset() { subj = null; subjAt = 0L; askedAt = 0L }

    /** Other subjects: ML Kit first (anything that stands out), then the on-phone subject AI (people, pets…). */
    private fun requestSubject(context: Context, small: Bitmap) {
        val now = System.currentTimeMillis()
        if (busy || now - askedAt < 300) return
        busy = true; askedAt = now
        val copy = small.copy(Bitmap.Config.ARGB_8888, false)
        slow.execute {
            try {
                val m = Portrait.anySubjectMask(copy) ?: Portrait.editorSubjectMask(context, copy, allowCloud = false)
                subjW = copy.width; subjH = copy.height; subj = m
                subjAt = System.currentTimeMillis()
            } catch (_: Throwable) {
            } finally { copy.recycle(); busy = false }
        }
    }

    /** Blurred background over [small] (a downscaled viewfinder frame); transparent where the subject is. Off the main thread. */
    fun render(context: Context, small: Bitmap, fNumber: Float): Frame {
        val w = small.width; val h = small.height
        val person = Portrait.livePersonMask(small)
        var kind = 1
        val mask: FloatArray = person ?: run {
            kind = 2
            requestSubject(context, small)
            val m = subj
            if (m == null || System.currentTimeMillis() - subjAt > 1500) return Frame(null, 0, 0f)
            if (subjW == w && subjH == h) m.copyOf() else Portrait.resample(m, subjW, subjH, w, h)
        }
        val f = if (fNumber <= 0f) Portrait.autoF(mask) else fNumber
        // Firm up, then soften the edge a little (same as the saved photo).
        for (i in mask.indices) mask[i] = ((mask[i] - 0.35f) / 0.4f).coerceIn(0f, 1f)
        Portrait.blurPlane(mask, w, h, 1, 2)

        val px = IntArray(w * h); small.getPixels(px, 0, w, 0, 0, w, h)
        val R = FloatArray(px.size); val G = FloatArray(px.size); val B = FloatArray(px.size); val W = FloatArray(px.size)
        for (i in px.indices) {
            val bg = 1f - mask[i]; val c = px[i]
            R[i] = ((c shr 16) and 0xFF) * bg; G[i] = ((c shr 8) and 0xFF) * bg; B[i] = (c and 0xFF) * bg; W[i] = bg
        }
        val st = Portrait.strength(f)
        val radius = (max(w, h) * 0.018f * st).roundToInt().coerceAtLeast(1)
        // The low-res preview is itself a little soft, so a weak blur is shown fainter (f/16 ≈ barely there, like the photo).
        val fade = (st / 0.35f).coerceIn(0f, 1f)
        for (pl in listOf(R, G, B, W)) Portrait.blurPlane(pl, w, h, radius)
        for (i in px.indices) {
            val a = ((1f - mask[i]) * fade * 255f).roundToInt().coerceIn(0, 255)
            val ww = W[i]
            val rgb = if (ww > 0.02f)
                ((R[i] / ww).toInt().coerceIn(0, 255) shl 16) or ((G[i] / ww).toInt().coerceIn(0, 255) shl 8) or (B[i] / ww).toInt().coerceIn(0, 255)
            else px[i] and 0xFFFFFF
            px[i] = (a shl 24) or rgb
        }
        return Frame(Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888), kind, f)
    }
}
