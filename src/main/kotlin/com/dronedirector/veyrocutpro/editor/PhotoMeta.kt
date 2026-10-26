package com.dronedirector.veyrocutpro.editor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Locale

/**
 * Photo metadata for clients, agencies and photo software:
 *  - IPTC title, caption and keywords written into every exported JPEG / WebP (as XMP), and
 *  - an optional Camera Raw .xmp sidecar with the edit, for the original photo.
 */
object PhotoMeta {
    var title by mutableStateOf("")
    var caption by mutableStateOf("")
    /** Comma-separated. */
    var keywords by mutableStateOf("")
    var sidecar by mutableStateOf(false)

    private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)

    fun load(c: Context) {
        val p = prefs(c)
        title = p.getString("pm_title", "") ?: ""
        caption = p.getString("pm_caption", "") ?: ""
        keywords = p.getString("pm_keywords", "") ?: ""
        sidecar = p.getBoolean("pm_sidecar", false)
    }

    fun save(c: Context) {
        prefs(c).edit().putString("pm_title", title).putString("pm_caption", caption)
            .putString("pm_keywords", keywords).putBoolean("pm_sidecar", sidecar).apply()
    }

    private val keywordList: List<String> get() = keywords.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }

    val hasIptc: Boolean get() = title.isNotBlank() || caption.isNotBlank() || keywordList.isNotEmpty()

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** dc: / photoshop: elements for the IPTC fields plus your name and copyright. */
    private fun iptcXml(): String {
        val sb = StringBuilder()
        val creator = com.dronedirector.veyrocutpro.camera.CaptureExtras.creator
        val rights = com.dronedirector.veyrocutpro.camera.CaptureExtras.copyright
        if (title.isNotBlank()) sb.append("   <dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">${esc(title)}</rdf:li></rdf:Alt></dc:title>\n")
        if (caption.isNotBlank()) sb.append("   <dc:description><rdf:Alt><rdf:li xml:lang=\"x-default\">${esc(caption)}</rdf:li></rdf:Alt></dc:description>\n")
        if (keywordList.isNotEmpty()) sb.append("   <dc:subject><rdf:Bag>").append(keywordList.joinToString("") { "<rdf:li>${esc(it)}</rdf:li>" }).append("</rdf:Bag></dc:subject>\n")
        if (creator.isNotBlank()) sb.append("   <dc:creator><rdf:Seq><rdf:li>${esc(creator)}</rdf:li></rdf:Seq></dc:creator>\n")
        if (rights.isNotBlank()) sb.append("   <dc:rights><rdf:Alt><rdf:li xml:lang=\"x-default\">${esc(rights)}</rdf:li></rdf:Alt></dc:rights>\n")
        if (title.isNotBlank()) sb.append("   <photoshop:Headline>${esc(title)}</photoshop:Headline>\n")
        return sb.toString()
    }

    private fun packet(attrs: String, body: String) = """<?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="AlcorCut Pro">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:dc="http://purl.org/dc/elements/1.1/"
    xmlns:photoshop="http://ns.adobe.com/photoshop/1.0/"
    xmlns:xmp="http://ns.adobe.com/xap/1.0/"
    xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"
    xmp:CreatorTool="AlcorCut Pro"$attrs>
$body  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""

    /** XMP with the IPTC fields, to embed in an exported picture; null when there's nothing to write. */
    fun embedXmp(): String? = if (!hasIptc) null else packet("", iptcXml())

    private fun v(x: Float, scale: Float = 100f, lo: Float = -100f, hi: Float = 100f) =
        Math.round((x * scale).coerceIn(lo, hi)).toString()

    /**
     * A Camera Raw (.xmp) sidecar with the closest matching settings. [raw] = the
     * original is a DNG (white balance then stays "As Shot").
     */
    fun sidecarXmp(g: ProGrade, d: Detail, geo: Geometry, raw: Boolean): String {
        val names = listOf("Red", "Orange", "Yellow", "Green", "Aqua", "Blue", "Purple", "Magenta")
        val a = StringBuilder()
        fun put(k: String, value: String) { a.append("\n    crs:").append(k).append("=\"").append(value).append('"') }
        put("Version", "15.0"); put("ProcessVersion", "11.0")
        put("Exposure2012", String.format(Locale.US, "%+.2f", g.exposure.coerceIn(-5f, 5f)))
        put("Contrast2012", v(g.contrast)); put("Highlights2012", v(g.highlights)); put("Shadows2012", v(g.shadows))
        put("Whites2012", v(g.whites)); put("Blacks2012", v(g.blacks))
        put("Vibrance", v(g.vibrance)); put("Saturation", v(g.saturation)); put("Dehaze", v(g.dehaze))
        put("ParametricHighlights", v(g.curveHighlights)); put("ParametricLights", v(g.curveLights))
        put("ParametricDarks", v(g.curveDarks)); put("ParametricShadows", v(g.curveShadows))
        if (raw) put("WhiteBalance", "As Shot") else {
            put("IncrementalTemperature", v(g.temperature)); put("IncrementalTint", v(g.tint))
        }
        put("Clarity2012", v(d.clarity)); put("Texture", v(d.texture))
        put("Sharpness", v(d.sharpen, 150f, 0f, 150f)); put("LuminanceSmoothing", v(d.denoise, 100f, 0f, 100f))
        put("PostCropVignetteAmount", v(d.vignette)); put("GrainAmount", v(d.grain, 100f, 0f, 100f))
        g.hsl.take(8).forEachIndexed { i, b ->
            put("HueAdjustment${names[i]}", v(b.hue)); put("SaturationAdjustment${names[i]}", v(b.sat)); put("LuminanceAdjustment${names[i]}", v(b.lum))
        }
        if (geo.straighten != 0f) put("CropAngle", String.format(Locale.US, "%.2f", geo.straighten))
        if (geo.vertical != 0f) put("PerspectiveVertical", v(geo.vertical))
        if (geo.horizontal != 0f) put("PerspectiveHorizontal", v(geo.horizontal))
        put("HasSettings", "True")
        return packet(a.toString(), iptcXml())
    }

    /** Writes [xmp] as `<photo name>.xmp` in Documents/AlcorCutPro/XMP. Returns where, or null. */
    fun writeSidecar(context: Context, source: Uri, xmp: String): String? {
        val srcName = try {
            context.contentResolver.query(source, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Throwable) { null } ?: source.lastPathSegment ?: "photo"
        val name = srcName.substringBeforeLast('.') + ".xmp"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/AlcorCutPro/XMP")
                }
                val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { it.write(xmp.toByteArray()) }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "AlcorCutPro/XMP").apply { mkdirs() }
                File(dir, name).writeText(xmp)
            }
            "Documents/AlcorCutPro/XMP/$name"
        } catch (_: Throwable) { null }
    }
}
