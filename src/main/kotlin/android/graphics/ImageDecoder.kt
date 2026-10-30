package android.graphics

/** Picture decoding with a target size, as the phone's ImageDecoder (on top of BitmapFactory). Own code. */
class ImageDecoder private constructor() {
    class Source internal constructor(val open: () -> java.io.InputStream?)
    class ImageInfo(val size: android.util.Size, val mimeType: String = "image/*")
    var allocator = ALLOCATOR_DEFAULT
    var isMutableRequired = false
    internal var tw = 0; internal var th = 0
    fun setTargetSize(w: Int, h: Int) { tw = w; th = h }
    fun setTargetSampleSize(n: Int) {}
    fun interface OnHeaderDecodedListener { fun onHeaderDecoded(decoder: ImageDecoder, info: ImageInfo, source: Source) }

    companion object {
        const val ALLOCATOR_DEFAULT = 0; const val ALLOCATOR_SOFTWARE = 1; const val ALLOCATOR_SHARED_MEMORY = 2; const val ALLOCATOR_HARDWARE = 3
        fun createSource(cr: android.content.ContentResolver, uri: android.net.Uri) = Source { cr.openInputStream(uri) }
        fun createSource(f: java.io.File) = Source { f.inputStream() }
        fun decodeBitmap(src: Source): Bitmap = decodeBitmap(src) { _, _, _ -> }
        fun decodeBitmap(src: Source, l: OnHeaderDecodedListener): Bitmap {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            src.open()?.use { BitmapFactory.decodeStream(it, null, o) }
            if (o.outWidth <= 0) throw java.io.IOException("Not a picture")
            val d = ImageDecoder()
            l.onHeaderDecoded(d, ImageInfo(android.util.Size(o.outWidth, o.outHeight), o.outMimeType ?: "image/*"), src)
            var sample = 1
            if (d.tw > 0) while (o.outWidth / (sample * 2) >= d.tw) sample *= 2
            val b = src.open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: throw java.io.IOException("Not a picture")
            return if (d.tw > 0 && d.th > 0 && (b.width != d.tw || b.height != d.th)) Bitmap.createScaledBitmap(b, d.tw, d.th, true) else b
        }
    }
}
