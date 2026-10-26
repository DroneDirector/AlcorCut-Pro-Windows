package com.dronedirector.veyrocutpro.editor

/**
 * Reads camera RAW formats other than DNG (Sony ARW, Canon CR2 / CR3, Nikon NEF, Fujifilm RAF,
 * Olympus ORF, Panasonic RW2, Pentax PEF…) through LibRaw — a separate library (libraw.so,
 * LGPL 2.1, © LibRaw LLC) loaded at run time. The app's own RAW pipeline does the developing.
 */
internal object LibRawBridge {
    /** The library loaded (false if it's missing — then only DNG is supported). */
    val available: Boolean by lazy {
        try { System.loadLibrary("raw"); System.loadLibrary("veyroraw"); true } catch (_: Throwable) { false }
    }

    /** JPEG XL compressed DNGs can be decoded (libjxl, shipped with the app's JPEG XL export). */
    val jxlAvailable: Boolean by lazy {
        available && run {
            try { System.loadLibrary("jxl") } catch (_: Throwable) {}
            try { nJxlInit() } catch (_: Throwable) { false }
        }
    }

    /** File name endings LibRaw is asked to open. */
    val EXTENSIONS = setOf(
        "arw", "srf", "sr2", "cr2", "cr3", "crw", "nef", "nrw", "raf", "orf", "rw2", "rwl", "pef", "srw",
        "3fr", "fff", "iiq", "mos", "erf", "kdc", "dcr", "mrw", "mef", "x3f", "raw", "rwz", "cap", "gpr",
    )

    @JvmStatic external fun nOpen(path: String): Long
    @JvmStatic external fun nClose(h: Long)
    @JvmStatic external fun nUnpack(h: Long): Int
    @JvmStatic external fun nStrings(h: Long): Array<String>
    @JvmStatic external fun nInfo(h: Long): DoubleArray
    @JvmStatic external fun nPattern(h: Long, n: Int): IntArray
    @JvmStatic external fun nRaw(h: Long, one: Int): ShortArray?
    @JvmStatic external fun nThumb(h: Long): ByteArray?
    @JvmStatic external fun nVersion(): String
    @JvmStatic external fun nJxlInit(): Boolean
    @JvmStatic external fun nJxl(data: ByteArray, channels: Int, wh: IntArray): ShortArray?
}
