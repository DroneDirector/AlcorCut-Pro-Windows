package android.os

import java.io.File
import java.io.FileDescriptor
import java.io.RandomAccessFile

/** An open file. Own code. */
class ParcelFileDescriptor private constructor(val file: File, private val raf: RandomAccessFile) : java.io.Closeable {
    val fileDescriptor: FileDescriptor get() = raf.fd.also { androidx.exifinterface.media.FileLinks.link(it, file) }
    val statSize: Long get() = file.length()
    override fun close() = raf.close()
    fun detachFd(): Int = -1

    companion object {
        const val MODE_READ_ONLY = 0x10000000
        const val MODE_READ_WRITE = 0x30000000
        fun open(f: File, mode: String): ParcelFileDescriptor = ParcelFileDescriptor(f, RandomAccessFile(f, if ('w' in mode) "rw" else "r"))
        fun open(f: File, mode: Int): ParcelFileDescriptor = ParcelFileDescriptor(f, RandomAccessFile(f, if (mode == MODE_READ_ONLY) "r" else "rw"))
    }
}
