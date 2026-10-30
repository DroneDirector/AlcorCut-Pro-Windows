package com.dronedirector.veyrocutpro.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.border
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.dronedirector.veyrocutpro.camera.AspectMode
import com.dronedirector.veyrocutpro.camera.CameraUiState
import com.dronedirector.veyrocutpro.camera.CaptureMode
import com.dronedirector.veyrocutpro.camera.ProCameraController
import kotlinx.coroutines.delay

@Composable
fun CameraScreen(controller: ProCameraController, openSettings: Boolean = false, openGallery: Boolean = false) {
    val s by controller.state.collectAsStateWithLifecycle()
    val analysis by controller.analysis.collectAsStateWithLifecycle()
    val audio by controller.audioMeter.levels.collectAsStateWithLifecycle()
    val liveState by controller.live.state.collectAsStateWithLifecycle()
    val liveMode = s.mode == CaptureMode.LIVE
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    var showSettings by rememberSaveable { mutableStateOf(openSettings) }
    var showGallery by rememberSaveable { mutableStateOf(openGallery) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    LaunchedEffect(previewView) { controller.attach(lifecycleOwner, previewView) }
    DisposableEffect(Unit) { onDispose { controller.detach() } }

    // Keep capture orientation in sync, including 180° flips that don't change the configuration.
    DisposableEffect(view) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                view.display?.let { controller.setDisplayRotation(it.rotation) }
            }
        }
        dm.registerDisplayListener(listener, null)
        view.display?.let { controller.setDisplayRotation(it.rotation) }
        onDispose { dm.unregisterDisplayListener(listener) }
    }

    // Samsung DeX / desktop mode on a wide monitor: take photos and videos wide, not upright.
    run {
        val mon = onExternalMonitor()
        val conf = androidx.compose.ui.platform.LocalConfiguration.current
        val wide = conf.screenWidthDp > conf.screenHeightDp
        LaunchedEffect(mon, wide) { controller.setMonitorLandscape(mon && wide) }
    }

    // Live audio levels in the video modes (the recorder takes over while recording).
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    // With headphone monitoring on (and headphones connected) the mic keeps playing while recording.
    LaunchedEffect(s.mode, s.recording, s.audio, resumed, s.headphoneMonitor, s.headphones) {
        val listen = s.headphoneMonitor && s.headphones != null
        controller.setAudioMonitoring(s.mode.isNormalVideo && s.audio && resumed && (!s.recording || listen))
    }
    // Re-check the SD card when coming back to the app.
    LaunchedEffect(resumed) { if (resumed) controller.refreshStorage() }

    val (manualIso, manualShutter) = controller.effectiveExposure(s)
    val isoShown = if (s.manualExposure) manualIso else s.liveIso
    val shutterShown = if (s.manualExposure) manualShutter else s.liveShutterNs

    // Notch / camera-cutout size in pixels; full-screen video draws underneath it.
    val rootDensity = LocalDensity.current
    val cutout = WindowInsets.displayCutout
    val cutL = cutout.getLeft(rootDensity, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val cutT = cutout.getTop(rootDensity)
    val cutR = cutout.getRight(rootDensity, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val cutB = cutout.getBottom(rootDensity)

    // The gallery sits above everything, edge to edge, so no camera feed shows around it.
    val upCtx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { com.dronedirector.veyrocutpro.camera.UpscaleSettings.load(upCtx) }
    val upNote = com.dronedirector.veyrocutpro.camera.Upscaler.lastNote
    LaunchedEffect(upNote) {
        if (upNote != null) {
            android.widget.Toast.makeText(upCtx, com.dronedirector.veyrocutpro.ui.I18n.tr(upNote), android.widget.Toast.LENGTH_SHORT).show()
            com.dronedirector.veyrocutpro.camera.Upscaler.lastNote = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        UpscaleBadge(Modifier.align(Alignment.TopCenter).zIndex(10f))
        // Teleprompter: floats over everything, draggable.
        Box(Modifier.fillMaxSize().zIndex(20f)) { TeleprompterOverlay(s.recording, s.isFront) }
        // Stop motion: each new photo is the next frame.
        LaunchedEffect(s.lastMedia) { val u = s.lastMedia; if (StopMotion.active && u != null && !s.isVideo) StopMotion.add(u) }
        if (StopMotion.editing) StopMotionEditor { StopMotion.editing = false }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        // Tablets: draw everything ~25% larger so controls suit the bigger screen.
        val tablet = minOf(maxWidth, maxHeight) >= 600.dp
        val baseDensity = LocalDensity.current
        val scale = if (tablet) (minOf(maxWidth, maxHeight).value / 600f).coerceIn(1.15f, 1.35f) else 1f

        CompositionLocalProvider(LocalDensity provides Density(baseDensity.density * scale, baseDensity.fontScale)) {
            // External ultra-wide monitors: the camera in the middle, a big histogram and the exposure read-out at the sides.
            MonitorCaptureFrame(analysis, s) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val landscape = maxWidth > maxHeight

                // LIVE: the camera fills the whole screen, edge to edge, in any orientation.
                if (liveMode) {
                    LiveViewfinder(controller.live, Modifier.fillMaxSize())
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                if (landscape) androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    0f to Color.Black.copy(alpha = 0.55f), 0.12f to Color.Transparent,
                                    0.85f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f),
                                ) else androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.55f), 0.12f to Color.Transparent,
                                    0.72f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f),
                                )
                            )
                    )
                }

                val sideBar = if (tablet) 84.dp else 76.dp
                val rail = if (tablet) 132.dp else 120.dp
                val topBar = if (tablet) 60.dp else 56.dp
                val bottomBar = if (tablet) 226.dp else 214.dp
                // One-handed (Appearance): on a phone held upright the top bar moves down, within thumb reach.
                val oneHand = !landscape && !tablet && OneHanded.side != 0
                val topPad = if (oneHand) 0.dp else topBar
                val botPad = if (oneHand) bottomBar + topBar else bottomBar
                // Space taken by the control bars around the viewfinder.
                val barPad = androidx.compose.foundation.layout.PaddingValues(
                    start = if (landscape) sideBar else 0.dp,
                    end = if (landscape) rail else 0.dp,
                    top = if (landscape) 0.dp else topPad,
                    bottom = if (landscape) 0.dp else botPad,
                )
                // Video modes fill the whole screen edge to edge; photo modes keep exact framing.
                // Clean feed: nothing but the picture, edge to edge (for HDMI out / mirroring / capture).
                val clean = s.cleanFeed && !liveMode
                val fillScreen = (s.isVideo || clean) && !liveMode
                val noPad = androidx.compose.foundation.layout.PaddingValues(0.dp)

                val sensorRatio = if (s.isVideo || (s.mode != CaptureMode.PANORAMA && s.aspect == AspectMode.R16_9)) 16f / 9f else 4f / 3f
                val frameRatio = when {
                    liveMode -> if (liveState.vertical) 9f / 16f else 16f / 9f
                    landscape -> sensorRatio
                    else -> 1f / sensorRatio
                }

                // Photo modes keep the exact frame, but when it would leave black bars at the sides
                // (e.g. 16:9 on a phone held upright) it is widened edge to edge instead and allowed to
                // run under the controls, like the stock camera.
                val freeW = maxWidth - (if (landscape) sideBar + rail else 0.dp)
                val freeH = maxHeight - (if (landscape) 0.dp else topPad + botPad)
                val freeRatio = freeW.value / freeH.value
                val widen = !fillScreen && !liveMode &&
                    (if (landscape) freeRatio < frameRatio - 0.01f else freeRatio > frameRatio + 0.01f)
                var frameW = 0.dp; var frameH = 0.dp; var frameX = 0.dp; var frameY = 0.dp
                if (widen) {
                    if (landscape) {
                        frameH = maxHeight; frameW = maxHeight * frameRatio
                        if (frameW > maxWidth) { frameW = maxWidth; frameH = maxWidth / frameRatio }
                        frameX = if (sideBar + frameW <= maxWidth) sideBar else (maxWidth - frameW) / 2
                        frameY = (maxHeight - frameH) / 2
                    } else {
                        frameW = maxWidth; frameH = maxWidth / frameRatio
                        if (frameH > maxHeight) { frameH = maxHeight; frameW = maxHeight * frameRatio }
                        frameY = if (topPad + frameH <= maxHeight) topPad else (maxHeight - frameH) / 2
                        frameX = (maxWidth - frameW) / 2
                    }
                }
                // Parts of a widened frame that sit under the bars: read-outs stay clear of them.
                val coverPad = if (!widen) noPad else if (landscape) androidx.compose.foundation.layout.PaddingValues(
                    start = (sideBar - frameX).coerceAtLeast(0.dp),
                    end = (frameX + frameW - (maxWidth - rail)).coerceAtLeast(0.dp),
                ) else androidx.compose.foundation.layout.PaddingValues(
                    top = (topPad - frameY).coerceAtLeast(0.dp),
                    bottom = (frameY + frameH - (maxHeight - botPad)).coerceAtLeast(0.dp),
                )
                val overlayControls = fillScreen || widen

                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(if (overlayControls) noPad else barPad),
                    contentAlignment = if (widen) Alignment.TopStart else Alignment.Center,
                ) {

                    if (liveMode) {
                        Box(Modifier.fillMaxSize()) {
                            GridOverlay(s.grid)
                            LiveBadge(liveState, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                        }
                    } else Box(
                        if (fillScreen) Modifier.fillMaxSize().layout { m, c ->
                            // Bleed under the notch so there is no black band at the edge.
                            val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(c.maxWidth + cutL + cutR, c.maxHeight + cutT + cutB))
                            layout(c.maxWidth, c.maxHeight) { p.place(-cutL, -cutT) }
                        } else if (widen) {
                            Modifier.offset(frameX, frameY).size(frameW, frameH)
                        } else Modifier.aspectRatio(frameRatio)
                    ) {
                        AndroidView(
                            factory = {
                                (previewView.parent as? ViewGroup)?.removeView(previewView)
                                previewView
                            },
                            // Anamorphic lenses: show the picture at its real (desqueezed) shape.
                            modifier = Modifier.fillMaxSize().graphicsLayer { scaleY = 1f / com.dronedirector.veyrocutpro.camera.CaptureExtras.desqueeze },
                        )
                        // AI PORTRAIT: live background blur over the viewfinder (the subject stays live and sharp).
                        if (s.mode == CaptureMode.AI_PORTRAIT) PortraitLivePreview(
                            previewView,
                            Modifier.fillMaxSize().graphicsLayer { scaleY = 1f / com.dronedirector.veyrocutpro.camera.CaptureExtras.desqueeze },
                        )
                        if (com.dronedirector.veyrocutpro.camera.CaptureExtras.desqueeze != 1f && !clean) Text(
                            "ANAMORPHIC ${com.dronedirector.veyrocutpro.camera.CaptureExtras.desqueeze}×",
                            style = Pro.Label.copy(color = Color.Black),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(Pro.Amber).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        if (overlayControls && !clean) {
                            // Soft shading behind the floating controls.
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (landscape) androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            0f to Color.Black.copy(alpha = 0.55f), 0.12f to Color.Transparent,
                                            0.85f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f),
                                        ) else androidx.compose.ui.graphics.Brush.verticalGradient(
                                            0f to Color.Black.copy(alpha = 0.55f), 0.1f to Color.Transparent,
                                            0.66f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f),
                                        )
                                    )
                            )
                        }

                        if (s.isPro) {
                            analysis?.overlay?.let { bmp ->
                                Image(
                                    bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        if (!s.isVideo) OnionSkin()
                        if (!clean) {
                            if (!s.isVideo && s.mode != CaptureMode.PANORAMA && s.aspect == AspectMode.R1_1) SquareMask()
                            if (s.mode == CaptureMode.PRO_VIDEO) FrameGuideOverlay(s.guide)
                            if (com.dronedirector.veyrocutpro.camera.CaptureExtras.safeAreas && s.mode != CaptureMode.PANORAMA) SafeAreasOverlay()
                            if (s.mode != CaptureMode.PANORAMA) GridOverlay(s.grid)
                            if (s.showLevel && s.mode != CaptureMode.PANORAMA) LevelIndicator()
                        }

                        // Gestures: tap to focus/meter, pinch to zoom, short hold = exposure lock,
                        // 2-second hold = manual focus (all modes).
                        var holdAt by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .holdGestures(
                                    onLock = { o ->
                                        if (controller.state.value.aeLockPoint != null) controller.unlockExposure()
                                        else controller.lockExposureAt(o.x, o.y)
                                    },
                                    onManualFocus = { o -> controller.holdManualFocus(o.x, o.y) },
                                    onHolding = { holdAt = it },
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { o -> controller.tapToFocus(o.x, o.y) },
                                        // Split focus / exposure: double-tap sets the exposure point.
                                        onDoubleTap = { o -> if (com.dronedirector.veyrocutpro.camera.CaptureExtras.splitAfAe) controller.meterAt(o.x, o.y) else controller.setActiveParam(null) },
                                    )
                                }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, zoom, _ ->
                                        if (zoom != 1f) controller.setZoom(controller.state.value.zoom * zoom)
                                    }
                                }
                                // Mouse: wheel zooms, right-click locks / unlocks exposure and focus there, middle-click takes the shot.
                                .mouseActions(
                                    onWheel = { dy, _, _, _, _ -> controller.setZoom(controller.state.value.zoom * (if (dy > 0) 0.92f else 1.087f)); true },
                                    onRightClick = { o -> if (controller.state.value.aeLockPoint != null) controller.unlockExposure() else controller.lockExposureAt(o.x, o.y) },
                                    onMiddleClick = { controller.shutter() },
                                )
                        )
                        FocusReticle(s.focusPoint, s.focusId, locked = s.aeLock || s.manualFocusActive)
                        // Separate exposure point (split focus / exposure): a small sun-like ring.
                        com.dronedirector.veyrocutpro.camera.CaptureExtras.aePoint?.takeIf { com.dronedirector.veyrocutpro.camera.CaptureExtras.splitAfAe }?.let { (ax, ay) ->
                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                val r = 18.dp.toPx(); val c0 = androidx.compose.ui.geometry.Offset(ax, ay)
                                drawCircle(Pro.Amber, r, c0, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                                for (k in 0 until 8) {
                                    val a2 = k * Math.PI / 4
                                    val d0 = androidx.compose.ui.geometry.Offset((kotlin.math.cos(a2) * r * 1.25).toFloat(), (kotlin.math.sin(a2) * r * 1.25).toFloat())
                                    val d1 = androidx.compose.ui.geometry.Offset((kotlin.math.cos(a2) * r * 1.6).toFloat(), (kotlin.math.sin(a2) * r * 1.6).toFloat())
                                    drawLine(Pro.Amber, c0 + d0, c0 + d1, 2.dp.toPx())
                                }
                            }
                        }
                        HoldRing(holdAt, (HOLD_MF_MS - HOLD_LOCK_MS).toInt())
                        LongExposureOverlay(s, controller)
                        if (!clean) AeLockOverlay(s, controller)
                        PanoramaOverlay(s)
                        ShutterFlash(s.shutterFlash)
                        Countdown(s.countdown)

                        // Read-outs and badges stay inside the area not covered by the control bars.
                        if (!clean) Box(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (fillScreen) with(LocalDensity.current) {
                                        Modifier
                                            .padding(start = cutL.toDp(), top = cutT.toDp(), end = cutR.toDp(), bottom = cutB.toDp())
                                            .padding(barPad)
                                    } else Modifier.padding(coverPad)
                                )
                        ) {
                        // Draggable read-outs: drag them anywhere; positions are remembered.
                        if (s.isPro) {
                            if (panelOn("cap.hud")) DraggableOverlay("hud", Alignment.TopStart, s.layoutVersion) {
                                ExposureHud(s, isoShown, shutterShown)
                            }
                            if (s.showHistogram) {
                                DraggableOverlay("histogram", Alignment.TopEnd, s.layoutVersion) {
                                    val scope = com.dronedirector.veyrocutpro.camera.CaptureExtras.scope
                                    if (scope == 0) Histogram(
                                        analysis,
                                        width = if (tablet) 150.dp else 112.dp,
                                        height = if (tablet) 62.dp else 52.dp,
                                    ) else LiveScope(previewView, scope, if (tablet) 180.dp else 132.dp, if (tablet) 90.dp else 70.dp)
                                }
                            }
                            // Focus magnifier while the manual focus control is open.
                            if (com.dronedirector.veyrocutpro.camera.CaptureExtras.focusMagnifier && s.activeParam == com.dronedirector.veyrocutpro.camera.ProParam.FOCUS && s.manualFocus) {
                                FocusMagnifier(previewView, s.focusPoint, Modifier.align(Alignment.Center))
                            }
                        }
                        // Manual focus rail on the right edge of the feed, with ✕ back to autofocus.
                        var railDragging by remember { mutableStateOf(false) }
                        if (s.manualFocusActive && !s.sceneActive && s.minFocusDiopters > 0f) {
                            FocusRail(s, controller, onDragging = { railDragging = it }, modifier = Modifier.fillMaxSize())
                            val proMagnifier = s.isPro && s.activeParam == com.dronedirector.veyrocutpro.camera.ProParam.FOCUS
                            if (railDragging && !proMagnifier && com.dronedirector.veyrocutpro.camera.CaptureExtras.focusMagnifier) {
                                FocusMagnifier(previewView, s.focusPoint, Modifier.align(Alignment.Center))
                            }
                        } else railDragging = false
                        // Stop motion: on the picture, drag it anywhere, pinch to resize, double tap to make it small.
                        if (StopMotion.active && !s.isVideo) DraggableOverlay("stopmotion", Alignment.TopCenter, s.layoutVersion) { StopMotionBar() }
                        if (s.mode.isNormalVideo && s.audio && panelOn("cap.audio")) {
                            DraggableOverlay("audio", if (s.isPro) Alignment.BottomEnd else Alignment.TopStart, s.layoutVersion) {
                                AudioGraph(
                                    audio,
                                    width = if (s.isPro) 150.dp else 120.dp,
                                    height = if (s.isPro) 40.dp else 30.dp,
                                    recording = s.recording,
                                )
                            }
                        }
                        Column(
                            Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RecordingBadge(s)
                            IntervalBadge(s)
                        }
                        if (s.proFalseColor) FalseColorLegend(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = if (s.isPro) 100.dp else 64.dp))
                        // Title preview (video): where the title will be burned in.
                        val xt = com.dronedirector.veyrocutpro.camera.CaptureExtras
                        if (s.isVideo && xt.titleOn && xt.titleText.isNotBlank() && panelOn("cap.titlepreview")) {
                            Text(
                                xt.titleText, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .align(when (xt.titlePos) { 1 -> Alignment.Center; 2 -> Alignment.TopCenter; else -> Alignment.BottomCenter })
                                    .padding(top = if (xt.titlePos == 2) 56.dp else 0.dp, bottom = if (xt.titlePos == 0) 120.dp else 0.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = if (xt.titlePos == 0) 0.45f else 0.2f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        // Slate badge: scene / shot / take.
                        if (xt.slateOn && panelOn("cap.slate")) {
                            Text(
                                com.dronedirector.veyrocutpro.camera.Slate.label(), style = Pro.Value.copy(fontSize = 11.sp, color = Color.White),
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 38.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        // Cast to a TV: on the picture (default; drag it anywhere, pinch to resize, double tap to make it small), unless the gear puts it below.
                        if (!clean && tablet && panelOn("cap.cast") && CastPlace.get(context, "cap") == 0)
                            DraggableOverlay("cast", Alignment.CenterEnd, s.layoutVersion) { CastButton({ controller.castFrame() }) }
                        val hints = panelOn("cap.hints")
                        if (hints) SmartModeHint(s, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
                        ProcessingBadge(s, Modifier.align(Alignment.Center))
                        if (s.sceneActive && hints) {
                            Text(
                                s.scene.label,
                                style = Pro.Value.copy(color = Pro.Amber),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        }
                    }

                    val panelPos = Modifier.align(Alignment.BottomCenter).padding(if (overlayControls) barPad else noPad)
                    if (clean) {
                        // No panels in clean feed.
                    } else if (liveMode) {
                        LiveBar(liveState, controller.live, Modifier.align(Alignment.BottomCenter))
                    } else if (s.isPro) {
                        if (panelOn("cap.controls")) ProPanel(s, controller, isoShown, shutterShown, panelPos)
                    } else if (!s.panoActive && !s.stitching) {
                        SimpleBar(s, controller, panelPos)
                    }
                }

                if (clean) {
                    // Controls hidden; double-tap to bring them back (layer below).
                } else if (landscape) {
                    QuickBar(
                        s, controller, vertical = true, onSettings = { showSettings = true },
                        panelsButton = { CapturePanelsButton(s, controller) },
                        modifier = Modifier.align(Alignment.CenterStart).width(sideBar).fillMaxHeight(),
                    )
                    CaptureControls(
                        s, controller, vertical = true, tablet = tablet, onGallery = { showGallery = true },
                        modifier = Modifier.align(Alignment.CenterEnd).width(rail).fillMaxHeight(),
                    )
                } else {
                    QuickBar(
                        s, controller, vertical = false, onSettings = { showSettings = true },
                        panelsButton = { CapturePanelsButton(s, controller) },
                        modifier = if (oneHand) Modifier.align(Alignment.BottomCenter).padding(bottom = bottomBar).fillMaxWidth().height(topBar)
                        else Modifier.align(Alignment.TopCenter).fillMaxWidth().height(topBar),
                    )
                    CaptureControls(
                        s, controller, vertical = false, tablet = tablet, onGallery = { showGallery = true },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomBar),
                        hand = if (oneHand) OneHanded.side else 0,
                    )
                }

                // Cast button below the picture (gear → Remote & output), just above the controls.
                if (!clean && tablet && panelOn("cap.cast") && CastPlace.get(context, "cap") == 1) CastButton(
                    { controller.castFrame() }, small = true,
                    modifier = if (landscape) Modifier.align(Alignment.BottomEnd).padding(end = rail + 6.dp, bottom = 12.dp)
                    else Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = bottomBar + 6.dp),
                )
                // Pro status: red tally border while recording, low storage / battery / heat warnings, touch lock.
                if (!clean) ProStatus(s)
                ToastHost(s.message, s.messageId, Modifier.align(Alignment.Center))
                ToastHost(liveState.message, liveState.messageId, Modifier.align(Alignment.Center))
                if (liveMode) LiveSetupPanel(liveState, controller.live)

                if (clean) {
                    androidx.activity.compose.BackHandler { controller.setCleanFeed(false) }
                    var cleanHoldAt by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .holdGestures(
                                onLock = { o ->
                                    if (controller.state.value.aeLockPoint != null) controller.unlockExposure()
                                    else controller.lockExposureAt(o.x + cutL, o.y + cutT)
                                },
                                onManualFocus = { o -> controller.holdManualFocus(o.x + cutL, o.y + cutT) },
                                onHolding = { cleanHoldAt = it },
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { o -> controller.tapToFocus(o.x + cutL, o.y + cutT) },
                                    onDoubleTap = { controller.setCleanFeed(false) },
                                )
                            }
                    )
                    HoldRing(cleanHoldAt, (HOLD_MF_MS - HOLD_LOCK_MS).toInt())
                    var hint by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) { delay(2500); hint = false }
                    AnimatedVisibility(hint, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                        Text(
                            "Clean feed · double-tap to show controls",
                            color = Color.White, fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }

                // The settings panel follows the light / dark look; the controls on the picture stay dark.
                if (showSettings) androidx.compose.runtime.CompositionLocalProvider(LocalForceDark provides false) { SettingsPanel(s, controller) { showSettings = false } }
                run {
                    val tctx = androidx.compose.ui.platform.LocalContext.current
                    LaunchedEffect(Unit) { Tour.maybeStart(tctx) }
                    if (Tour.visible && !showSettings) TourOverlay()
                }
            }
            }
        }
    }
    if (showGallery) GalleryScreen(onClose = { showGallery = false }, onChanged = { controller.refreshLastMedia() })
    }
}

@Composable
private fun ToastHost(message: String?, id: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        if (id == 0 || message == null) return@LaunchedEffect
        visible = message
        delay(2600)
        visible = null
    }
    AnimatedVisibility(visible = visible != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            visible ?: "",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** Key for the false-colour bands. */
@Composable
private fun FalseColorLegend(modifier: Modifier = Modifier) {
    val bands = listOf(
        Color(0xFFFF2A2A) to "CLIP",
        Color(0xFFFFE03B) to "HIGH",
        Color(0xFFFF7FAA) to "SKIN",
        Color(0xFF2EBD4F) to "18%",
        Color(0xFF1E5BFF) to "LOW",
        Color(0xFF6A1B9A) to "CRUSH",
    )
    Column(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        bands.forEach { (c, label) ->
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(10.dp).height(10.dp).background(c))
                Text(label, color = Color.White, fontSize = 8.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

/** Shows AI upscaling progress while photos are being processed in the background. */
@Composable
private fun UpscaleBadge(modifier: Modifier) {
    val n = com.dronedirector.veyrocutpro.camera.Upscaler.pending
    // Cloud AI stage (upload / processing / download) replaces the on-phone badge while it runs.
    val cloudStage = com.dronedirector.veyrocutpro.camera.CloudAi.stage
    if (cloudStage != null) {
        AiBadge(
            cloudStage, 1, com.dronedirector.veyrocutpro.camera.CloudAi.progress,
            onStop = { com.dronedirector.veyrocutpro.camera.CloudAi.stop(); com.dronedirector.veyrocutpro.camera.Upscaler.cancelAll() },
            modifier = modifier,
        )
        return
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = n > 0,
        modifier = modifier,
        enter = androidx.compose.animation.slideInVertically { -it * 2 } + androidx.compose.animation.fadeIn() +
            androidx.compose.animation.scaleIn(initialScale = 0.7f),
        exit = androidx.compose.animation.slideOutVertically { -it * 2 } + androidx.compose.animation.fadeOut(),
    ) {
        AiBadge(
            "AI UPSCALING", n.coerceAtLeast(1), com.dronedirector.veyrocutpro.camera.Upscaler.progress,
            onStop = { com.dronedirector.veyrocutpro.camera.Upscaler.cancelAll() },
        )
    }
}


/** Panels menu for the Capture screen (icon next to the settings gear). */
@Composable
private fun CapturePanelsButton(s: CameraUiState, controller: ProCameraController) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val panels = listOf(
        PanelDef("cap.hud", "Exposure read-out", "ISO, shutter, aperture and focus values (Pro modes)"),
        PanelDef("cap.histogram", "Histogram / scope", "Live brightness graph, waveform, parade or vectorscope (Pro modes)",
            get = { s.showHistogram }, put = { controller.toggleHistogram() }),
        PanelDef("cap.audio", "Audio meter", "Sound level graph while filming"),
        PanelDef("cap.level", "Level", "Horizon line that turns green when straight",
            get = { s.showLevel }, put = { controller.toggleLevel() }),
        PanelDef("cap.controls", "Pro controls bar", "ISO, shutter, focus, WB, EV and zoom chips (Pro modes)"),
        PanelDef("cap.hints", "Tips & scene badge", "Hints for smart modes and the active scene label"),
        PanelDef("cap.tally", "Tally border", "Red frame around the screen while recording"),
        PanelDef("cap.warnings", "Warnings", "Low storage, low battery and heat warnings"),
        PanelDef("cap.slate", "Slate badge", "Scene / shot / take on the viewfinder (when the slate is on)"),
        PanelDef("cap.presets", "Custom set-ups C1–C3", "Tap to recall, hold to save the current settings"),
        PanelDef("cap.qblur", "Face blur switch", "BLUR on / off in the quick bar"),
        PanelDef("cap.qtitle", "Title switch", "TITLE on / off in the quick bar (video)"),
        PanelDef("cap.titlepreview", "Title preview", "Shows where your video title will appear"),
        PanelDef("cap.cast", "Cast button", "Chromecast button: show the live picture on a TV"),
    )
    val all = panels.map { it.key }.toSet()
    PanelsButton(
        prefix = "cap",
        panels = panels,
        presets = listOf(
            PanelPreset("Full", all),
            PanelPreset("Minimal", setOf("cap.controls")),
            PanelPreset("Video", setOf("cap.hud", "cap.histogram", "cap.audio", "cap.controls", "cap.qtitle", "cap.titlepreview")),
        ),
        onReset = {
            OverlayLayout.reset(ctx)
            controller.bumpLayoutVersion()
            controller.toast("Layout reset to original")
        },
    )
}

/** Exposure lock from a long press: lock icon with a tiny X, and an EV slider under it. */
@Composable
private fun AeLockOverlay(s: CameraUiState, c: ProCameraController) {
    val pt = s.aeLockPoint ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }; val hPx = with(density) { maxHeight.toPx() }
        val boxW = with(density) { 190.dp.toPx() }; val boxH = with(density) { 96.dp.toPx() }
        val left = (pt.first - boxW / 2f).coerceIn(0f, (wPx - boxW).coerceAtLeast(0f))
        val top = (pt.second - with(density) { 40.dp.toPx() }).coerceIn(0f, (hPx - boxH).coerceAtLeast(0f))
        Column(
            Modifier.offset { androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()) }.width(190.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.dronedirector.veyrocutpro.ui.Icon(
                        androidx.compose.material.icons.Icons.Filled.Lock, contentDescription = "Exposure locked",
                        tint = if (s.aeLock) Pro.Amber else Color.White, modifier = Modifier.size(16.dp),
                    )
                    Text(
                        if (s.aeLock) "  AE LOCK  %+.1f".format(s.ev * s.evStep) else "  LOCKING…",
                        style = Pro.Value.copy(fontSize = 12.sp, color = if (s.aeLock) Pro.Amber else Color.White),
                    )
                }
                // Very small X: unlock.
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp).size(18.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.75f)).clickable { c.unlockExposure() },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = Color.White, fontSize = 9.sp) }
            }
            if (s.evMax > s.evMin) {
                androidx.compose.material3.Slider(
                    value = s.ev.toFloat(),
                    onValueChange = { c.setEv(kotlin.math.round(it).toInt()) },
                    valueRange = s.evMin.toFloat()..s.evMax.toFloat(),
                    steps = (s.evMax - s.evMin - 1).coerceAtLeast(0),
                    modifier = Modifier.width(180.dp).height(30.dp),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Pro.Amber, activeTrackColor = Pro.Amber, inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

/** Hold this long and let go: exposure lock. */
private const val HOLD_LOCK_MS = 450L
/** Hold this long: manual focus. */
private const val HOLD_MF_MS = 2000L

private enum class HoldEnd { RELEASED, BROKEN }

/** Waits up to [ms] while the finger stays still; null = still holding when time ran out. */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.watchHold(
    down: androidx.compose.ui.input.pointer.PointerInputChange,
    slop: Float,
    ms: Long,
    consume: Boolean,
): HoldEnd? = withTimeoutOrNull(ms) {
    while (true) {
        val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        if (consume) ev.changes.forEach { it.consume() }
        if (ev.changes.count { it.pressed } > 1) return@withTimeoutOrNull HoldEnd.BROKEN   // pinch: not a hold
        val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull HoldEnd.BROKEN
        if (!ch.pressed) return@withTimeoutOrNull HoldEnd.RELEASED
        if ((ch.position - down.position).getDistance() > slop) return@withTimeoutOrNull HoldEnd.BROKEN
    }
    @Suppress("UNREACHABLE_CODE") HoldEnd.BROKEN
}

/**
 * Press-and-hold on the viewfinder, in two stages:
 *  - after 0.45 s a tick and a ring appear; let go before the ring closes → [onLock] (exposure lock).
 *  - keep holding to 2 s → [onManualFocus].
 * A quick tap is left alone so the normal tap-to-focus still works.
 */
private fun Modifier.holdGestures(
    onLock: (androidx.compose.ui.geometry.Offset) -> Unit,
    onManualFocus: (androidx.compose.ui.geometry.Offset) -> Unit,
    onHolding: (androidx.compose.ui.geometry.Offset?) -> Unit,
): Modifier = composed {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val lock by androidx.compose.runtime.rememberUpdatedState(onLock)
    val mf by androidx.compose.runtime.rememberUpdatedState(onManualFocus)
    val holding by androidx.compose.runtime.rememberUpdatedState(onHolding)
    pointerInput(Unit) {
        val slop = 24.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            if (watchHold(down, slop, HOLD_LOCK_MS, consume = false) != null) return@awaitEachGesture   // tap, drag or pinch
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            holding(down.position)
            val end = try {
                watchHold(down, slop, HOLD_MF_MS - HOLD_LOCK_MS, consume = true)
            } finally {
                holding(null)
            }
            when (end) {
                HoldEnd.RELEASED -> {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    lock(down.position)
                }
                HoldEnd.BROKEN, null -> {
                    if (end == null) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        mf(down.position)
                    }
                    // Swallow the rest of this touch so it doesn't also count as a tap.
                    do {
                        val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        ev.changes.forEach { it.consume() }
                    } while (ev.changes.any { it.pressed })
                }
            }
        }
    }
}

/**
 * Tally border while recording, warnings (storage, battery, heat) and a touch lock that stops
 * accidental taps during a take (hold the padlock for a moment to unlock).
 */
@Composable
private fun ProStatus(s: CameraUiState) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var locked by remember { mutableStateOf(false) }
    if (!s.recording && locked) locked = false
    // Warnings, checked every few seconds.
    var warn by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            val w = mutableListOf<String>()
            try {
                val free = android.os.Environment.getExternalStorageDirectory().usableSpace
                if (free < 2L * 1024 * 1024 * 1024) w += "STORAGE %.1f GB".format(free / 1e9)
            } catch (_: Throwable) {}
            try {
                val bm = ctx.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
                val pct = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (pct in 1..15 && !bm.isCharging) w += "BATTERY $pct%"
            } catch (_: Throwable) {}
            if (android.os.Build.VERSION.SDK_INT >= 29) try {
                val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                val t = pm.currentThermalStatus
                if (t >= android.os.PowerManager.THERMAL_STATUS_SEVERE) w += "HOT — MAY STOP" else if (t >= android.os.PowerManager.THERMAL_STATUS_MODERATE) w += "WARM"
            } catch (_: Throwable) {}
            warn = w
            delay(5000)
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (s.recording && panelOn("cap.tally")) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawRect(Pro.Red, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
            }
        }
        if (warn.isNotEmpty() && panelOn("cap.warnings")) {
            Row(Modifier.align(Alignment.TopCenter).padding(top = 64.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                warn.forEach {
                    Text(it, style = Pro.Label.copy(color = Color.Black, fontSize = 10.sp),
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Pro.Amber).padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
        }
        if (s.recording) {
            if (locked) {
                // Eats every touch except the padlock.
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { }
                })
            }
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(44.dp).clip(CircleShape)
                    .background((if (locked) Pro.Red else Color.Black).copy(alpha = 0.6f))
                    .pointerInput(locked) {
                        detectTapGestures(
                            onTap = { if (!locked) { locked = true; haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) } },
                            onLongPress = { if (locked) { locked = false; haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) } },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                com.dronedirector.veyrocutpro.ui.Icon(
                    if (locked) androidx.compose.material.icons.Icons.Filled.Lock else androidx.compose.material.icons.Icons.Filled.LockOpen,
                    contentDescription = if (locked) "Screen locked — hold to unlock" else "Lock the screen", tint = Color.White, modifier = Modifier.size(20.dp),
                )
            }
            if (locked) Text(
                "Screen locked · hold 🔒 to unlock", style = Pro.Label.copy(color = Color.White, fontSize = 10.sp),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 62.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(4.dp),
            )
        }
    }
}

/** On an ultra-wide / super ultra-wide external monitor, keeps the camera at a sensible shape in the middle with side panels; elsewhere it shows [content] unchanged. */
@androidx.compose.runtime.Composable
private fun MonitorCaptureFrame(analysis: com.dronedirector.veyrocutpro.camera.AnalysisResult?, s: com.dronedirector.veyrocutpro.camera.CameraUiState, content: @androidx.compose.runtime.Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val mon = monitorClass(maxWidth.value, maxHeight.value)
        if (mon != MonitorClass.ULTRA_WIDE && mon != MonitorClass.SUPER_ULTRA_WIDE) { content(); return@BoxWithConstraints }
        val centreW = minOf(maxWidth, maxHeight * 1.9f)
        val side = (maxWidth - centreW) / 2
        androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column(Modifier.width(side).fillMaxHeight().background(Color(0xFF0B0D12)).padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                Text("HISTOGRAM", style = Pro.Label.copy(color = Pro.Amber))
                Histogram(analysis, width = side - 32.dp, height = (side - 32.dp) * 0.45f)
            }
            Box(Modifier.width(centreW).fillMaxHeight()) { content() }
            androidx.compose.foundation.layout.Column(Modifier.width(side).fillMaxHeight().background(Color(0xFF0B0D12)).padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                Text("EXPOSURE", style = Pro.Label.copy(color = Pro.Amber))
                ExposureHud(s, null, null)
            }
        }
    }
}
