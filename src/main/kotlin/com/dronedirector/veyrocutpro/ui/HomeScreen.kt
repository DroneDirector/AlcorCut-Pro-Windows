package com.dronedirector.veyrocutpro.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Settings
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.R
import kotlin.math.cos
import kotlin.math.sin

enum class AppScreen { HOME, CAPTURE, EDITOR, PHOTO_EDITOR, VIDEO_EDITOR }

/** Which screen the app opens on (the start screen unless the user picked one in a settings menu). */
object StartScreen {
    private const val KEY = "start_screen"
    fun get(context: android.content.Context): AppScreen {
        val v = context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).getString(KEY, null)
        return AppScreen.entries.firstOrNull { it.name == v && it in listOf(AppScreen.CAPTURE, AppScreen.PHOTO_EDITOR, AppScreen.VIDEO_EDITOR) } ?: AppScreen.HOME
    }
    fun set(context: android.content.Context, screen: AppScreen) {
        context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).edit().putString(KEY, screen.name).apply()
    }
}

/** Start screen shown on every launch: Capture or Editor. */
@Composable
fun HomeScreen(onCapture: () -> Unit, onEditor: () -> Unit) {
    HubLayout(
        title = "AlcorCut Pro",
        subtitle = "CAPTURE · EDIT · CREATE",
        onBack = null,
        showVersion = true,
        tiles = listOf(
            Tile("CAPTURE", "Photo · Video · Pro · Smart modes · Live", Icons.Filled.CameraAlt, onCapture),
            Tile("EDITOR", "Photo editor · Video editor", Icons.Filled.Tune, onEditor),
        ),
    )
}

/** Editor hub: photo editor or video editor. */
@Composable
fun EditorHub(onPhoto: () -> Unit, onVideo: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    HubLayout(
        title = "Editor",
        subtitle = "GRADE · CUT · DELIVER",
        onBack = onBack,
        tiles = listOf(
            Tile("PHOTO EDITOR", "Light, curves, HSL, colour grading, detail, crop, export", Icons.Filled.Photo, onPhoto),
            Tile("VIDEO EDITOR", "Timeline, log footage, colour pages, audio, 4K delivery", Icons.Filled.Movie, onVideo),
        ),
    )
}

private class Tile(val title: String, val detail: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun HubLayout(title: String, subtitle: String, onBack: (() -> Unit)?, tiles: List<Tile>, showVersion: Boolean = false) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (_: Throwable) { "" }
    }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(700)) }
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "angle")
    val pulse by spin.animateFloat(0.55f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "pulse")
    val accent = Pro.Amber
    // Exit: the whole screen fades and shrinks away, then the app closes.
    var exiting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val leave by androidx.compose.animation.core.animateFloatAsState(
        if (exiting) 1f else 0f, tween(520),
        finishedListener = { if (it >= 1f) (context as? android.app.Activity)?.finishAndRemoveTask() },
        label = "leave",
    )

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - leave; scaleX = 1f - leave * 0.08f; scaleY = 1f - leave * 0.08f }
            .background(Brush.radialGradient(if (AppTheme.light) listOf(Color(0xFFFFFFFF), Color(0xFFEFF1F4), Color(0xFFE2E5E9)) else listOf(Color(0xFF1B1710), Color(0xFF0B0B0D), Color(0xFF050506)), radius = 1600f))
    ) {
        // Slowly turning aperture rings behind everything.
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.3f)
            val r = size.minDimension * 0.42f
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.18f * pulse), Color.Transparent), c, r * 1.5f), r * 1.5f, c)
            rotate(angle, c) {
                for (i in 0 until 6) {
                    val a = Math.toRadians(i * 60.0)
                    val p1 = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
                    val a2 = Math.toRadians(i * 60.0 + 100.0)
                    val p2 = Offset(c.x + (r * 0.55f * cos(a2)).toFloat(), c.y + (r * 0.55f * sin(a2)).toFloat())
                    drawLine(accent.copy(alpha = 0.10f), p1, p2, strokeWidth = 2f)
                }
                drawCircle(accent.copy(alpha = 0.12f), r, c, style = Stroke(2f))
            }
            rotate(-angle * 1.6f, c) {
                drawCircle(Color.White.copy(alpha = 0.05f), r * 0.75f, c, style = Stroke(1.5f))
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(20.dp)) {
            val landscape = maxWidth > maxHeight
            if (onBack != null) {
                Box(Modifier.size(46.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
            val header = @Composable {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Logo with the glowing, sparkling ring in the app colour.
                    GlowLogo(if (landscape) 96.dp else 128.dp)
                    Text(title, color = Color.White, fontSize = if (landscape) 30.sp else 38.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 2.dp))
                    Text(subtitle, style = Pro.Label.copy(color = accent, fontSize = 12.sp, letterSpacing = 4.sp))
                }
            }
            val tileViews = @Composable { mod: Modifier ->
                tiles.forEachIndexed { i, t ->
                    val k = ((appear.value * 1.6f) - i * 0.3f).coerceIn(0f, 1f)
                    HubTile(t, mod.graphicsLayer { alpha = k; translationY = (1f - k) * 60f })
                }
            }
            if (landscape) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Box(Modifier.weight(0.8f), contentAlignment = Alignment.Center) { header() }
                    Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(16.dp)) { tileViews(Modifier.fillMaxWidth()) }
                }
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                ) {
                    header()
                    Spacer(Modifier.height(18.dp))
                    tileViews(Modifier.fillMaxWidth().widthIn(max = 520.dp))
                }
            }
            if (showVersion) ExitButton(angle, pulse, Modifier.align(Alignment.BottomEnd)) { exiting = true }
            // Start screen settings (gear): home-screen shortcuts.
            if (showVersion) {
                var settings by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                Box(Modifier.align(Alignment.TopEnd).size(46.dp).clip(CircleShape).clickable { settings = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                }
                if (settings) AppSettingsDialog { settings = false }
            }
            if (showVersion && version.isNotEmpty()) {
                Text(
                    "v$version",
                    style = Pro.Label.copy(color = Pro.Dim, fontSize = 11.sp, letterSpacing = 1.sp),
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
    }
}

@Composable
private fun HubTile(t: Tile, modifier: Modifier) {
    val accent = Pro.Amber
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(if (AppTheme.light) listOf(Color(0xFFFFFFFF), Color(0xFFF0F2F5)) else listOf(Color(0xFF1C1E23), Color(0xFF111215))))
            .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.8f), accent.copy(alpha = 0.1f))), RoundedCornerShape(20.dp))
            .clickable(onClick = t.onClick)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(t.icon, contentDescription = null, tint = accent, modifier = Modifier.size(32.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(t.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(t.detail, color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = accent)
    }
}

/** Power-style exit button: turning gradient ring, breathing glow, press flash. */
@Composable
private fun ExitButton(angle: Float, pulse: Float, modifier: Modifier, onExit: () -> Unit) {
    val accent = Pro.Amber
    var pressed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val flash by androidx.compose.animation.core.animateFloatAsState(if (pressed) 1f else 0f, tween(260), label = "flash")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).graphicsLayer { val k = 1f + flash * 0.12f; scaleX = k; scaleY = k }
                .clip(CircleShape)
                .clickable { if (!pressed) { pressed = true; onExit() } },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension / 2f
                // breathing glow
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f * pulse + flash * 0.4f), Color.Transparent), c, r), r, c)
                // dark core
                drawCircle(Brush.radialGradient(if (AppTheme.light) listOf(Color(0xFFFFFFFF), Color(0xFFE4E7EB)) else listOf(Color(0xFF22252B), Color(0xFF0D0E11)), c, r * 0.8f), r * 0.78f, c)
                // turning gradient ring
                rotate(angle * 6f, c) {
                    drawCircle(
                        Brush.sweepGradient(listOf(accent, accent.copy(alpha = 0.05f), Color.White.copy(alpha = 0.9f), accent.copy(alpha = 0.05f), accent), c),
                        r * 0.8f, c, style = Stroke(3.dp.toPx()),
                    )
                }
                drawCircle(Color.White.copy(alpha = flash * 0.5f), r * 0.78f, c)
            }
            Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Exit", tint = accent, modifier = Modifier.size(26.dp))
        }
        Text("EXIT", style = Pro.Label.copy(color = accent.copy(alpha = 0.85f), fontSize = 10.sp, letterSpacing = 3.sp), modifier = Modifier.padding(top = 2.dp))
    }
}
