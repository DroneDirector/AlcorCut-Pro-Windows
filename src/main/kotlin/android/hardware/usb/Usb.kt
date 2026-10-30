package android.hardware.usb

/** USB devices: none are offered on Windows (cameras show up as drives). Own code. */
class UsbInterface { val interfaceClass: Int = 0 }
class UsbDevice {
    val interfaceCount: Int = 0
    fun getInterface(i: Int): UsbInterface = UsbInterface()
}
class UsbDeviceConnection { fun close() {} }
class UsbManager {
    val deviceList: HashMap<String, UsbDevice> = HashMap()
    fun hasPermission(d: UsbDevice): Boolean = false
    fun openDevice(d: UsbDevice): UsbDeviceConnection? = null
    fun requestPermission(d: UsbDevice, pi: android.app.PendingIntent) {}
    companion object { const val EXTRA_PERMISSION_GRANTED = "permission" }
}
object UsbConstants { const val USB_CLASS_STILL_IMAGE = 6 }
