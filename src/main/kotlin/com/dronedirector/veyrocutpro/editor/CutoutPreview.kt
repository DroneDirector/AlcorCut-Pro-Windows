package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.TextureView
import android.view.View
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteOrder
import java.util.WeakHashMap

/**
 * Live background cut-out in the editor's preview (Android 13+), drawn on the player's view the
 * same way the export does it: people found live on the shown picture, or the tracked Magic Mask
 * at the playhead. Blur, colour or see-through (layers below show through).
 */
object CutoutPreview {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 33

    private const val AGSL = """
        uniform shader content;
        uniform shader mask;
        uniform float2 uSize;
        uniform float2 uMaskSize;
        uniform float4 uRect;
        uniform float uMode;
        uniform float uInvert;
        uniform float3 uColor;
        half4 main(float2 p) {
            half4 c = content.eval(p);
            float2 muv = (p / uSize - uRect.xy) / (uRect.zw - uRect.xy);
            float m = (muv.x < 0.0 || muv.y < 0.0 || muv.x > 1.0 || muv.y > 1.0) ? 0.0 : float(mask.eval(muv * uMaskSize).r);
            m = smoothstep(0.25, 0.75, m);
            if (uInvert > 0.5) m = 1.0 - m;
            if (uMode < 0.5) return c * half(m);
            half3 bg;
            if (uMode < 1.5) {
                half3 s = half3(0.0);
                for (int i = 0; i < 16; i++) {
                    float a = float(i) * 0.3927;
                    float r = (i < 8) ? 0.019 : 0.039;
                    s += content.eval(p + float2(cos(a), sin(a)) * r * uSize.y).rgb;
                }
                bg = s / 16.0;
            } else bg = half3(uColor);
            return half4(mix(bg, c.rgb, half(m)), c.a);
        }
    """

    private class Live(var mask: Bitmap? = null, var busy: Boolean = false, var last: Long = 0L)
    private val live = WeakHashMap<View, Live>()
    private val segmenter by lazy {
        Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.STREAM_MODE).enableRawSizeMask().build())
    }
    private val entryBitmaps = WeakHashMap<MaskEntry, Bitmap>()
    private val blank: Bitmap by lazy { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { setPixel(0, 0, 0xFF000000.toInt()) } }

    /** The cut-out for [v] showing [bg]; [srcMs] = the shown frame's source time (Magic Mask). Call on the main thread. */
    fun effect(context: Context, v: View, bg: BgRemoval?, magic: MagicSel?, invert: Boolean, srcMs: Long): RenderEffect? {
        if (!supported || bg == null || v.width <= 0 || v.height <= 0) return null
        var rect = floatArrayOf(0f, 0f, 1f, 1f)
        val mask: Bitmap = if (magic != null) {
            val t = MaskTrack.load(context, magic.id) ?: return null
            val e = t.at(srcMs)
            if (e.empty) blank else {
                rect = e.rect
                entryBitmaps.getOrPut(e) {
                    val px = IntArray(e.bytes.size) { val g = e.bytes[it].toInt() and 255; (0xFF shl 24) or (g shl 16) or (g shl 8) or g }
                    Bitmap.createBitmap(px, e.w, e.h, Bitmap.Config.ARGB_8888)
                }
            }
        } else {
            val st = live.getOrPut(v) { Live() }
            requestPeople(v, st)
            st.mask ?: return null
        }
        val sh = try { RuntimeShader(AGSL) } catch (_: Throwable) { return null }
        sh.setInputShader("mask", BitmapShader(mask, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR })
        sh.setFloatUniform("uSize", v.width.toFloat(), v.height.toFloat())
        sh.setFloatUniform("uMaskSize", mask.width.toFloat(), mask.height.toFloat())
        sh.setFloatUniform("uRect", rect[0], rect[1], rect[2], rect[3])
        sh.setFloatUniform("uMode", bg.mode.toFloat())
        sh.setFloatUniform("uInvert", if (invert) 1f else 0f)
        sh.setFloatUniform("uColor", ((bg.color shr 16) and 255) / 255f, ((bg.color shr 8) and 255) / 255f, (bg.color and 255) / 255f)
        return RenderEffect.createRuntimeShaderEffect(sh, "content")
    }

    /** People: segment the shown picture now and then (about 15 times a second). */
    private fun requestPeople(v: View, st: Live) {
        val tv = v as? TextureView ?: return
        val now = System.currentTimeMillis()
        if (st.busy || now - st.last < 66) return
        val w = 256; val h = (256f * tv.height / tv.width.coerceAtLeast(1)).toInt().coerceIn(16, 512)
        val bmp = try { tv.getBitmap(w, h) } catch (_: Throwable) { null } ?: return
        st.busy = true; st.last = now
        segmenter.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { m ->
                try {
                    val fb = m.buffer.order(ByteOrder.nativeOrder()); fb.rewind()
                    val px = IntArray(m.width * m.height) { val g = ((if (fb.remaining() >= 4) fb.float else 0f).coerceIn(0f, 1f) * 255f).toInt(); (0xFF shl 24) or (g shl 16) or (g shl 8) or g }
                    st.mask = Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
                } catch (_: Throwable) {}
                st.busy = false
                v.postInvalidateOnAnimation()
            }
            .addOnFailureListener { st.busy = false }
    }

    /** The background setting of a clip as the preview should show it (nothing while a Magic Mask isn't tracked yet). */
    fun forClip(context: Context, v: View, c: Clip, srcMs: Long): RenderEffect? {
        val bg = c.bg ?: return null
        val m = bg.magic
        if (m != null && m.frames <= 0) return null
        return effect(context, v, bg, m, bg.invert == true, srcMs)
    }
}
