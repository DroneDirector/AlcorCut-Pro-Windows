package androidx.heifwriter

/** HEIC saving with Windows' own HEIF encoder (see WinEncode). */
class HeifWriter private constructor(private val path: String) : AutoCloseable {
    class Builder(private val path: String, w: Int, h: Int, mode: Int) {
        fun setQuality(q: Int) = this
        fun setMaxImages(n: Int) = this
        fun setGridEnabled(b: Boolean) = this
        fun build(): HeifWriter = HeifWriter(path)
    }
    private var bmp: android.graphics.Bitmap? = null
    fun start() {}
    fun addBitmap(b: android.graphics.Bitmap) { if (bmp == null) bmp = b }
    fun stop(ms: Long) { bmp?.let { com.dronedirector.veyrocutpro.editor.WinEncode.heic(it, java.io.File(path)) } }
    override fun close() { bmp = null }
    companion object { const val INPUT_MODE_BITMAP = 2; const val INPUT_MODE_SURFACE = 1; const val INPUT_MODE_BUFFER = 0 }
}
