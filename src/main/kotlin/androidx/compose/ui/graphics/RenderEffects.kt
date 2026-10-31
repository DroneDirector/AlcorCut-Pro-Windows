package androidx.compose.ui.graphics

/**
 * The phone's RenderEffect as a Compose one (desktop Compose takes Skia image filters): runtime
 * shaders, blur, chains and colour filters. Own code.
 */
fun android.graphics.RenderEffect.asComposeRenderEffect(): RenderEffect = toSkia().asComposeRenderEffect()

internal fun android.graphics.RenderEffect.toSkia(): org.jetbrains.skia.ImageFilter = when (kind) {
    0 -> {
        val sh = shader!!
        for ((name, s) in sh.children) if (name != input) android.graphics.ShaderBridge.child(sh, name, s)
        org.jetbrains.skia.ImageFilter.makeRuntimeShader(sh.builder, input!!, null)
    }
    1 -> org.jetbrains.skia.ImageFilter.makeBlur(blurX, blurY, org.jetbrains.skia.FilterTileMode.CLAMP, null, null)
    2 -> org.jetbrains.skia.ImageFilter.makeCompose(outer!!.toSkia(), inner!!.toSkia())
    3 -> org.jetbrains.skia.ImageFilter.makeColorFilter(android.graphics.ShaderBridge.colorFilter(colorFilter!!), null, null)
    else -> org.jetbrains.skia.ImageFilter.makeOffset(blurX, blurY, null, null)
}
