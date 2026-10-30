package android.os

/** Power and temperature: a PC is plugged in and keeps itself cool, so these read as "fine". */
class PowerManager {
    val currentThermalStatus: Int get() = THERMAL_STATUS_NONE
    val isPowerSaveMode: Boolean get() = false
    companion object { const val THERMAL_STATUS_NONE = 0; const val THERMAL_STATUS_LIGHT = 1; const val THERMAL_STATUS_MODERATE = 2; const val THERMAL_STATUS_SEVERE = 3; const val THERMAL_STATUS_CRITICAL = 4 }
}
class BatteryManager {
    fun getIntProperty(id: Int): Int = if (id == BATTERY_PROPERTY_CAPACITY) battery() else Int.MIN_VALUE
    val isCharging: Boolean get() = true
    companion object {
        const val BATTERY_PROPERTY_CAPACITY = 4
        /** A laptop's battery level from Windows (100 on a desktop or when unknown). */
        fun battery(): Int = try {
            if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) 100 else {
                val p = ProcessBuilder("powershell.exe", "-NoProfile", "-Command", "(Get-CimInstance Win32_Battery | Select-Object -First 1).EstimatedChargeRemaining").redirectErrorStream(true).start()
                val t = p.inputStream.bufferedReader().readText().trim(); p.waitFor()
                t.toIntOrNull() ?: 100
            }
        } catch (_: Throwable) { 100 }
    }
}
