package com.google.mlkit.vision.common

class InputImage private constructor(val bitmap: android.graphics.Bitmap) {
    companion object {
        const val IMAGE_FORMAT_NV21 = 17
        fun fromBitmap(b: android.graphics.Bitmap, rotation: Int) = InputImage(rotate(b, rotation))
        /** A camera picture (NV21: the brightness plane is used, as grey), turned upright by [rotation]. */
        fun fromByteArray(data: ByteArray, w: Int, h: Int, rotation: Int, format: Int): InputImage {
            val px = IntArray(w * h) { i -> val l = data[i].toInt() and 0xFF; (0xFF shl 24) or (l shl 16) or (l shl 8) or l }
            return InputImage(rotate(android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888), rotation))
        }
        private fun rotate(b: android.graphics.Bitmap, r: Int): android.graphics.Bitmap =
            if (r % 360 == 0) b else android.graphics.Bitmap.createBitmap(b, 0, 0, b.width, b.height, android.graphics.Matrix().apply { postRotate(r.toFloat()) }, true)
    }
}
