package android.text.format

/** Dates and file sizes as text. */
object DateFormat {
    fun format(pattern: CharSequence, millis: Long): CharSequence = java.text.SimpleDateFormat(pattern.toString(), java.util.Locale.getDefault()).format(java.util.Date(millis))
    fun format(pattern: CharSequence, d: java.util.Date): CharSequence = java.text.SimpleDateFormat(pattern.toString(), java.util.Locale.getDefault()).format(d)
    fun is24HourFormat(c: android.content.Context?): Boolean = true
}
object Formatter {
    fun formatShortFileSize(c: android.content.Context?, bytes: Long): String = formatFileSize(c, bytes)
    fun formatFileSize(c: android.content.Context?, bytes: Long): String {
        val u = listOf("B", "kB", "MB", "GB", "TB"); var v = bytes.toDouble(); var i = 0
        while (v >= 1000 && i < u.size - 1) { v /= 1000; i++ }
        return if (i == 0) "$bytes B" else String.format(java.util.Locale.US, if (v < 10) "%.2f %s" else if (v < 100) "%.1f %s" else "%.0f %s", v, u[i])
    }
}
