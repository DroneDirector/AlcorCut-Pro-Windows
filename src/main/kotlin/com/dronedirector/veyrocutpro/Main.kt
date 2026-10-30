package com.dronedirector.veyrocutpro

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackDispatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.dronedirector.veyrocutpro.editor.KeyBus
import com.dronedirector.veyrocutpro.editor.PhotoEditorScreen
import com.dronedirector.veyrocutpro.ui.AccentStore
import com.dronedirector.veyrocutpro.ui.I18n
import com.dronedirector.veyrocutpro.ui.ProCamTheme
import com.dronedirector.veyrocutpro.ui.Text
import java.io.File
import com.dronedirector.veyrocutpro.ui.AppScreen as S

fun main(args: Array<String>) {
    val app = Context.app
    AccentStore.load(app)
    com.dronedirector.veyrocutpro.editor.LutLibrary.init(app)
    com.dronedirector.veyrocutpro.camera.CaptureExtras.load(app)
    com.dronedirector.veyrocutpro.camera.SimpleMode.load(app)
    I18n.load(app)
    com.dronedirector.veyrocutpro.ui.AppLog.init(app)
    com.dronedirector.veyrocutpro.ui.CrashReport.install(app)
    com.dronedirector.veyrocutpro.ui.SafeMode.started(app)
    com.dronedirector.veyrocutpro.ui.OneHanded.load(app)
    com.dronedirector.veyrocutpro.ui.Haptics.load(app)
    com.dronedirector.veyrocutpro.editor.UserPresets.init(app)
    com.dronedirector.veyrocutpro.editor.AppData.init(app)
    com.dronedirector.veyrocutpro.editor.WinData.start(app)
    // Ran fine for a while: no more "closed while starting".
    Thread { Thread.sleep(25_000); com.dronedirector.veyrocutpro.ui.SafeMode.healthy(app) }.apply { isDaemon = true }.start()
    // A photo opened with the app ("Open with" in Windows), or a desktop shortcut (--screen=capture / photo).
    var opened = args.firstOrNull { !it.startsWith("--") }?.let { File(it) }?.takeIf { it.isFile }?.let { Uri.fromFile(it) }
    val asked = args.firstOrNull { it.startsWith("--screen=") }?.substringAfter('=')
    screen.value = when {
        opened != null -> S.PHOTO_EDITOR
        asked == "capture" -> S.CAPTURE
        asked == "photo" -> S.PHOTO_EDITOR
        asked == "video" || asked == "editor" -> S.EDITOR
        com.dronedirector.veyrocutpro.ui.SafeMode.offer -> S.HOME
        else -> com.dronedirector.veyrocutpro.ui.StartScreen.get(app).let { if (it == S.VIDEO_EDITOR) S.EDITOR else it }
    }
    com.dronedirector.veyrocutpro.ui.AudioHardware.start(app)
    application {
        val state = rememberWindowState(placement = WindowPlacement.Maximized, position = WindowPosition(Alignment.Center), size = DpSize(1440.dp, 900.dp))
        var restartKey by remember { mutableStateOf(0) }
        android.content.AppRestart.handler = { restartKey++ }
        Window(
            onCloseRequest = ::exitApplication, title = "AlcorCut Pro", icon = painterResource("icon.png"), state = state,
            onPreviewKeyEvent = { e -> handleKey(e) },
        ) {
            TaskbarProgress.window = window
            androidx.compose.runtime.LaunchedEffect(Unit) {
                com.dronedirector.veyrocutpro.ui.AppLog.i("Window drawn with " + com.dronedirector.veyrocutpro.ui.SystemInfo.drawingApi())
            }
            // Frame-rate check for testing (set ALCORCUT_FPS=1): frames per second and the slowest frame, once a second.
            if (System.getenv("ALCORCUT_FPS") != null) androidx.compose.runtime.LaunchedEffect(Unit) {
                var n = 0; var worst = 0L; var last = 0L; var start = 0L
                while (true) {
                    androidx.compose.runtime.withFrameNanos { t ->
                        if (last > 0) worst = maxOf(worst, t - last)
                        if (start == 0L) start = t
                        last = t; n++
                        if (t - start >= 1_000_000_000L) { println("FPS $n, slowest frame ${worst / 1_000_000} ms"); n = 0; worst = 0; start = t }
                    }
                }
            }
            ProCamTheme {
                BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
                    CompositionLocalProvider(LocalConfiguration provides Configuration(maxWidth.value.toInt(), maxHeight.value.toInt())) {
                        androidx.compose.runtime.key(restartKey) {
                            com.dronedirector.veyrocutpro.ui.AppExitLayer {
                                Screens(opened.also { opened = null })
                                com.dronedirector.veyrocutpro.ui.CaptureForEditorHost(controller) { content -> content() }
                            }
                        }
                    }
                    Toasts(Modifier.align(Alignment.BottomCenter))
                    com.dronedirector.veyrocutpro.editor.WinDataBadge(Modifier.align(Alignment.BottomStart))
                    com.dronedirector.veyrocutpro.ui.CrashReportDialog()
                    com.dronedirector.veyrocutpro.ui.SafeModeDialog {
                        app.getSharedPreferences("procam", Context.MODE_PRIVATE).edit().remove("last_mode").remove("last_lens_pc").apply()
                        controller.setMode(com.dronedirector.veyrocutpro.camera.CaptureMode.PHOTO)
                        screen.value = com.dronedirector.veyrocutpro.ui.AppScreen.HOME
                    }
                    com.dronedirector.veyrocutpro.ui.AudioHardwareNotice()
                    if (com.dronedirector.veyrocutpro.camera.CaptureKeys.show) com.dronedirector.veyrocutpro.editor.ShortcutsDialog(com.dronedirector.veyrocutpro.editor.CaptureShortcuts) { com.dronedirector.veyrocutpro.camera.CaptureKeys.show = false }
                    if (!com.dronedirector.veyrocutpro.ui.SafeMode.offer) com.dronedirector.veyrocutpro.ui.WhatsNewDialog()
                    com.dronedirector.veyrocutpro.ui.UndoBarHost()
                    com.dronedirector.veyrocutpro.ui.HelpTipHost()
                }
            }
        }
    }
}

/** Which screen is open: start screen, Capture, the editor hub or an editor. */
private val screen = mutableStateOf(com.dronedirector.veyrocutpro.ui.AppScreen.HOME)

/** The camera (one for the whole app, like on the phone). */
private val controller by lazy { com.dronedirector.veyrocutpro.camera.ProCameraController(Context.app) }

@androidx.compose.runtime.Composable
private fun Screens(opened: Uri?) {
    androidx.compose.animation.Crossfade(screen.value, label = "screen") { s ->
        when (s) {
            S.HOME -> com.dronedirector.veyrocutpro.ui.HomeScreen(onCapture = { screen.value = S.CAPTURE }, onEditor = { screen.value = S.EDITOR })
            S.CAPTURE -> {
                androidx.activity.compose.BackHandler { screen.value = S.HOME }
                com.dronedirector.veyrocutpro.ui.ForceDark { com.dronedirector.veyrocutpro.ui.CameraScreen(controller) }
            }
            S.EDITOR -> com.dronedirector.veyrocutpro.ui.EditorHub(
                onPhoto = { screen.value = S.PHOTO_EDITOR },
                onVideo = { Toast.makeText(null, I18n.tr("The video editor comes to Windows in a later update"), Toast.LENGTH_LONG).show() },
                onBack = { screen.value = S.HOME },
            )
            S.PHOTO_EDITOR -> PhotoEditorScreen(
                onOpenVideo = { Toast.makeText(null, I18n.tr("The video editor comes to Windows in a later update"), Toast.LENGTH_LONG).show() },
                onExit = { screen.value = S.EDITOR },
                initial = opened,
            )
            S.VIDEO_EDITOR -> { androidx.compose.runtime.LaunchedEffect(Unit) { screen.value = S.EDITOR } }
        }
    }
}

/** Short notices at the bottom of the window. */
@androidx.compose.runtime.Composable
private fun Toasts(modifier: Modifier) {
    Column(modifier.padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (t in Toast.shown.toList()) {
            Text(t.text, color = Color.White, fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Color(0xE6202226)).padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
}

/** A desktop key event as the phone's (same codes and modifier flags). */
fun phoneKeyEvent(e: KeyEvent): android.view.KeyEvent {
    val meta = (if (e.isCtrlPressed) android.view.KeyEvent.META_CTRL_ON else 0) or (if (e.isShiftPressed) android.view.KeyEvent.META_SHIFT_ON else 0) or
        (if (e.isAltPressed) android.view.KeyEvent.META_ALT_ON else 0) or (if (e.isMetaPressed) android.view.KeyEvent.META_META_ON else 0)
    return android.view.KeyEvent(if (e.type == KeyEventType.KeyDown) android.view.KeyEvent.ACTION_DOWN else android.view.KeyEvent.ACTION_UP, keyCode(e.key), meta)
}

/** Esc goes back; other keys go to the open editor's shortcuts (same codes as on the phone). */
private fun handleKey(e: KeyEvent): Boolean {
    if (e.type == KeyEventType.KeyDown && e.key == Key.Escape && !e.isCtrlPressed) return BackDispatcher.back()
    val code = keyCode(e.key)
    if (code == android.view.KeyEvent.KEYCODE_UNKNOWN) return false
    // Capture's keyboard shortcuts (changeable; Ctrl + / lists them).
    if (screen.value == com.dronedirector.veyrocutpro.ui.AppScreen.CAPTURE && com.dronedirector.veyrocutpro.ui.CaptureForEditor.request == null)
        return com.dronedirector.veyrocutpro.camera.CaptureKeys.run(Context.app, controller, phoneKeyEvent(e))
    val meta = (if (e.isCtrlPressed) android.view.KeyEvent.META_CTRL_ON else 0) or (if (e.isShiftPressed) android.view.KeyEvent.META_SHIFT_ON else 0) or
        (if (e.isAltPressed) android.view.KeyEvent.META_ALT_ON else 0) or (if (e.isMetaPressed) android.view.KeyEvent.META_META_ON else 0)
    val action = if (e.type == KeyEventType.KeyDown) android.view.KeyEvent.ACTION_DOWN else android.view.KeyEvent.ACTION_UP
    return KeyBus.dispatch(android.view.KeyEvent(action, code, meta))
}

private fun keyCode(k: Key): Int {
    val K = android.view.KeyEvent
    val letters = listOf(Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M, Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z)
    letters.indexOf(k).takeIf { it >= 0 }?.let { return K.KEYCODE_A + it }
    val digits = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    digits.indexOf(k).takeIf { it >= 0 }?.let { return K.KEYCODE_0 + it }
    val pad = listOf(Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9)
    pad.indexOf(k).takeIf { it >= 0 }?.let { return K.KEYCODE_0 + it }
    return when (k) {
        Key.DirectionLeft -> K.KEYCODE_DPAD_LEFT; Key.DirectionRight -> K.KEYCODE_DPAD_RIGHT
        Key.DirectionUp -> K.KEYCODE_DPAD_UP; Key.DirectionDown -> K.KEYCODE_DPAD_DOWN
        Key.Spacebar -> K.KEYCODE_SPACE; Key.Enter -> K.KEYCODE_ENTER; Key.Tab -> K.KEYCODE_TAB
        Key.Backspace -> K.KEYCODE_DEL; Key.Delete -> K.KEYCODE_FORWARD_DEL
        Key.Minus -> K.KEYCODE_MINUS; Key.Equals -> K.KEYCODE_EQUALS; Key.Plus -> K.KEYCODE_PLUS
        Key.LeftBracket -> K.KEYCODE_LEFT_BRACKET; Key.RightBracket -> K.KEYCODE_RIGHT_BRACKET
        Key.Comma -> K.KEYCODE_COMMA; Key.Period -> K.KEYCODE_PERIOD; Key.Slash -> K.KEYCODE_SLASH; Key.Backslash -> K.KEYCODE_BACKSLASH
        Key.PageUp -> K.KEYCODE_PAGE_UP; Key.PageDown -> K.KEYCODE_PAGE_DOWN; Key.MoveHome -> K.KEYCODE_MOVE_HOME; Key.MoveEnd -> K.KEYCODE_MOVE_END
        Key.F1 -> K.KEYCODE_F1; Key.F2 -> K.KEYCODE_F2; Key.F3 -> K.KEYCODE_F3; Key.F4 -> K.KEYCODE_F4; Key.F5 -> K.KEYCODE_F5; Key.F6 -> K.KEYCODE_F6
        Key.F7 -> K.KEYCODE_F7; Key.F8 -> K.KEYCODE_F8; Key.F9 -> K.KEYCODE_F9; Key.F10 -> K.KEYCODE_F10; Key.F11 -> K.KEYCODE_F11; Key.F12 -> K.KEYCODE_F12
        else -> K.KEYCODE_UNKNOWN
    }
}
