package androidx.media3.common.util

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
annotation class UnstableApi

class Size(val width: Int, val height: Int) {
    private val w get() = width
    private val h get() = height
    override fun equals(other: Any?) = other is Size && other.width == w && other.height == h
    override fun hashCode() = w * 31 + h
    override fun toString() = "${w}x$h"
    companion object { @JvmField val UNKNOWN = Size(-1, -1); @JvmField val ZERO = Size(0, 0) }
}

interface Clock {
    fun currentTimeMillis(): Long
    fun elapsedRealtime(): Long
    companion object { @JvmField val DEFAULT: Clock = object : Clock { override fun currentTimeMillis() = System.currentTimeMillis(); override fun elapsedRealtime() = System.nanoTime() / 1_000_000 } }
}
