package android.system

import java.io.FileDescriptor

/** Reading and writing at a position in an open file. Own code. */
object Os {
    private fun raf(fd: FileDescriptor, mode: String): java.io.RandomAccessFile {
        val f = androidx.exifinterface.media.FileLinks.fileOf(fd) ?: throw java.io.IOException("Unknown file")
        return java.io.RandomAccessFile(f, mode)
    }
    fun pread(fd: FileDescriptor, buf: ByteArray, off: Int, n: Int, pos: Long): Int = raf(fd, "r").use { it.seek(pos); it.read(buf, off, n) }
    fun pwrite(fd: FileDescriptor, buf: ByteArray, off: Int, n: Int, pos: Long): Int = raf(fd, "rw").use { it.seek(pos); it.write(buf, off, n); n }
    fun fstat(fd: FileDescriptor): StructStat = StructStat(androidx.exifinterface.media.FileLinks.fileOf(fd)?.length() ?: 0L)
}
class StructStat(val st_size: Long)
