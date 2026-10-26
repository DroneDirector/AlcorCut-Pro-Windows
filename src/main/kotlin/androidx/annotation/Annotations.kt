package androidx.annotation

annotation class RequiresApi(val value: Int = 0, val api: Int = 0)
annotation class OptIn(vararg val markerClass: kotlin.reflect.KClass<*>)
annotation class Keep
annotation class WorkerThread
annotation class MainThread
annotation class UiThread
annotation class ColorInt
annotation class FloatRange(val from: Double = 0.0, val to: Double = 1.0)
annotation class IntRange(val from: Long = 0, val to: Long = Long.MAX_VALUE)
