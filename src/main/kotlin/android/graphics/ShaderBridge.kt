package android.graphics

/** Phone shaders as Skia ones (for runtime shader children and filters). Own code. */
object ShaderBridge {
    fun child(sh: RuntimeShader, name: String, s: Shader) { toSkia(s)?.let { sh.builder.child(name, it) } }

    fun toSkia(s: Shader): org.jetbrains.skia.Shader? = when (s) {
        is RuntimeShader -> { for ((n, c) in s.children) child(s, n, c); s.builder.makeShader(null) }
        is BitmapShader -> {
            val b = s.bitmap
            val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
            val bytes = java.nio.ByteBuffer.allocate(px.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).also { it.asIntBuffer().put(px) }.array()
            val img = org.jetbrains.skia.Image.makeRaster(org.jetbrains.skia.ImageInfo.makeN32(b.width, b.height, org.jetbrains.skia.ColorAlphaType.UNPREMUL), bytes, b.width * 4)
            fun tm(t: Shader.TileMode) = when (t) { Shader.TileMode.REPEAT -> org.jetbrains.skia.FilterTileMode.REPEAT; Shader.TileMode.MIRROR -> org.jetbrains.skia.FilterTileMode.MIRROR; else -> org.jetbrains.skia.FilterTileMode.CLAMP }
            val m = s.localMatrix?.let { v -> FloatArray(9).also { v.getValues(it) }.let { org.jetbrains.skia.Matrix33(*it) } }
            val sampling = if (s.filterMode == BitmapShader.FILTER_MODE_NEAREST) org.jetbrains.skia.SamplingMode.DEFAULT else org.jetbrains.skia.FilterMipmap(org.jetbrains.skia.FilterMode.LINEAR, org.jetbrains.skia.MipmapMode.NONE)
            img.makeShader(tm(s.tileX), tm(s.tileY), sampling, m)
        }
        is LinearGradient -> org.jetbrains.skia.Shader.makeLinearGradient(s.x0, s.y0, s.x1, s.y1, s.colors, s.positions)
        is RadialGradient -> org.jetbrains.skia.Shader.makeRadialGradient(s.cx, s.cy, s.radius, s.colors, s.positions)
        is SweepGradient -> org.jetbrains.skia.Shader.makeSweepGradient(s.cx, s.cy, s.colors, s.positions)
        else -> null
    }

    fun colorFilter(f: ColorFilter): org.jetbrains.skia.ColorFilter = when (f) {
        is ColorMatrixColorFilter -> {
            val m = f.m.copyOf(); for (r in 0 until 4) m[r * 5 + 4] = m[r * 5 + 4] / 255f
            org.jetbrains.skia.ColorFilter.makeMatrix(org.jetbrains.skia.ColorMatrix(*m))
        }
        is PorterDuffColorFilter -> org.jetbrains.skia.ColorFilter.makeBlend(f.color, org.jetbrains.skia.BlendMode.SRC_IN)
        else -> org.jetbrains.skia.ColorFilter.makeMatrix(org.jetbrains.skia.ColorMatrix(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
    }
}
