package androidx.heifwriter

/** HEIC saving needs the HEVC encoder; not on Windows yet. */
class HeifWriter private constructor() : AutoCloseable {
    class Builder(path: String, w: Int, h: Int, mode: Int) {
        fun setQuality(q: Int) = this
        fun setMaxImages(n: Int) = this
        fun setGridEnabled(b: Boolean) = this
        fun build(): HeifWriter = throw UnsupportedOperationException("HEIC saving isn't on Windows yet")
    }
    fun start() {}
    fun addBitmap(b: android.graphics.Bitmap) {}
    fun stop(ms: Long) {}
    override fun close() {}
    companion object { const val INPUT_MODE_BITMAP = 2; const val INPUT_MODE_SURFACE = 1; const val INPUT_MODE_BUFFER = 0 }
}
