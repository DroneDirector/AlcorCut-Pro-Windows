package android.hardware

/** Motion sensors: PCs have none, so the level and gyro features stay off. */
class Sensor private constructor() {
    companion object { const val TYPE_ACCELEROMETER = 1; const val TYPE_GYROSCOPE = 4; const val TYPE_GRAVITY = 9; const val TYPE_ROTATION_VECTOR = 11; const val TYPE_GAME_ROTATION_VECTOR = 15 }
}
class SensorEvent(val values: FloatArray, val timestamp: Long, val sensor: Sensor?)
interface SensorEventListener {
    fun onSensorChanged(event: SensorEvent)
    fun onAccuracyChanged(sensor: Sensor?, accuracy: Int)
}
class SensorManager {
    fun getDefaultSensor(type: Int): Sensor? = null
    fun registerListener(l: SensorEventListener, s: Sensor?, rate: Int): Boolean = false
    fun registerListener(l: SensorEventListener, s: Sensor?, rate: Int, h: android.os.Handler?): Boolean = false
    fun unregisterListener(l: SensorEventListener) {}
    companion object {
        const val SENSOR_DELAY_FASTEST = 0; const val SENSOR_DELAY_GAME = 1; const val SENSOR_DELAY_UI = 2; const val SENSOR_DELAY_NORMAL = 3
        fun getRotationMatrixFromVector(r: FloatArray, v: FloatArray) {}
        fun getOrientation(r: FloatArray, values: FloatArray): FloatArray = values
    }
}
