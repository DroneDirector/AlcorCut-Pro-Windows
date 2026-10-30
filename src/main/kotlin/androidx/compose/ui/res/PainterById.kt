package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity

/** painterResource(R.drawable.x) as on the phone: vector drawables load from the app's resources. */
@Composable
fun painterResource(id: Int): Painter {
    val path = com.dronedirector.veyrocutpro.R.path(id)
    val density = LocalDensity.current
    return if (path.endsWith(".xml")) {
        val v = remember(path) {
            Thread.currentThread().contextClassLoader.getResourceAsStream(path)!!.use { loadXmlImageVector(org.xml.sax.InputSource(it), density) }
        }
        androidx.compose.ui.graphics.vector.rememberVectorPainter(v)
    } else {
        val img = remember(path) { Thread.currentThread().contextClassLoader.getResourceAsStream(path)!!.use { androidx.compose.ui.graphics.painter.BitmapPainter(loadImageBitmap(it)) } }
        img
    }
}
