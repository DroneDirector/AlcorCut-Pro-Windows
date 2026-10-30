package com.dronedirector.veyrocutpro.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Accent colours the user can pick in Settings; all bright enough for black text on top. */
enum class Accent(val label: String, val color: Color) {
    GOLD("Gold", Color(0xFFFFB300)),
    ORANGE("Orange", Color(0xFFFF8A3D)),
    CORAL("Coral", Color(0xFFFF6B6B)),
    PINK("Pink", Color(0xFFFF7EB6)),
    VIOLET("Violet", Color(0xFFB79CFF)),
    BLUE("Blue", Color(0xFF5AB4FF)),
    CYAN("Cyan", Color(0xFF3FE0E0)),
    MINT("Mint", Color(0xFF4CE0A0)),
    LIME("Lime", Color(0xFFB8E04A)),
    WHITE("White", Color(0xFFF2F2F2)),
}

object AccentStore {
    private const val PREFS = "procam"
    private const val KEY = "accent"

    fun load(context: Context) {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        Pro.accent = Accent.entries.firstOrNull { it.name == name } ?: Accent.GOLD
    }

    fun save(context: Context, accent: Accent) {
        Pro.accent = accent
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, accent.name).apply()
    }
}

/**
 * Dark or light look (gear → Preferences → Appearance). Light is used everywhere except Capture (which sits on
 * the live camera picture), with dark text throughout; the picture itself, the timeline and the media strip
 * stay dark inside the editors, as in pro editing apps.
 */
object AppTheme {
    /** 0 dark, 1 light, 2 like the phone. */
    var pref by mutableStateOf(0)
    var systemDark by mutableStateOf(true)
    /** The screen on show allows the light look. */
    var screenAllowsLight by mutableStateOf(false)
    val light: Boolean get() = screenAllowsLight && (pref == 1 || (pref == 2 && !systemDark))

    fun load(c: Context) { pref = c.getSharedPreferences("procam", Context.MODE_PRIVATE).getInt("app_theme", 0) }
    fun set(c: Context, t: Int) { pref = t; c.getSharedPreferences("procam", Context.MODE_PRIVATE).edit().putInt("app_theme", t).apply() }

    /** Text and icon colour on the light look for a colour meant for the dark look (white-ish → near-black, same transparency). */
    fun onLight(c: Color): Color = if (c.red > 0.93f && c.green > 0.93f && c.blue > 0.93f) Color(0xFF15181C).copy(alpha = c.alpha) else c

    /**
     * Text colour on the light look: every light or coloured text (white, the accent yellow, green, blue…) becomes
     * near-black so it reads on the light panels; warnings in red stay red (a darker red). Same transparency.
     */
    fun onLightText(c: Color): Color {
        val l = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
        return when {
            c.red > 0.8f && c.green < 0.45f && c.blue < 0.45f -> Color(0xFFC62828).copy(alpha = c.alpha)
            l > 0.3f -> Color(0xFF15181C).copy(alpha = c.alpha)
            else -> c
        }
    }

    /** A background colour: [dark] on the dark look, [light] on the light look. */
    fun pick(dark: Color, light: Color): Color = if (this.light) light else dark
}

/** Areas that stay dark in the light look (picture, timeline, media strip): their white text and icons stay white. */
val LocalForceDark = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun ForceDark(content: @Composable () -> Unit) = androidx.compose.runtime.CompositionLocalProvider(LocalForceDark provides true, content = content)

/** The colour to draw a text / icon colour with here. */
@Composable
fun themed(c: Color): Color = if (AppTheme.light && !LocalForceDark.current && c != Color.Unspecified) AppTheme.onLight(c) else c

/** True when the light look applies here (not inside an area that stays dark, such as Capture's controls). */
@Composable
fun lightHere(): Boolean = AppTheme.light && !LocalForceDark.current

/** Solid panel colour here (dialogs, menus). */
@Composable
fun panelHere(): Color = if (lightHere()) Pro.PanelSolid else Pro.HudPanelSolid

/** Main text / icon colour here. */
@Composable
fun fgHere(): Color = if (lightHere()) Pro.Fg else Color.White

/** [dark] or [light] for the look in use here. */
@Composable
fun pickHere(dark: Color, light: Color): Color = if (lightHere()) light else dark

/** The colour to draw text with here (light look: dark text; areas that stay dark keep their colours). */
@Composable
fun themedText(c: Color): Color = if (AppTheme.light && !LocalForceDark.current && c != Color.Unspecified) AppTheme.onLightText(c) else c

object Pro {
    /** The highlight colour used across the app (yellow by default). Changing it recolours the UI live. */
    var accent by mutableStateOf(Accent.GOLD)
    val Amber: Color get() = accent.color
    val Red = Color(0xFFFF3B30)
    val Green = Color(0xFF34C759)
    val Panel: Color get() = if (AppTheme.light) Color(0xE6F4F5F7) else Color(0xCC0E1013)
    val PanelSolid: Color get() = if (AppTheme.light) Color(0xFFF4F5F7) else Color(0xFF15181C)
    val Line: Color get() = if (AppTheme.light) Color(0x33000000) else Color(0x33FFFFFF)
    /** Panels and lines on the camera picture (Capture): always dark, whatever the look. */
    val HudPanel = Color(0xCC0E1013)
    val HudPanelSolid = Color(0xFF15181C)
    val HudLine = Color(0x33FFFFFF)
    /** Second-level text (white at 60 % — near-black at 60 % on the light look, via [themed]). */
    val Dim = Color(0x99FFFFFF)
    /** Main text / icon colour of the look in use. */
    val Fg: Color get() = if (AppTheme.light) Color(0xFF15181C) else Color.White

    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color.White)
    val Label = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 1.2.sp, color = Dim)
    val Value = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
}

@Composable
fun ProCamTheme(content: @Composable () -> Unit) {
    AppTheme.systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (AppTheme.light) androidx.compose.material3.lightColorScheme(
            primary = Pro.Amber, onPrimary = Color.Black, secondary = Pro.Amber,
            background = Color(0xFFE9EBEE), surface = Pro.PanelSolid, onSurface = Pro.Fg,
            surfaceVariant = Color(0xFFE2E4E8), onSurfaceVariant = Color(0xFF3A3F45),
        ) else darkColorScheme(
            primary = Pro.Amber,
            onPrimary = Color.Black,
            secondary = Pro.Amber,
            background = Color.Black,
            surface = Pro.PanelSolid,
            onSurface = Color.White,
        ),
        content = content,
    )
}
