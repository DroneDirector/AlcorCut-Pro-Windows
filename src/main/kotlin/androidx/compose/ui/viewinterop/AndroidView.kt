package androidx.compose.ui.viewinterop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Phone views inside the screen. On Windows the views are drawn here: the viewfinder (PreviewView /
 * SurfaceView) shows its latest picture, and view groups (FrameLayout, AspectRatioFrameLayout) with
 * video views (TextureView) are laid out and drawn with each view's alpha, position, scale, rotation
 * and effect, as the phone would. Own code.
 */
@Composable
fun <T : android.view.View> AndroidView(factory: (android.content.Context) -> T, modifier: Modifier = Modifier, update: (T) -> Unit = {}, onRelease: (T) -> Unit = {}) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val v = remember { factory(ctx) }
    androidx.compose.runtime.DisposableEffect(v) { onDispose { onRelease(v) } }
    update(v)
    Box(modifier.onSizeChanged { v.layoutTo(it.width, it.height) }) {
        val f = (v as? androidx.camera.view.PreviewView)?.frame ?: (v as? android.view.SurfaceView)?.preview?.frame
        if (f != null) Image(f, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else ViewNode(v, Modifier.matchParentSize())
    }
}

@Composable
private fun ViewNode(v: android.view.View, modifier: Modifier) {
    v.version.intValue                          // redraw when the view changes
    if (v.visibility != android.view.View.VISIBLE) return
    val px = with(LocalDensity.current) { 1.dp.toPx() }
    val layer = Modifier.graphicsLayer {
        alpha = v.alpha
        translationX = v.translationX; translationY = v.translationY
        scaleX = v.scaleX; scaleY = v.scaleY
        rotationZ = v.rotation
        if (!v.pivotX.isNaN() && !v.pivotY.isNaN() && size.width > 0f && size.height > 0f) transformOrigin = TransformOrigin(v.pivotX / size.width, v.pivotY / size.height)
        renderEffect = v.renderEffect?.let { try { it.asComposeRenderEffect() } catch (_: Throwable) { null } }
        shadowElevation = v.translationZ.coerceAtLeast(0f) * 0f
    }
    when (v) {
        is androidx.media3.ui.AspectRatioFrameLayout -> BoxWithConstraints(modifier.then(layer).onSizeChanged { v.layoutTo(it.width, it.height) }, contentAlignment = Alignment.Center) {
            val a = v.aspect
            val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
            val (cw, ch) = if (a <= 0f || w <= 0f || h <= 0f) w to h else {
                val zoom = v.resizeMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                val wide = w / h > a
                if (wide != zoom) (h * a) to h else w to (w / a)
            }
            val d = LocalDensity.current
            Box(Modifier.requiredSize(with(d) { cw.toDp() }, with(d) { ch.toDp() })) {
                for (c in v.children) ViewNode(c, Modifier.fillMaxSize())
            }
        }
        is android.view.ViewGroup -> Box(modifier.then(layer).onSizeChanged { v.layoutTo(it.width, it.height) }, contentAlignment = Alignment.Center) {
            for (c in v.children) ViewNode(c, Modifier.fillMaxSize())
        }
        is android.view.TextureView -> Box(modifier.then(layer).onSizeChanged { v.layoutTo(it.width, it.height) }.onGloballyPositioned { val p = it.localToWindow(androidx.compose.ui.geometry.Offset.Zero); v.screenX = p.x.toInt(); v.screenY = p.y.toInt() }) {
            val img = v.frame
            if (img != null) {
                val m = v.transformMatrix
                if (m.isIdentity) Image(img, null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
                else Canvas(Modifier.matchParentSize()) {
                    val vals = FloatArray(9); m.getValues(vals)
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.concat(org.jetbrains.skia.Matrix33(*vals))
                    drawImage(img, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()))
                    drawContext.canvas.nativeCanvas.restore()
                }
            }
        }
        else -> Box(modifier.then(layer).onSizeChanged { v.layoutTo(it.width, it.height) })
    }
}
