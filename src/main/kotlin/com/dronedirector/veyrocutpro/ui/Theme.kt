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

object Pro {
    /** The highlight colour used across the app (yellow by default). Changing it recolours the UI live. */
    var accent by mutableStateOf(Accent.GOLD)
    val Amber: Color get() = accent.color
    val Red = Color(0xFFFF3B30)
    val Green = Color(0xFF34C759)
    val Panel = Color(0xCC0E1013)
    val PanelSolid = Color(0xFF15181C)
    val Line = Color(0x33FFFFFF)
    val Dim = Color(0x99FFFFFF)

    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color.White)
    val Label = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 1.2.sp, color = Dim)
    val Value = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
}

@Composable
fun ProCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
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
