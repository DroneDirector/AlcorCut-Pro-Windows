package com.google.mlkit.vision.common

class InputImage private constructor(val bitmap: android.graphics.Bitmap) {
    companion object { fun fromBitmap(b: android.graphics.Bitmap, rotation: Int) = InputImage(b) }
}
