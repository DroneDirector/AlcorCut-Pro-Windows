package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import java.io.File

/** A 3D LUT (.cube): N×N×N RGB entries, red changing fastest (as in the .cube format). */
class CubeLut(val n: Int, val data: FloatArray, val domainMin: FloatArray, val domainMax: FloatArray) {
    /** Trilinear look-up of [c] (0..1 RGB) in place. */
    fun apply(c: FloatArray) {
        val m = n - 1
        fun idx(v: Float, i: Int) = (((v - domainMin[i]) / (domainMax[i] - domainMin[i])).coerceIn(0f, 1f) * m)
        val r = idx(c[0], 0); val g = idx(c[1], 1); val b = idx(c[2], 2)
        val r0 = r.toInt().coerceAtMost(m - 1).coerceAtLeast(0); val g0 = g.toInt().coerceAtMost(m - 1).coerceAtLeast(0); val b0 = b.toInt().coerceAtMost(m - 1).coerceAtLeast(0)
        val fr = r - r0; val fg = g - g0; val fb = b - b0
        for (ch in 0 until 3) {
            fun at(ri: Int, gi: Int, bi: Int) = data[((bi * n + gi) * n + ri) * 3 + ch]
            val c00 = at(r0, g0, b0) * (1 - fr) + at(r0 + 1, g0, b0) * fr
            val c10 = at(r0, g0 + 1, b0) * (1 - fr) + at(r0 + 1, g0 + 1, b0) * fr
            val c01 = at(r0, g0, b0 + 1) * (1 - fr) + at(r0 + 1, g0, b0 + 1) * fr
            val c11 = at(r0, g0 + 1, b0 + 1) * (1 - fr) + at(r0 + 1, g0 + 1, b0 + 1) * fr
            val c0 = c00 * (1 - fg) + c10 * fg
            val c1 = c01 * (1 - fg) + c11 * fg
            c[ch] = (c0 * (1 - fb) + c1 * fb).coerceIn(0f, 1f)
        }
    }

    /**
     * The LUT resampled to [size]³ as a 2D strip for the GPU: width size×size (one slice per blue
     * step), height size; ARGB pixels.
     */
    fun strip(size: Int = 33): IntArray {
        val out = IntArray(size * size * size)
        val c = FloatArray(3)
        for (bi in 0 until size) for (gi in 0 until size) for (ri in 0 until size) {
            c[0] = lerpDomain(ri / (size - 1f), 0); c[1] = lerpDomain(gi / (size - 1f), 1); c[2] = lerpDomain(bi / (size - 1f), 2)
            apply(c)
            out[gi * size * size + bi * size + ri] = (0xFF shl 24) or ((c[0] * 255f + 0.5f).toInt() shl 16) or ((c[1] * 255f + 0.5f).toInt() shl 8) or (c[2] * 255f + 0.5f).toInt()
        }
        return out
    }

    private fun lerpDomain(t: Float, i: Int) = domainMin[i] + (domainMax[i] - domainMin[i]) * t

    companion object {
        /** Parses .cube text (the common 3D LUT file format). */
        fun parse(text: String): CubeLut {
            var n = 0
            val min = floatArrayOf(0f, 0f, 0f); val max = floatArrayOf(1f, 1f, 1f)
            val values = ArrayList<Float>(35937 * 3)
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split(Regex("\\s+"))
                when (parts[0].uppercase()) {
                    "TITLE" -> {}
                    "LUT_3D_SIZE" -> n = parts[1].toInt()
                    "LUT_1D_SIZE" -> throw IllegalArgumentException("1D LUTs aren't supported — use a 3D .cube file")
                    "DOMAIN_MIN" -> for (i in 0..2) min[i] = parts[i + 1].toFloat()
                    "DOMAIN_MAX" -> for (i in 0..2) max[i] = parts[i + 1].toFloat()
                    "LUT_3D_INPUT_RANGE" -> { for (i in 0..2) { min[i] = parts[1].toFloat(); max[i] = parts[2].toFloat() } }
                    else -> if (parts.size >= 3 && (parts[0][0].isDigit() || parts[0][0] == '-' || parts[0][0] == '.')) {
                        values += parts[0].toFloat(); values += parts[1].toFloat(); values += parts[2].toFloat()
                    }
                }
            }
            require(n in 2..129) { "Not a 3D .cube LUT" }
            require(values.size >= n * n * n * 3) { "The LUT file is incomplete" }
            return CubeLut(n, values.toFloatArray().copyOf(n * n * n * 3), min, max)
        }
    }
}

/** A LUT the user imported. */
data class UserLut(val id: String, val name: String)

/**
 * Your own LUTs (.cube), imported once and kept by the app (files/luts). Used by the photo
 * editor, the video editor (as a creative look or as the log → Rec.709 input) and Capture
 * (monitoring look).
 */
object LutLibrary {
    private var app: Context? = null
    private val cache = HashMap<String, CubeLut?>()
    val luts = mutableStateListOf<UserLut>()

    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        refresh()
    }

    private fun dir(): File? = app?.let { File(it.filesDir, "luts").apply { mkdirs() } }

    fun refresh() {
        val d = dir() ?: return
        val list = d.listFiles { f -> f.name.endsWith(".cube") }?.sortedBy { it.lastModified() }?.map { f ->
            UserLut(f.nameWithoutExtension, File(d, f.nameWithoutExtension + ".name").takeIf { it.exists() }?.readText() ?: f.nameWithoutExtension)
        } ?: emptyList()
        luts.clear(); luts.addAll(list)
    }

    /** Copies a .cube file into the app. Returns the new LUT or throws with a readable message. */
    fun import(context: Context, uri: Uri): UserLut {
        init(context)
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Couldn't read the file")
        val lut = CubeLut.parse(text)
        val name = displayName(context, uri)?.substringBeforeLast('.') ?: "LUT"
        val id = "lut_" + System.currentTimeMillis()
        val d = dir() ?: error("No storage")
        File(d, "$id.cube").writeText(text)
        File(d, "$id.name").writeText(name)
        synchronized(cache) { cache[id] = lut }
        refresh()
        return UserLut(id, name)
    }

    /**
     * The grade's colour (everything in [g]: tone, curves, colour, HSL, grading, LUT, calibration — not the
     * spatial effects like sharpening or vignette) as a standard 33³ .cube LUT, red changing fastest.
     */
    fun cubeText(g: ProGrade, title: String, n: Int = 33): String {
        val sb = StringBuilder(n * n * n * 26 + 200)
        sb.append("TITLE \"").append(title.replace("\"", "'")).append("\"\n")
        sb.append("# Made with AlcorCut Pro\n")
        sb.append("LUT_3D_SIZE ").append(n).append('\n')
        sb.append("DOMAIN_MIN 0.0 0.0 0.0\nDOMAIN_MAX 1.0 1.0 1.0\n")
        val c = FloatArray(3)
        for (bi in 0 until n) for (gi in 0 until n) for (ri in 0 until n) {
            c[0] = ri / (n - 1f); c[1] = gi / (n - 1f); c[2] = bi / (n - 1f)
            g.apply(c)
            sb.append(String.format(java.util.Locale.US, "%.6f %.6f %.6f\n", c[0].coerceIn(0f, 1f), c[1].coerceIn(0f, 1f), c[2].coerceIn(0f, 1f)))
        }
        return sb.toString()
    }

    /** Exports [g] as a .cube LUT: saved to Documents/AlcorCutPro/LUTs and added to My LUTs. Returns where, or null. */
    fun exportLook(context: Context, g: ProGrade, name: String): String? {
        init(context)
        val clean = name.replace(Regex("[^A-Za-z0-9 _.-]"), "_").ifBlank { "Look" }
        val text = cubeText(g, clean)
        val where = try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val v = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$clean.cube")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Documents/AlcorCutPro/LUTs")
                }
                val u = context.contentResolver.insert(android.provider.MediaStore.Files.getContentUri("external"), v) ?: return null
                context.contentResolver.openOutputStream(u)?.use { it.write(text.toByteArray()) }
            } else {
                @Suppress("DEPRECATION")
                val d = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "AlcorCutPro/LUTs").apply { mkdirs() }
                File(d, "$clean.cube").writeText(text)
            }
            "Documents/AlcorCutPro/LUTs/$clean.cube"
        } catch (_: Throwable) { null }
        // Also into My LUTs, so it can be used on videos, other photos and the Capture monitor straight away.
        try {
            val id = "lut_" + System.currentTimeMillis()
            val d = dir()
            if (d != null) { File(d, "$id.cube").writeText(text); File(d, "$id.name").writeText(clean); refresh() }
        } catch (_: Throwable) {}
        return where
    }

    fun delete(id: String) {
        val d = dir() ?: return
        File(d, "$id.cube").delete(); File(d, "$id.name").delete()
        synchronized(cache) { cache.remove(id) }
        refresh()
    }

    fun name(id: String?): String? = luts.firstOrNull { it.id == id }?.name

    /** The parsed LUT (loaded from disk the first time), or null if it's gone. */
    fun get(id: String?): CubeLut? {
        if (id.isNullOrEmpty()) return null
        synchronized(cache) {
            if (cache.containsKey(id)) return cache[id]
            val f = dir()?.let { File(it, "$id.cube") }
            val lut = try { f?.takeIf { it.exists() }?.readText()?.let { CubeLut.parse(it) } } catch (_: Throwable) { null }
            cache[id] = lut
            return lut
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Throwable) { uri.lastPathSegment }
}
