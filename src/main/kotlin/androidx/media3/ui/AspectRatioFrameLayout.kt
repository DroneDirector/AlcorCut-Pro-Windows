package androidx.media3.ui

/** A frame keeping its content at an aspect ratio (fit inside, or zoomed to fill). */
class AspectRatioFrameLayout(context: android.content.Context? = null) : android.widget.FrameLayout(context) {
    var aspect = 0f; private set
    var resizeMode = RESIZE_MODE_FIT; set(v) { field = v; invalidate() }
    fun setAspectRatio(a: Float) { if (a != aspect) { aspect = a; invalidate() } }
    companion object { const val RESIZE_MODE_FIT = 0; const val RESIZE_MODE_FIXED_WIDTH = 1; const val RESIZE_MODE_FIXED_HEIGHT = 2; const val RESIZE_MODE_FILL = 3; const val RESIZE_MODE_ZOOM = 4 }
}
