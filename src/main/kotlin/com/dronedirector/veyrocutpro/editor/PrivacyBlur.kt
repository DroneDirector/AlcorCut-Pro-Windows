package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.opengl.GLES20
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One blurred thing through time: [t] source ms, [box] centre x, centre y, width, height (0..1 of the frame) per time. */
data class BlurTrack(val face: Boolean, val t: List<Long>, val box: List<Float>)

/** Privacy blur on a clip: faces found automatically and spots you tap, each followed as it moves. */
data class PrivacyBlur(
    val tracks: List<BlurTrack> = emptyList(),
    /** false = blur, true = pixelate. */
    val pixelate: Boolean = false,
    val strength: Float = 0.7f,
    /** Extra room round each face / spot (0..1). */
    val pad: Float = 0.25f,
) {
    val active: Boolean get() = tracks.isNotEmpty()

    /** The boxes showing at source time [src] (up to [MAX]). */
    fun boxesAt(src: Long): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        for (tr in tracks) {
            val n = tr.t.size
            if (n == 0) continue
            if (src < tr.t[0] - 120 || src > tr.t[n - 1] + 120) continue
            var i = tr.t.binarySearch(src).let { if (it >= 0) it else (-it - 2) }.coerceIn(0, n - 1)
            val j = min(i + 1, n - 1)
            val k = if (j == i || tr.t[j] == tr.t[i]) 0f else ((src - tr.t[i]).toFloat() / (tr.t[j] - tr.t[i])).coerceIn(0f, 1f)
            val b = FloatArray(4) { q -> tr.box[i * 4 + q] + (tr.box[j * 4 + q] - tr.box[i * 4 + q]) * k }
            val grow = 1f + pad
            b[2] *= grow; b[3] *= grow
            out += b
            if (out.size >= MAX) break
        }
        return out
    }

    companion object { const val MAX = 8 }
}

/** The preview player's position inside the clip on screen (ms from its in point), for effects drawn in the player. */
object PreviewClock {
    @Volatile var posMs: Long = 0L
    @Volatile var at: Long = 0L
    @Volatile var playing: Boolean = false
    /** The position now: between the editor's updates it runs on by itself while playing. */
    fun now(): Long = posMs + if (playing) (android.os.SystemClock.uptimeMillis() - at).coerceIn(0L, 250L) else 0L
}

/** Finding faces through a clip, and following a tapped spot. Own code; faces with ML Kit (on the phone). */
object PrivacyFinder {
    private const val STEP_MS = 200L
    private const val W = 640

    private fun frame(mmr: MediaMetadataRetriever, srcMs: Long): Bitmap? =
        if (Build.VERSION.SDK_INT >= 27) mmr.getScaledFrameAtTime(srcMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST, W, W * 4)
        else mmr.getFrameAtTime(srcMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)

    /** Every face in the clip's used part, linked from frame to frame into tracks. */
    suspend fun faces(context: Context, c: Clip, progress: (Int) -> Unit, cancelled: () -> Boolean): List<BlurTrack> = withContext(Dispatchers.Default) {
        val det = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).setMinFaceSize(0.04f).build())
        val mmr = MediaMetadataRetriever()
        class Open(val t: MutableList<Long>, val b: MutableList<Float>, var last: FloatArray, var lastT: Long)
        val open = ArrayList<Open>(); val done = ArrayList<Open>()
        try {
            mmr.setDataSource(context, c.uri)
            var s = c.startMs
            while (s <= c.endMs && !cancelled()) {
                val bmp = frame(mmr, s)
                if (bmp != null) {
                    val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
                    val faces = try { com.google.android.gms.tasks.Tasks.await(det.process(InputImage.fromBitmap(bmp, 0))) } catch (_: Throwable) { emptyList() }
                    bmp.recycle()
                    val boxes = faces.map { f -> val r = f.boundingBox; floatArrayOf(r.exactCenterX() / w, r.exactCenterY() / h, r.width() / w, r.height() / h) }
                    val used = BooleanArray(boxes.size)
                    // Link each running track to the nearest new box.
                    for (o in open) {
                        var best = -1; var bd = Float.MAX_VALUE
                        boxes.forEachIndexed { k, b ->
                            if (used[k]) return@forEachIndexed
                            val d = abs(b[0] - o.last[0]) + abs(b[1] - o.last[1])
                            if (d < max(o.last[2], o.last[3]) * 1.2f && d < bd) { bd = d; best = k }
                        }
                        if (best >= 0) { used[best] = true; val b = boxes[best]; o.t += s; b.forEach { o.b += it }; o.last = b; o.lastT = s }
                    }
                    // Faces missing for a while end their track; new faces start one.
                    open.removeAll { o -> (s - o.lastT > 600).also { if (it) done += o } }
                    boxes.forEachIndexed { k, b -> if (!used[k]) open += Open(mutableListOf(s), b.toMutableList(), b, s) }
                }
                progress((((s - c.startMs) * 100) / (c.endMs - c.startMs).coerceAtLeast(1)).toInt())
                s += STEP_MS
            }
        } catch (_: Throwable) {
        } finally {
            try { mmr.release() } catch (_: Throwable) {}
            try { det.close() } catch (_: Throwable) {}
        }
        (done + open).filter { it.t.size >= 2 || open.size + done.size == 1 }.map { BlurTrack(true, it.t.toList(), it.b.toList()) }
    }

    /** Follows the spot at [x], [y] (0..1) from source [fromSrc] to the clip's out point. */
    suspend fun spot(context: Context, c: Clip, fromSrc: Long, x: Float, y: Float, size: Float, progress: (Int) -> Unit, cancelled: () -> Boolean): BlurTrack =
        withContext(Dispatchers.Default) {
            val start = c.offsetOfSrc(fromSrc)
            val path = try {
                Tracker.track(context, listOf(c), 0, start, c.lengthMs, x, y, progress, cancelled)
            } catch (_: Throwable) { emptyList() }
            val aspect = if (c.width > 0 && c.height > 0) c.width.toFloat() / c.height else 16f / 9f
            val pts = if (path.isEmpty()) listOf(Triple(start, x, y)) else path
            val t = pts.map { c.srcAtOffset(it.first) }
            val b = pts.flatMap { listOf(it.second, it.third, size, size * aspect) }
            BlurTrack(false, t, b)
        }
}

/** Source ms → timeline offset from the clip's start (inverse of [Clip.srcAtOffset], found by searching). */
fun Clip.offsetOfSrc(src: Long): Long {
    var lo = 0L; var hi = lengthMs
    repeat(30) { val mid = (lo + hi) / 2; if (srcAtOffset(mid) < src) lo = mid + 1 else hi = mid }
    return lo.coerceIn(0L, lengthMs)
}

/**
 * The blur on the picture as filmed. Export: [offsetMs] = where this part of the clip starts (from the clip
 * start). Preview ([preview] = true): the player's own position is read from [PreviewClock].
 */
@OptIn(UnstableApi::class)
class PrivacyBlurEffect(private val c: Clip, private val offsetMs: Long, private val preview: Boolean) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = PrivacyProgram(c, offsetMs, preview, useHdr)
    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean = c.privacy?.active != true
}

@OptIn(UnstableApi::class)
private class PrivacyProgram(private val c: Clip, private val offsetMs: Long, private val preview: Boolean, useHdr: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val p = c.privacy ?: PrivacyBlur()
    private val program = try { GlProgram(VERT, FRAG) } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    private var t0 = Long.MIN_VALUE
    private var w = 1f; private var h = 1f

    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
    override fun configure(inputWidth: Int, inputHeight: Int): Size { w = inputWidth.toFloat(); h = inputHeight.toFloat(); return Size(inputWidth, inputHeight) }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        if (t0 == Long.MIN_VALUE) t0 = presentationTimeUs
        val src = if (preview) c.startMs + PreviewClock.now() else c.srcAtOffset(offsetMs + (presentationTimeUs - t0) / 1000)
        val boxes = p.boxesAt(src)
        val arr = FloatArray(PrivacyBlur.MAX * 4)
        boxes.forEachIndexed { k, b -> for (q in 0 until 4) arr[k * 4 + q] = b[q] }
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uBox", arr)
            program.setIntUniform("uCount", boxes.size)
            program.setFloatsUniform("uTexel", floatArrayOf(1f / w, 1f / h))
            program.setFloatUniform("uStrength", p.strength.coerceIn(0.05f, 1f))
            program.setIntUniform("uPixel", if (p.pixelate) 1 else 0)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }
    }

    companion object {
        const val VERT = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """
        // Boxes are in picture coordinates with y down; the texture has y up.
        const val FRAG = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec4 uBox[8];
            uniform int uCount;
            uniform vec2 uTexel;
            uniform float uStrength;
            uniform int uPixel;
            varying vec2 vTexSamplingCoord;
            void main() {
                vec2 uv = vTexSamplingCoord;
                vec2 p = vec2(uv.x, 1.0 - uv.y);
                vec4 c = texture2D(uTexSampler, uv);
                float m = 0.0; vec4 b = vec4(0.0);
                for (int k = 0; k < 8; k++) {
                    if (k >= uCount) break;
                    vec4 bx = uBox[k];
                    vec2 d = (p - bx.xy) / max(bx.zw * 0.5, vec2(0.001));
                    float r = length(d);
                    float w = 1.0 - smoothstep(0.85, 1.0, r);
                    if (w > m) { m = w; b = bx; }
                }
                if (m <= 0.0) { gl_FragColor = c; return; }
                vec3 o;
                if (uPixel == 1) {
                    float cell = max(b.z, b.w) * mix(0.06, 0.22, uStrength);
                    vec2 q = (floor(p / cell) + 0.5) * cell;
                    o = texture2D(uTexSampler, vec2(q.x, 1.0 - q.y)).rgb;
                } else {
                    vec2 rad = vec2(max(b.z, b.w)) * mix(0.05, 0.16, uStrength);
                    vec3 s = vec3(0.0);
                    for (int i = -3; i <= 3; i++) {
                        for (int j = -3; j <= 3; j++) {
                            s += texture2D(uTexSampler, uv + vec2(float(i), float(j)) / 3.0 * rad).rgb;
                        }
                    }
                    o = s / 49.0;
                }
                gl_FragColor = vec4(mix(c.rgb, o, m), c.a);
            }
        """
    }
}

/** Edit page → PRIVACY BLUR: find faces, add tracked spots, blur or pixelate. */
@Composable
fun PrivacySection(c: Clip, srcNow: Long, onChange: ((Clip) -> Clip) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var stop by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val pb = c.privacy ?: PrivacyBlur()
    SectionTitle("PRIVACY BLUR") { onChange { it.copy(privacy = null) } }
    Text("Blur faces and anything else you choose (number plates, screens, signs). Faces are found on the phone and followed through the clip; a spot you tap is followed as it moves.", color = Pro.Dim, fontSize = 11.sp)
    val faces = pb.tracks.count { it.face }; val spots = pb.tracks.count { !it.face }
    if (pb.active) Text("$faces face" + (if (faces == 1) "" else "s") + " · $spots spot" + (if (spots == 1) "" else "s"), color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    busy?.let { Text(it, color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
    androidx.compose.foundation.layout.Row(Modifier.padding(top = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        PillChip(if (busy != null) "STOP" else "FIND FACES", busy != null, accentText = true) {
            if (busy != null) { stop = true; return@PillChip }
            stop = false; busy = "Finding faces… 0%"
            scope.launch {
                val found = PrivacyFinder.faces(context, c, { p -> busy = "Finding faces… $p% — tap STOP to end" }, { stop })
                busy = null
                onChange { cl -> cl.copy(privacy = (cl.privacy ?: PrivacyBlur()).let { it.copy(tracks = it.tracks.filter { t -> !t.face } + found) }) }
            }
        }
        PillChip("+ SPOT", false) { if (busy == null) picking = true }
        if (pb.active) PillChip("CLEAR", false) { onChange { it.copy(privacy = null) } }
    }
    if (pb.active) {
        Chips(listOf("Blur", "Pixelate"), if (pb.pixelate) 1 else 0) { i -> onChange { cl -> cl.copy(privacy = (cl.privacy ?: PrivacyBlur()).copy(pixelate = i == 1)) } }
        EdSlider("Strength", pb.strength, 0f..1f, 0.7f, ::pct) { v -> onChange { cl -> cl.copy(privacy = (cl.privacy ?: PrivacyBlur()).copy(strength = v)) } }
        EdSlider("Extra room", pb.pad, 0f..1f, 0.25f, ::pct) { v -> onChange { cl -> cl.copy(privacy = (cl.privacy ?: PrivacyBlur()).copy(pad = v)) } }
    }
    if (picking) SpotPickDialog(c, srcNow, onDismiss = { picking = false }) { x, y, size ->
        picking = false
        stop = false; busy = "Following the spot… 0%"
        scope.launch {
            val tr = PrivacyFinder.spot(context, c, srcNow, x, y, size, { p -> busy = "Following the spot… $p% — tap STOP to end" }, { stop })
            busy = null
            onChange { cl -> cl.copy(privacy = (cl.privacy ?: PrivacyBlur()).let { it.copy(tracks = it.tracks + tr) }) }
        }
    }
}

/** The frame at the playhead: tap what to blur, set its size. */
@Composable
private fun SpotPickDialog(c: Clip, srcMs: Long, onDismiss: () -> Unit, onPick: (Float, Float, Float) -> Unit) {
    val context = LocalContext.current
    val frame by produceState<Bitmap?>(null, c.uri, srcMs) {
        value = withContext(Dispatchers.IO) {
            val mmr = MediaMetadataRetriever()
            try { mmr.setDataSource(context, c.uri); if (Build.VERSION.SDK_INT >= 27) mmr.getScaledFrameAtTime(srcMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 960, 960 * 4) else mmr.getFrameAtTime(srcMs * 1000) } catch (_: Throwable) { null } finally { try { mmr.release() } catch (_: Throwable) {} }
        }
    }
    var px by remember { mutableFloatStateOf(-1f) }
    var py by remember { mutableFloatStateOf(-1f) }
    var spot by remember { mutableFloatStateOf(0.12f) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Pro.PanelSolid,
        title = { Text("Tap what to blur", color = Color.White) },
        text = {
            Column {
                val f = frame
                if (f == null) Text("Loading the frame…", color = Pro.Dim)
                else {
                    val img = remember(f) { f.asImageBitmap() }
                    Box(Modifier.fillMaxWidth().aspectRatio(f.width.toFloat() / f.height).pointerInput(Unit) {
                        detectTapGestures { o -> px = o.x / size.width; py = o.y / size.height }
                    }) {
                        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        if (px >= 0f) {
                            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                                val dw = maxWidth * spot
                                Box(Modifier.offset(maxWidth * px - dw / 2, maxHeight * py - dw / 2).size(dw).border(2.dp, Pro.Amber, CircleShape).background(Pro.Amber.copy(alpha = 0.2f), CircleShape))
                            }
                        }
                    }
                }
                EdSlider("Size", spot, 0.03f..0.5f, 0.12f, ::pct) { v -> spot = v }
                Text("The spot is followed from the playhead to the end of the clip.", color = Pro.Dim, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = { if (px >= 0f) onPick(px, py, spot) }, enabled = px >= 0f) { Text("BLUR IT", color = if (px >= 0f) Pro.Amber else Pro.Dim) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )
}
