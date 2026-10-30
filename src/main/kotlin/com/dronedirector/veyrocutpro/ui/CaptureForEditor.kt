package com.dronedirector.veyrocutpro.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dronedirector.veyrocutpro.camera.CaptureMode
import com.dronedirector.veyrocutpro.camera.ProCameraController

/**
 * Capture opened from inside an editor: the full Capture screen (every pro control, every mode) on
 * top of the editor, which stays open underneath. Each photo or video taken is handed straight to the
 * editor; Back (or the bar at the top) returns to it.
 */
object CaptureForEditor {
    class Request(val editor: String, val video: Boolean, val onMedia: (Uri, Boolean) -> Unit)

    var request by mutableStateOf<Request?>(null)
        private set

    /** [editor] names the editor for the return bar; [video] picks a video mode to start in (else a photo mode). */
    fun open(editor: String, video: Boolean, onMedia: (uri: Uri, isVideo: Boolean) -> Unit) {
        request = Request(editor, video, onMedia)
    }

    fun close() { request = null }
}

@Composable
fun CaptureForEditorHost(controller: ProCameraController, gate: @Composable (@Composable () -> Unit) -> Unit) {
    val req = CaptureForEditor.request ?: return
    val context = LocalContext.current
    val s by controller.state.collectAsStateWithLifecycle()
    // Only what's taken from now on goes to the editor.
    val before = remember(req) { s.lastMedia }
    val delivered = remember(req) { HashSet<String>() }
    var count by remember(req) { mutableIntStateOf(0) }
    LaunchedEffect(req) {
        val m = controller.state.value.mode
        if (req.video && !m.isVideo) controller.setMode(CaptureMode.PRO_VIDEO)
        if (!req.video && m.isVideo) controller.setMode(CaptureMode.PRO_PHOTO)
    }
    LaunchedEffect(s.lastMedia, s.processing) {
        val u = s.lastMedia ?: return@LaunchedEffect
        if (u == before || s.processing || !delivered.add(u.toString())) return@LaunchedEffect
        val isVideo = context.contentResolver.getType(u)?.startsWith("video/") == true
        req.onMedia(u, isVideo)
        count++
    }
    androidx.activity.compose.BackHandler { CaptureForEditor.close() }
    Box(Modifier.fillMaxSize().zIndex(50f).background(Color.Black)) {
        gate { com.dronedirector.veyrocutpro.ui.ForceDark { com.dronedirector.veyrocutpro.ui.CameraScreen(controller) } }
        Text(
            "← Back to ${req.editor}" + if (count > 0) " · $count added" else "",
            color = Color.Black, fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp)
                .clip(RoundedCornerShape(50)).background(Pro.Amber).clickable { CaptureForEditor.close() }
                .padding(horizontal = 16.dp, vertical = 7.dp),
        )
    }
}
