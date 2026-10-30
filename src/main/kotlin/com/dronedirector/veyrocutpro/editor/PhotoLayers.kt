package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.GradeIO
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Photo layers: other photos or textures on top of the photo (a sky, a texture, a second exposure, a logo
 * plate…), each with a blend mode, opacity, size and position. Kept with the edit (as picture layers in the
 * photo's layer list) and drawn at full size on export.
 */
object PhotoLayers {
    private val cache = object : android.util.LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun isPhotoLayer(l: Layer) = l.kind == LayerKind.IMAGE && l.blend != null

    private fun picture(c: Context, uri: Uri, longSide: Int): Bitmap? {
        val bucket = when { longSide <= 1200 -> 1200; longSide <= 2400 -> 2400; else -> 4800 }
        val key = "$uri@$bucket"
        cache.get(key)?.let { return it }
        return GradeIO.decode(c, uri, bucket)?.also { cache.put(key, it) }
    }

    private fun paintFor(b: LayerBlend, opacity: Float) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha = (opacity * 255).toInt().coerceIn(0, 255)
        if (Build.VERSION.SDK_INT >= 29) blendMode = when (b) {
            LayerBlend.NORMAL -> android.graphics.BlendMode.SRC_OVER
            LayerBlend.SCREEN -> android.graphics.BlendMode.SCREEN
            LayerBlend.MULTIPLY -> android.graphics.BlendMode.MULTIPLY
            LayerBlend.OVERLAY -> android.graphics.BlendMode.OVERLAY
            LayerBlend.ADD -> android.graphics.BlendMode.PLUS
            LayerBlend.LIGHTEN -> android.graphics.BlendMode.LIGHTEN
            LayerBlend.DARKEN -> android.graphics.BlendMode.DARKEN
            LayerBlend.SOFT_LIGHT -> android.graphics.BlendMode.SOFT_LIGHT
            LayerBlend.DIFFERENCE -> android.graphics.BlendMode.DIFFERENCE
        } else when (b) {
            LayerBlend.SCREEN -> xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            LayerBlend.MULTIPLY -> xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            LayerBlend.OVERLAY -> xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
            LayerBlend.ADD -> xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            LayerBlend.LIGHTEN -> xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN)
            LayerBlend.DARKEN -> xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
            else -> {}
        }
    }

    /** [l] on [src]: its height is [Layer.size] × the photo's height, centred at (x, y). */
    fun composite(c: Context, src: Bitmap, l: Layer): Bitmap {
        val uri = l.imageUri ?: return src
        val pic = picture(c, uri, (maxOf(src.width, src.height) * maxOf(1f, l.size)).toInt()) ?: return src
        val out = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true)
        val h = src.height * l.size; val w = h * pic.width / pic.height
        val cx = l.x * src.width; val cy = l.y * src.height
        Canvas(out).drawBitmap(pic, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), paintFor(l.blend ?: LayerBlend.NORMAL, l.opacity))
        return out
    }

    /** Copies the picked picture into the app (so the edit keeps working if the original moves). */
    fun keep(c: Context, uri: Uri): Uri? = try {
        val dir = File(c.filesDir, "photo_layers").apply { mkdirs() }
        val f = File(dir, "layer_${System.currentTimeMillis()}.jpg")
        val b = GradeIO.decode(c, uri, 4800) ?: throw IllegalStateException()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        Uri.fromFile(f)
    } catch (_: Throwable) { null }

    /** The size that fills the whole photo (height share) for a layer picture of aspect [layerAsp] on a photo of [photoAsp]. */
    fun fillSize(photoAsp: Float, layerAsp: Float): Float = if (layerAsp < photoAsp) photoAsp / layerAsp else 1f
}

/** Text tab → PHOTO LAYERS: add pictures on top with a blend mode; each one's controls. */
@Composable
fun PhotoLayersSection(layers: List<Layer>, photoAspect: Float, onChange: (List<Layer>) -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var sel by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val kept = withContext(Dispatchers.IO) { PhotoLayers.keep(c, u) }
            val asp = withContext(Dispatchers.IO) { GradeIO.decode(c, u, 256)?.let { it.width.toFloat() / it.height } } ?: 1.5f
            busy = false
            if (kept != null) {
                val l = Layer(kind = LayerKind.IMAGE, text = "", imageUri = kept, size = PhotoLayers.fillSize(photoAspect, asp), x = 0.5f, y = 0.5f,
                    opacity = 0.6f, blend = LayerBlend.SOFT_LIGHT, wholeVideo = true, name = "Photo layer ${layers.count { PhotoLayers.isPhotoLayer(it) } + 1}", mediaAspect = asp)
                onChange(layers + l); sel = l.id
            }
        }
    }
    val mine = layers.filter { PhotoLayers.isPhotoLayer(it) }
    SectionTitle("PHOTO LAYERS") { onChange(layers.filterNot { PhotoLayers.isPhotoLayer(it) }) }
    Text("Put another photo or a texture on top — a sky, film dust, paper, a second exposure — and choose how it mixes in (blend mode) and how strongly.", color = Pro.Dim, fontSize = 11.sp)
    PillChip(if (busy) "Adding…" else "+ ADD PHOTO LAYER", false, accentText = true) { if (!busy) try { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } catch (_: Throwable) {} }
    mine.forEach { l ->
        val on = l.id == sel
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Pro.Amber.copy(alpha = 0.16f) else Ed.Raised).clickable { sel = if (on) null else l.id }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text((l.name ?: "Photo layer") + " · " + (l.blend ?: LayerBlend.NORMAL).label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(if (l.visible) "👁" else "—", color = Color.White, modifier = Modifier.clickable { onChange(layers.map { if (it.id == l.id) it.copy(visible = !it.visible) else it }) }.padding(horizontal = 8.dp))
            Text("✕", color = Pro.Red, fontSize = 13.sp, modifier = Modifier.clickable { onChange(layers.filter { it.id != l.id }) }.padding(horizontal = 6.dp))
        }
        if (on) Column {
            fun upd(n: Layer) = onChange(layers.map { if (it.id == l.id) n else it })
            Text("Blend", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LayerBlend.entries.forEach { b -> PillChip(b.label, l.blend == b) { upd(l.copy(blend = b)) } }
            }
            EdSlider("Opacity", l.opacity, 0f..1f, 0.6f, ::pct) { upd(l.copy(opacity = it)) }
            EdSlider("Size", l.size, 0.1f..3f, PhotoLayers.fillSize(photoAspect, l.mediaAspect), ::pct) { upd(l.copy(size = it)) }
            EdSlider("Left / right", l.x, 0f..1f, 0.5f, ::pct) { upd(l.copy(x = it)) }
            EdSlider("Up / down", l.y, 0f..1f, 0.5f, ::pct) { upd(l.copy(y = it)) }
            PillChip("Fill the photo", false) { upd(l.copy(size = PhotoLayers.fillSize(photoAspect, l.mediaAspect), x = 0.5f, y = 0.5f)) }
        }
    }
}
