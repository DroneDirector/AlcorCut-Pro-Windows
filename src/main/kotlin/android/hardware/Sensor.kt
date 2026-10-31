package android.hardware

/**
 * Motion sensors on a PC: the gravity direction comes from the tablet's or 2-in-1's own accelerometer,
 * or from the phone while it's the PC's camera ([Motion.phoneRoll]); the level uses it. No gyro.
 */
class Sensor private constructor(val type: Int) {
    companion object {
        const val TYPE_ACCELEROMETER = 1; const val TYPE_GYROSCOPE = 4; const val TYPE_GRAVITY = 9; const val TYPE_ROTATION_VECTOR = 11; const val TYPE_GAME_ROTATION_VECTOR = 15
        internal val gravity = Sensor(TYPE_GRAVITY)
    }
}
class SensorEvent(val values: FloatArray, val timestamp: Long, val sensor: Sensor?)
interface SensorEventListener {
    fun onSensorChanged(event: SensorEvent)
    fun onAccuracyChanged(sensor: Sensor?, accuracy: Int)
}

/** Where the PC's gravity reading comes from. */
object Motion {
    /** The phone's tilt in degrees while it's the PC's camera (null = not connected or lying flat). */
    @Volatile var phoneRoll: Float? = null
    @Volatile var phoneLinked = false
    private val pcSensor: Boolean by lazy { try { com.dronedirector.veyrocutpro.windows.MfNative.ok && com.dronedirector.veyrocutpro.windows.MfNative.accel() != null } catch (_: Throwable) { false } }

    /** Whether there's a level reading at all (a phone linked, or a PC with a motion sensor). */
    val available: Boolean get() = phoneLinked || pcSensor

    /** Gravity in m/s² on the phone's axes (x right, y up, z towards you), or null. */
    fun gravity(): FloatArray? {
        if (phoneLinked) {
            val r = phoneRoll ?: return floatArrayOf(0f, 0f, 9.81f)     // flat: the level hides
            val a = Math.toRadians(r.toDouble())
            return floatArrayOf((-Math.sin(a) * 9.81).toFloat(), (Math.cos(a) * 9.81).toFloat(), 0f)
        }
        if (!pcSensor) return null
        // Windows reports in g with the opposite sign (upright: y = −1).
        val g = try { com.dronedirector.veyrocutpro.windows.MfNative.accel() } catch (_: Throwable) { null } ?: return null
        return floatArrayOf(-g[0] * 9.81f, -g[1] * 9.81f, -g[2] * 9.81f)
    }
}

class SensorManager {
    private val running = java.util.concurrent.ConcurrentHashMap<SensorEventListener, Thread>()

    fun getDefaultSensor(type: Int): Sensor? =
        if ((type == Sensor.TYPE_GRAVITY || type == Sensor.TYPE_ACCELEROMETER) && Motion.available) Sensor.gravity else null

    fun registerListener(l: SensorEventListener, s: Sensor?, rate: Int): Boolean {
        if (s == null || running.containsKey(l)) return s != null
        val t = Thread({
            while (running[l] === Thread.currentThread()) {
                val g = Motion.gravity()
                if (g != null) javax.swing.SwingUtilities.invokeLater { if (running.containsKey(l)) l.onSensorChanged(SensorEvent(g, System.nanoTime(), s)) }
                try { Thread.sleep(if (rate == SENSOR_DELAY_UI || rate == SENSOR_DELAY_NORMAL) 66 else 20) } catch (_: InterruptedException) { break }
            }
        }, "motion").apply { isDaemon = true }
        running[l] = t
        t.start()
        return true
    }
    fun registerListener(l: SensorEventListener, s: Sensor?, rate: Int, h: android.os.Handler?): Boolean = registerListener(l, s, rate)
    fun unregisterListener(l: SensorEventListener) { running.remove(l)?.interrupt() }
    companion object {
        const val SENSOR_DELAY_FASTEST = 0; const val SENSOR_DELAY_GAME = 1; const val SENSOR_DELAY_UI = 2; const val SENSOR_DELAY_NORMAL = 3
        fun getRotationMatrixFromVector(r: FloatArray, v: FloatArray) {}
        fun getOrientation(r: FloatArray, values: FloatArray): FloatArray = values
    }
}
