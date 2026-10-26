package android.content.res

import java.io.File

/** A file's size and contents. Own code. */
class AssetFileDescriptor(private val f: File) : java.io.Closeable {
    val length: Long get() = f.length()
    fun createInputStream(): java.io.InputStream = f.inputStream()
    override fun close() {}
}
