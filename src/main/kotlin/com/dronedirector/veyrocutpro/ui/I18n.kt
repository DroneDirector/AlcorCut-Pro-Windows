package com.dronedirector.veyrocutpro.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * App languages. Every on-screen text goes through [tr]: exact matches come from
 * assets/i18n/<lang>.json; texts with numbers or names in them match a pattern there
 * ("{0} photos saved"). Anything not translated stays in English.
 */
object I18n {
    /** "" = follow the phone, else "en", "da" or "fo". */
    var choice by mutableStateOf("")
        private set
    /** Language in use now. */
    var lang by mutableStateOf("en")
        private set

    val options = listOf("" to "Phone language", "en" to "English", "da" to "Dansk", "fo" to "Føroyskt")

    private var exact: Map<String, String> = emptyMap()
    private var patterns: List<Pair<Regex, String>> = emptyList()
    private val cache = ConcurrentHashMap<String, String>()

    fun load(context: Context) {
        choice = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getString("ui_lang", "") ?: ""
        apply(context)
    }

    fun set(context: Context, value: String) {
        choice = value
        context.getSharedPreferences("procam", Context.MODE_PRIVATE).edit().putString("ui_lang", value).apply()
        apply(context)
    }

    private fun apply(context: Context) {
        val sys = java.util.Locale.getDefault().language
        val l = choice.ifEmpty { if (sys == "da" || sys == "fo") sys else "en" }
        cache.clear()
        if (l == "en") { exact = emptyMap(); patterns = emptyList(); lang = l; return }
        val map = HashMap<String, String>()
        val pats = ArrayList<Triple<Regex, String, Int>>()
        try {
            val o = JSONObject(context.assets.open("i18n/$l.json").bufferedReader().use { it.readText() })
            for (k in o.keys()) {
                val v = o.optString(k)
                if (v.isEmpty()) continue
                if (k.contains('{') && Regex("\\{\\d\\}").containsMatchIn(k)) {
                    val literal = k.replace(Regex("\\{\\d\\}"), "")
                    if (literal.count { it.isLetter() } < 3) continue
                    val re = buildString {
                        append('^')
                        var last = 0
                        for (m in Regex("\\{(\\d)\\}").findAll(k)) {
                            append(Regex.escape(k.substring(last, m.range.first)))
                            append("(?<g").append(m.groupValues[1]).append(">.*?)")
                            last = m.range.last + 1
                        }
                        append(Regex.escape(k.substring(last)))
                        append('$')
                    }
                    try { pats += Triple(Regex(re, RegexOption.DOT_MATCHES_ALL), v, literal.length) } catch (_: Throwable) {}
                } else map[k] = v
            }
        } catch (_: Throwable) {}
        exact = map
        // Longest fixed text first, so the most specific pattern wins.
        patterns = pats.sortedByDescending { it.third }.map { it.first to it.second }
        lang = l
    }

    /** The text in the chosen language (or unchanged). */
    fun tr(s: String): String {
        if (lang == "en" || s.isEmpty()) return s
        exact[s]?.let { return it }
        cache[s]?.let { return it }
        var out = s
        // Keep leading / trailing spaces and symbols like "▶ " outside the lookup.
        val core = s.trim()
        if (core != s) exact[core]?.let { out = s.replace(core, it) }
        if (out === s) for ((re, tpl) in patterns) {
            val m = re.matchEntire(s) ?: continue
            out = Regex("\\{(\\d)\\}").replace(tpl) { g ->
                val name = "g" + g.groupValues[1]
                (try { m.groups[name]?.value } catch (_: Throwable) { null })?.let { tr(it) } ?: g.value
            }
            break
        }
        if (cache.size > 5000) cache.clear()
        cache[s] = out
        return out
    }
}

@androidx.compose.runtime.Composable
fun LanguagePicker() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(vertical = 4.dp)) {
        Text("LANGUAGE · SPROG · MÁL", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp))
        androidx.compose.foundation.layout.Row(
            androidx.compose.ui.Modifier.padding(vertical = 6.dp).hintedScroll(horizontal = true),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            I18n.options.forEach { (code, label) ->
                val sel = I18n.choice == code
                androidx.compose.material3.Text(
                    label, color = if (sel) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White, fontSize = 13.sp,
                    modifier = androidx.compose.ui.Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .background(if (sel) Pro.Amber else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f))
                        .clickable { I18n.set(ctx, code) }.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** Material Text, translated (drop-in: same parameters). */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
) {
    @Suppress("UNUSED_VARIABLE") val l = I18n.lang   // recompose when the language changes
    // Inside a fold-out section, control titles (white, 13–16 sp) make up the section's "what's inside" line.
    if (color == Color.White && fontSize.isSp && fontSize.value in 12.5f..16.5f) LocalFold.current?.collect(I18n.tr(text))
    // Light look: white text becomes near-black (except in areas that stay dark).
    val c = themedText(if (color != Color.Unspecified) color else style.color)
    androidx.compose.material3.Text(
        text = I18n.tr(text), modifier = modifier, color = c, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, style = style,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = text, modifier = modifier, color = themedText(if (color != Color.Unspecified) color else style.color), fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines, style = style,
    )
}
