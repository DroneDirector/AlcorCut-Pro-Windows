package androidx.compose.ui.viewinterop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged

/** Phone views inside the screen: on Windows the viewfinder (PreviewView / SurfaceView) draws its latest picture. Own code. */
@Composable
fun <T : android.view.View> AndroidView(factory: (android.content.Context) -> T, modifier: Modifier = Modifier, update: (T) -> Unit = {}, onRelease: (T) -> Unit = {}) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val v = remember { factory(ctx) }
    update(v)
    Box(modifier.onSizeChanged { v.width = it.width; v.height = it.height }) {
        val f = (v as? androidx.camera.view.PreviewView)?.frame ?: (v as? android.view.SurfaceView)?.preview?.frame
        if (f != null) Image(f, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}
