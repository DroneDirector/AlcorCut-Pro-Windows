package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject

/** Optional memory of the last watermark, per editor, applied to every new photo / video. */
object WatermarkMemory {
    private val on = mutableStateMapOf<Boolean, Boolean>()
    private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)
    private fun k(video: Boolean, what: String) = "wm_${if (video) "video" else "photo"}_$what"

    /** Observable in Compose. */
    fun isOn(context: Context, video: Boolean): Boolean =
        on.getOrPut(video) { prefs(context).getBoolean(k(video, "on"), false) }

    fun setOn(context: Context, video: Boolean, value: Boolean) {
        on[video] = value
        prefs(context).edit().putBoolean(k(video, "on"), value).apply()
    }

    fun save(context: Context, video: Boolean, l: Layer?) {
        if (l == null) { prefs(context).edit().remove(k(video, "layer")).apply(); return }
        l.imageUri?.let { try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {} }
        val j = JSONObject()
            .put("kind", l.kind.name).put("text", l.text).put("image", l.imageUri?.toString() ?: "")
            .put("color", l.color).put("size", l.size.toDouble()).put("x", l.x.toDouble()).put("y", l.y.toDouble())
            .put("opacity", l.opacity.toDouble()).put("font", l.font.name).put("shadow", l.shadow).put("box", l.box)
        prefs(context).edit().putString(k(video, "layer"), j.toString()).apply()
    }

    /** The remembered watermark, or null when the option is off or nothing was saved yet. */
    fun load(context: Context, video: Boolean): Layer? {
        if (!isOn(context, video)) return null
        val s = prefs(context).getString(k(video, "layer"), null) ?: return null
        return try {
            val j = JSONObject(s)
            Layer(
                kind = LayerKind.entries.firstOrNull { it.name == j.optString("kind") } ?: LayerKind.WATERMARK,
                text = j.optString("text", "© AlcorCut Pro"),
                imageUri = j.optString("image").takeIf { it.isNotEmpty() }?.let(Uri::parse),
                color = j.optInt("color", 0xFFFFFFFF.toInt()),
                size = j.optDouble("size", 0.035).toFloat(),
                x = j.optDouble("x", 0.84).toFloat(), y = j.optDouble("y", 0.93).toFloat(),
                opacity = j.optDouble("opacity", 0.7).toFloat(),
                font = LayerFont.entries.firstOrNull { it.name == j.optString("font") } ?: LayerFont.LIGHT,
                shadow = j.optBoolean("shadow", true), box = j.optBoolean("box", false),
                wholeVideo = true,
            )
        } catch (_: Throwable) { null }
    }
}
