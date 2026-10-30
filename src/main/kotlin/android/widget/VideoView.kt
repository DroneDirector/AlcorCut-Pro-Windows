package android.widget

/** Playing a recorded clip from the Capture gallery: on Windows it opens in the PC's video player. Own code. */
open class VideoView(context: android.content.Context? = null) : android.view.View() {
    private var uri: android.net.Uri? = null
    private var started = false
    fun setVideoURI(u: android.net.Uri?) { uri = u }
    fun setVideoPath(p: String) { uri = android.net.Uri.fromFile(java.io.File(p)) }
    fun setMediaController(c: MediaController?) {}
    fun setOnPreparedListener(l: (Any?) -> Unit) {}
    fun setOnCompletionListener(l: (Any?) -> Unit) {}
    fun setOnErrorListener(l: (Any?, Int, Int) -> Boolean) {}
    fun requestFocus(): Boolean = true
    fun start() {
        if (started) return
        started = true
        val f = uri?.path?.let { java.io.File(it) } ?: return
        try { java.awt.Desktop.getDesktop().open(f) } catch (_: Throwable) {}
    }
    fun pause() {}
    fun stopPlayback() {}
    fun seekTo(ms: Int) {}
    val isPlaying: Boolean get() = false
}
class MediaController(context: android.content.Context? = null) { fun setAnchorView(v: android.view.View?) {} }
