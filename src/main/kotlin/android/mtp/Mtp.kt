package android.mtp

/** Cameras over USB (MTP/PTP): on Windows, open the camera's card as a drive instead. Own code. */
class MtpObjectInfo {
    val format: Int = 0
    val name: String? = null
    val compressedSizeLong: Long = 0
    val dateCreated: Long = 0
    val dateModified: Long = 0
}

class MtpDevice(dev: android.hardware.usb.UsbDevice) {
    val storageIds: IntArray? = null
    fun open(c: android.hardware.usb.UsbDeviceConnection): Boolean = false
    fun close() {}
    fun getObjectHandles(storage: Int, format: Int, parent: Int): IntArray? = null
    fun getObjectInfo(h: Int): MtpObjectInfo? = null
    fun importFile(h: Int, path: String): Boolean = false
}

object MtpConstants { const val FORMAT_ASSOCIATION = 0x3001 }
