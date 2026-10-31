package android.media

/** The phone's simple face finder, on the app's face model (YuNet, ONNX). Own code. */
class FaceDetector(private val width: Int, private val height: Int, private val max: Int) {
    class Face internal constructor(private val cx: Float, private val cy: Float, private val dist: Float, private val conf: Float) {
        fun getMidPoint(p: android.graphics.PointF) { p.x = cx; p.y = cy }
        fun eyesDistance() = dist
        fun confidence() = conf
        companion object { const val CONFIDENCE_THRESHOLD = 0.4f }
    }
    fun findFaces(b: android.graphics.Bitmap, out: Array<Face?>): Int {
        val found = try { com.google.mlkit.vision.face.YuNet.detect(b, 0.7f, 0.05f) } catch (_: Throwable) { emptyList() }
        var n = 0
        for (f in found) {
            if (n >= out.size || n >= max) break
            val r = f.boundingBox
            out[n++] = Face(r.exactCenterX(), r.exactCenterY(), r.width() * 0.4f, 0.9f)
        }
        return n
    }
}
