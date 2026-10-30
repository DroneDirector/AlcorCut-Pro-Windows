package com.dronedirector.veyrocutpro.ui

import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.PhotoFormat
import com.dronedirector.veyrocutpro.camera.CameraUiState
import com.dronedirector.veyrocutpro.camera.ProCameraController
import kotlin.math.roundToInt

@Composable
fun SettingsPanel(s: CameraUiState, c: ProCameraController, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Pro.PanelSolid)
                // Swallow taps so they don't close the panel.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .hintedScroll(horizontal = false)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClose).padding(6.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            var query by androidx.compose.runtime.remember { mutableStateOf("") }
            SettingsSearchField(query) { query = it }
            androidx.compose.runtime.CompositionLocalProvider(LocalSettingsQuery provides query) {
            val sctx = androidx.compose.ui.platform.LocalContext.current

            SettingsGroup("photo", "Photo", "Format (JPEG / RAW), photo size, burst, self-timer, interval, bracket, auto-enhance") {
                Section("PHOTO")
                // Pro photo file format: exactly one of the three is on; the choice is remembered.
                run {
                    val fmts = c.photoFormats()
                    val sub = "Pro photo mode · remembered next time"
                    listOf(PhotoFormat.JPEG to "Always JPEG", PhotoFormat.RAW_JPEG to "Always RAW + JPEG", PhotoFormat.RAW to "Always RAW only").forEach { (f, t) ->
                        val ok = f in fmts || f == PhotoFormat.JPEG
                        ToggleRow(t, if (ok) sub else "Not available with this lens / phone", s.photoFormat == f) { if (ok) c.setPhotoFormat(f) }
                    }
                }

                if (s.photoSizes.isNotEmpty()) {
                    Section("PHOTO SIZE · ${s.lens?.label ?: ""} CAMERA · ${s.aspect.label}")
                    s.photoSizes.forEach { ps ->
                        ChoiceRadio(
                            title = ps.label,
                            subtitle = ps.detail + if (ps.highRes) " · high-resolution mode, slower to take" else "",
                            selected = s.photoSize == ps,
                        ) { c.setPhotoSize(ps) }
                    }
                    if (s.sensorReport.isNotEmpty()) Text(
                        s.sensorReport, color = Pro.Dim, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                ChoiceRow("Burst", listOf(1, 3, 5, 10), s.burst, { if (it == 1) "OFF" else "x$it" }) { c.setBurst(it) }
                ChoiceRow("Self-timer", listOf(0, 3, 5, 10), s.timerSec, { if (it == 0) "OFF" else "${it}s" }) { c.setTimer(it) }
                ChoiceRow("Interval", listOf(0, 2, 5, 10, 30), s.intervalSec, { if (it == 0) "OFF" else "${it}s" }) { c.setInterval(it) }
                ChoiceRow("Bracket (PRO)", com.dronedirector.veyrocutpro.camera.Bracket.entries.toList(), s.bracket, { it.label }) { c.setBracket(it) }
                run {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val x = com.dronedirector.veyrocutpro.camera.CaptureExtras
                    ToggleRow("Auto-enhance", "Saves an extra, improved copy of each photo (exposure, contrast, white balance); the original is kept", x.autoEnhance) { x.autoEnhance = !x.autoEnhance; x.save(ctx) }
                    val askLocation = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
                        x.location = r.values.any { it }; x.save(ctx)
                        if (x.location) com.dronedirector.veyrocutpro.camera.CaptureLocation.start(ctx)
                        else android.widget.Toast.makeText(ctx, I18n.tr("Location not allowed — photos are saved without it"), android.widget.Toast.LENGTH_LONG).show()
                    }
                    ToggleRow("Save location", "Stores where each photo and video was taken (GPS) in the file", x.location) {
                        if (x.location) { x.location = false; x.save(ctx); com.dronedirector.veyrocutpro.camera.CaptureLocation.stop(ctx) }
                        else if (com.dronedirector.veyrocutpro.camera.CaptureLocation.allowed(ctx)) { x.location = true; x.save(ctx); com.dronedirector.veyrocutpro.camera.CaptureLocation.start(ctx) }
                        else askLocation.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                    ChoiceRow("Focus bracketing", listOf(0, 3, 4, 6), x.focusBracket, { if (it == 0) "OFF" else "$it SHOTS" }) { x.focusBracket = it; x.save(ctx) }
                    Text("Takes the same picture focused from close to far; the photo editor stacks them into one sharp photo (Merge → Focus stack). Use a tripod.", color = Pro.Dim, fontSize = 12.sp)
                    if (s.lenses.size > 1 || s.minFocusDiopters > 0f) ToggleRow("Macro", "Switches to the lens that focuses closest and focuses near — for flowers, insects, details", false) { c.macro(); onClose() }
                }
            }

            SettingsGroup("video", "Video", "Sound on / off, bitrate, 24-bit audio, NTSC, frame shape, flip, pre-record, stabilization, motion blur, codec") {
                Section("VIDEO")
                ToggleRow("Record audio", "Uses the microphone; levels show in the audio graph", s.audio) { c.toggleAudio() }
                run {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val x = com.dronedirector.veyrocutpro.camera.CaptureExtras
                    com.dronedirector.veyrocutpro.camera.DeviceClass.check(ctx)
                    val extremeOk = com.dronedirector.veyrocutpro.camera.DeviceClass.extremeBitrate(s.videoRes.highBitrate)
                    ChoiceRow("Bitrate", if (extremeOk) listOf(0, 1, 2, 3) else listOf(0, 1, 2), x.bitrateTier.coerceAtMost(if (extremeOk) 3 else 2), { listOf("ECONOMY", "STANDARD", "HIGH", "EXTREME")[it] }) { c.setBitrateTier(it) }
                    Text("Economy saves space, Standard is the phone's own choice, High keeps more detail" + (if (extremeOk) ", Extreme is for grading (big files)." else ". Extreme needs a more powerful phone (memory, processor and graphics)."), color = Pro.Dim, fontSize = 12.sp)
                    if (com.dronedirector.veyrocutpro.camera.DeviceClass.proAudio) ToggleRow(
                        "24-bit audio (WAV)",
                        "Pro Video and Video: the sound is recorded uncompressed and saved as a 24-bit WAV next to each clip (or in Music/AlcorCutPro); the clip gets the same sound. Headphone monitoring pauses while recording.",
                        x.wavAudio,
                    ) { c.setWavAudio(!x.wavAudio) }
                    else Text("24-bit audio (WAV): needs a more powerful phone (memory, processor and graphics).", color = Pro.Dim, fontSize = 12.sp)
                    ToggleRow(
                        "NTSC frame rates",
                        "24, 30 and 60 fps clips become 23.976, 29.97 and 59.94 fps (picture and sound slowed by 0.1 % after recording), for broadcast and US workflows",
                        x.ntsc,
                    ) { c.setNtsc(!x.ntsc) }
                    ToggleRow(
                        "Record in the frame guide's shape",
                        "Crops the recording to the chosen guide (2.76, 2.39, 1.85, 4:3, 1:1, 9:16) instead of only drawing it",
                        x.recordAspect,
                    ) { c.setRecordAspect(!x.recordAspect) }
                    ToggleRow(
                        "Flip picture",
                        "Mirrors left ↔ right in the viewfinder and the recording — for lens adapters, mirror rigs and teleprompters",
                        x.flipH,
                    ) { c.setFlip(!x.flipH) }
                }
                run {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val x = com.dronedirector.veyrocutpro.camera.CaptureExtras
                    ChoiceRow("Anti-flicker", listOf(0, 1, 2, 3), x.antiFlicker, { listOf("AUTO", "50 HZ", "60 HZ", "OFF")[it] }) { c.setAntiFlicker(it) }
                    Text("Stops banding under mains lights in photos and videos: 50 Hz in Europe, Africa, most of Asia; 60 Hz in the Americas.", color = Pro.Dim, fontSize = 12.sp)
                    ChoiceRow("Timecode", listOf(0, 1), x.timecode, { if (it == 0) "OFF" else "TIME OF DAY" }) { x.timecode = it; x.save(ctx) }
                    ToggleRow("Editing proxy", "Makes a small copy of each clip after recording, so the video editor plays it smoothly straight away", x.proxy) { x.proxy = !x.proxy; x.save(ctx) }
                    ToggleRow("Front + back camera", "Records the back camera with the front camera in a corner, in one video (phones that can run both together)", x.dualCam) { c.setDualCam(!x.dualCam) }
                    ChoiceRow("Pre-record", listOf(0, 3, 5, 10), x.preRecordSec, { if (it == 0) "OFF" else "${it}s" }) {
                        x.preRecordSec = it; x.save(ctx); c.refreshPreRecord()
                    }
                    Text("Keeps the last few seconds before you press record, so you never miss the moment. The camera keeps a temporary recording running while you wait (uses a little more battery).", color = Pro.Dim, fontSize = 12.sp)
                }
                if (s.stabilizationSupported || c.gyroAvailable) {
                    val modes = buildList { add(0); if (s.stabilizationSupported) add(1); if (c.gyroAvailable) add(2) }
                    ChoiceRow("Stabilization", modes, c.stabilizationMode, { listOf("OFF", "IN-APP (CROP)", "GYROFLOW")[it] }) { c.setStabilizationMode(it) }
                    Text(
                        when (c.stabilizationMode) {
                            1 -> "In-app: the camera steadies the picture while you film. Quick and ready to use; crops the edges a little."
                            2 -> "Gyroflow: the phone's motion sensors are recorded into a .gcsv file with the same name as each video (Documents/AlcorCutPro/Gyroflow, or next to the clip in a picked folder). Open both in Gyroflow (free, gyroflow.xyz) on a computer for gimbal-smooth results. The camera's own stabilization is off so the picture stays raw."
                            else -> "Choose In-app for steady video straight away, or Gyroflow for the smoothest result after filming."
                        },
                        color = Pro.Dim, fontSize = 12.sp,
                    )
                }
                run {
                    val x = com.dronedirector.veyrocutpro.camera.CaptureExtras
                    ChoiceRow("Motion blur (electronic ND)", listOf(0, 2, 4, 8), x.ndBlur, { if (it == 0) "OFF" else "×$it" }) { c.setNdBlur(it) }
                    Text("Blends each frame with the ones before it, like a slower shutter or an ND filter: smooth, cinematic movement even in bright sun. Use a fast shutter (or 180° in Pro Video) and keep the camera fairly steady. Not with HDR (HLG).", color = Pro.Dim, fontSize = 12.sp)
                }
                ChoiceRow(
                    "Codec (8-bit)",
                    if (s.hevcSupported) com.dronedirector.veyrocutpro.camera.VideoCodec.entries.toList()
                    else listOf(com.dronedirector.veyrocutpro.camera.VideoCodec.H264),
                    s.videoCodec, { it.label },
                ) { c.setVideoCodec(it) }
                Text(
                    (if (s.hevcSupported) "HEVC gives the same quality in about half the space. " else "This phone has no HEVC encoder, so H.264 is used. ") +
                        (if (s.hlgSupported) "HDR (HLG 10-bit) in Pro Video always records HEVC." else "This camera doesn't offer 10-bit HDR."),
                    color = Pro.Dim, fontSize = 12.sp,
                )
            }

            SettingsGroup("audio", "Audio", "Input gain, limiter, wind filter, voice noise reduction, microphones, headphone monitoring") {
                Section("AUDIO")
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Input gain", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text(
                        (if (s.audioGainDb > 0) "+" else "") + "${s.audioGainDb} dB",
                        style = Pro.Value.copy(color = if (s.audioGainDb != 0) Pro.Amber else Color.White),
                    )
                }
                Slider(
                    value = s.audioGainDb.toFloat(),
                    onValueChange = { c.setAudioGain(it.roundToInt()) },
                    valueRange = -12f..24f,
                    steps = 35,
                    colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber),
                )
                Text("Raise for quiet sources, lower for loud ones. The audio meter and headphones use the adjusted level; the gain is applied to the sound when each clip is saved.", color = Pro.Dim, fontSize = 12.sp)
                ToggleRow("Limiter", "Rounds off peaks so loud sounds don't distort", s.audioLimiter) { c.toggleAudioLimiter() }
                run {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val x = com.dronedirector.veyrocutpro.camera.CaptureExtras
                    ToggleRow("Wind filter", "Cuts the low rumble of wind and handling noise from recorded sound", x.windFilter) { x.windFilter = !x.windFilter; x.save(ctx) }
                    ChoiceRow("Voice noise reduction", listOf(0, 1, 2), x.noiseReduce, { listOf("OFF", "LIGHT", "STRONG")[it] }) { x.noiseReduce = it; x.save(ctx) }
                    Text("Cleans hiss, hum and background noise from the recorded sound so voices stand out. Applied when each clip is saved; Strong is for noisy places.", color = Pro.Dim, fontSize = 12.sp)
                    val inputs = androidx.compose.runtime.remember(s.headphones) { c.inputDevices() }
                    Text("Microphones found: " + (inputs.ifEmpty { listOf("phone microphone") }.joinToString(", ")), color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    Text("USB and wired microphones are used automatically when plugged in.", color = Pro.Dim, fontSize = 12.sp)
                    ToggleRow("Bluetooth microphone", "Record with a connected Bluetooth headset's mic (speech quality; works on most phones)", x.micBluetooth) { x.micBluetooth = !x.micBluetooth; x.save(ctx) }
                }
                ToggleRow(
                    "Headphone monitoring",
                    when (s.headphones) {
                        null -> "Hear the microphone live, with the gain applied. Connect headphones to use it."
                        "Bluetooth" -> "Bluetooth headphones connected · they add a delay, wired or USB-C is best for monitoring"
                        else -> "${s.headphones} headphones connected · hear the microphone live, with the gain applied"
                    },
                    s.headphoneMonitor,
                ) { c.toggleHeadphoneMonitor() }
                if (s.headphoneMonitor) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Headphone volume", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("${s.monitorVolume}%", style = Pro.Value)
                    }
                    Slider(
                        value = s.monitorVolume.toFloat(),
                        onValueChange = { c.setMonitorVolume(it.roundToInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber),
                    )
                }
            }

            SettingsGroup("view", "Viewfinder & monitoring", "Histogram, level, focus peaking, false colour, zebras, monitoring LUT, focus magnifier, safe areas, scopes, shutter angle, anamorphic") {
                Section("COMPOSITION")
                ToggleRow("Histogram", "Pro modes: live brightness graph with clipping warnings", s.showHistogram) { c.toggleHistogram() }
                ToggleRow("Level", "Horizon line, turns green when straight", s.showLevel) { c.toggleLevel() }
                ToggleRow("Focus peaking", "Pro modes: highlights in-focus edges in red", s.peaking) { c.togglePeaking() }
                ToggleRow("False colour", "Pro modes: exposure as colour bands (green = mid grey, pink = skin, red = clipped)", s.falseColor) { c.toggleFalseColor() }
                ToggleRow("Zebras", "Pro modes: stripes over areas brighter than ${s.zebraLevel}%", s.zebras) { c.toggleZebras() }
                if (s.zebras) {
                    Slider(
                        value = s.zebraLevel.toFloat(),
                        onValueChange = { c.setZebraLevel(it.roundToInt()) },
                        valueRange = 70f..100f,
                        steps = 5,
                        colors = SliderDefaults.colors(thumbColor = Pro.Amber, activeTrackColor = Pro.Amber),
                    )
                }

                Section("MONITORING & LENS")
                run {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val x = com.dronedirector.veyrocutpro.camera.CaptureExtras
                    val luts = com.dronedirector.veyrocutpro.editor.LutLibrary.luts
                    val importer = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) try {
                            val l = com.dronedirector.veyrocutpro.editor.LutLibrary.import(ctx, uri)
                            x.monitorLut = l.id; x.save(ctx); c.refreshMonitorLut()
                        } catch (t: Throwable) { android.widget.Toast.makeText(ctx, com.dronedirector.veyrocutpro.ui.I18n.tr(t.message ?: "Not a .cube LUT"), android.widget.Toast.LENGTH_SHORT).show() }
                    }
                    Text("Monitoring LUT", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.dronedirector.veyrocutpro.editor.PillChip("Off", x.monitorLut == null) { x.monitorLut = null; x.save(ctx); c.refreshMonitorLut() }
                        luts.forEach { l ->
                            com.dronedirector.veyrocutpro.editor.PillChip(l.name, x.monitorLut == l.id) { x.monitorLut = l.id; x.save(ctx); c.refreshMonitorLut() }
                        }
                        com.dronedirector.veyrocutpro.editor.PillChip("+ IMPORT .CUBE", false, accentText = true) { importer.launch(arrayOf("*/*")) }
                    }
                    Text("Shows your LUT on the viewfinder — e.g. shoot D-Log / Flat and see a normal Rec.709 picture. Same LUT library as the editors.", color = Pro.Dim, fontSize = 12.sp)
                    if (x.monitorLut != null) ToggleRow("Record the LUT", "Off: the LUT is only for monitoring and recordings stay log / flat. On: videos are recorded with the LUT applied.", x.bakeLut) {
                        x.bakeLut = !x.bakeLut; x.save(ctx); c.refreshMonitorLut()
                    }
                    ToggleRow("Focus magnifier", "Pro modes: a zoomed-in view while you turn the manual focus, to nail sharpness", x.focusMagnifier) { x.focusMagnifier = !x.focusMagnifier; x.save(ctx) }
                    ToggleRow("Safe areas", "Action-safe (90 %) and title-safe (80 %) frames, for broadcast and social crops", x.safeAreas) { x.safeAreas = !x.safeAreas; x.save(ctx) }
                    ChoiceRow("Scope", listOf(0, 1, 2, 3), x.scope, { listOf("HISTOGRAM", "WAVEFORM", "PARADE", "VECTOR")[it] }) { x.scope = it; x.save(ctx) }
                    Text("Pro modes: the brightness graph can be a histogram, a waveform (brightness across the picture), an RGB parade or a vectorscope (colour and skin-tone line).", color = Pro.Dim, fontSize = 12.sp)
                    ToggleRow("Shutter as angle", "Video: show the shutter as an angle (180° = natural motion blur) with a one-tap 180° button", x.shutterAngle) { x.shutterAngle = !x.shutterAngle; x.save(ctx) }
                    ChoiceRow("Anamorphic desqueeze", listOf(1f, 1.33f, 1.5f, 1.55f, 1.8f, 2f), x.desqueeze, { if (it == 1f) "OFF" else "${it}×" }) { x.desqueeze = it; x.save(ctx) }
                    Text("For anamorphic lenses / adapters: the viewfinder is stretched back to the real shape. Recordings stay squeezed — desqueeze them in the video editor (Edit → Anamorphic).", color = Pro.Dim, fontSize = 12.sp)
                }
            }

            SettingsGroup("ai", "AI", "AI upscale sizes, Cloud AI (fal.ai key, noise reduction, upscale)") {
                Section("AI UPSCALE")
                val upCtx = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.runtime.LaunchedEffect(Unit) { com.dronedirector.veyrocutpro.camera.UpscaleSettings.load(upCtx) }
                val up = com.dronedirector.veyrocutpro.camera.UpscaleSettings
                ToggleRow(
                    "AI upscale sizes",
                    "Adds AI 24 / 36 / 48 MP to the photo size choices. The built-in AI model (runs on the phone, no internet) rebuilds each photo at the bigger size in the background.",
                    up.enabled,
                ) { up.set(upCtx, on = !up.enabled); c.refreshSizes() }
                if (up.enabled) {
                    ChoiceRow("Quality", listOf(false, true), up.high, { if (it) "HIGH" else "STANDARD" }) { up.set(upCtx, hq = it) }
                    Text(
                        if (up.high) "High keeps more real detail but takes several minutes per photo." else "Standard takes roughly 1–2 minutes per photo on a recent phone.",
                        color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    ToggleRow("Keep the original", "Also keep the normal-size photo", up.keepOriginal) { up.set(upCtx, keep = !up.keepOriginal) }
                }

                CloudAiSettings(GuideVariant.CAPTURE)
            }

            SettingsGroup("controls", "Controls & buttons", "Side buttons, shutter sound, separate focus / exposure, Fn button, C1–C3 sync, simple layout, overlay positions") {
                Section("CONTROLS")
                run {
                    var side by remember { mutableStateOf(com.dronedirector.veyrocutpro.camera.SideKeys.get(sctx)) }
                    Text("Side buttons (volume up / down)", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    Text(when (side) { 0 -> "They just change the volume."; 2 -> "Volume up zooms in, volume down zooms out."; else -> "Take a photo, or start / stop recording — whichever mode is on." }, color = Pro.Dim, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        com.dronedirector.veyrocutpro.camera.SideKeys.NAMES.forEachIndexed { i, n ->
                            val on = side == i
                            Text(n, color = if (on) Color.Black else Color.White, fontSize = 12.sp,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Pro.Amber else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f))
                                    .clickable { side = i; com.dronedirector.veyrocutpro.camera.SideKeys.set(sctx, i) }.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.06f))
                        .clickable { com.dronedirector.veyrocutpro.camera.CaptureKeys.show = true }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Keyboard shortcuts", color = Color.White, fontSize = 15.sp)
                        Text("With a Bluetooth or USB keyboard: shutter, zoom, exposure, modes and more — choose your own keys", color = Pro.Dim, fontSize = 12.sp)
                    }
                    Text("›", color = Pro.Amber, fontSize = 20.sp)
                }
                SettingsGroup("cap_hw", "Audio hardware", "Which microphone and speaker / headphones the app uses") { AudioHardwareSettings() }
                SettingsGroup("cap_sysinfo", "System Info", "This phone's hardware and drivers") { SystemInfoSection() }
                ToggleRow(
                    "Shutter sound",
                    if (s.shutterSoundRequired) "Required by law in this region and can't be turned off"
                    else "Click when taking photos, beeps when recording starts/stops and focus locks",
                    s.cameraSounds || s.shutterSoundRequired,
                ) { c.toggleCameraSounds() }
                if ((s.cameraSounds || s.shutterSoundRequired) && !s.shutterSoundRequired) {
                    var style by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.dronedirector.veyrocutpro.camera.ShutterSounds.style(sctx)) }
                    Text("Shutter sound style — tap to hear it", style = Pro.Label.copy(color = Pro.Dim, fontSize = 11.sp), modifier = Modifier.padding(top = 4.dp))
                    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                        com.dronedirector.veyrocutpro.camera.ShutterSounds.NAMES.forEachIndexed { i, n ->
                            Text(n, color = if (style == i) Color.Black else Color.White, fontSize = 12.sp,
                                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(if (style == i) Pro.Amber else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f))
                                    .clickable {
                                        style = i; com.dronedirector.veyrocutpro.camera.ShutterSounds.setStyle(sctx, i)
                                        if (i > 0) com.dronedirector.veyrocutpro.camera.ShutterSounds.play(i) else android.media.MediaActionSound().play(android.media.MediaActionSound.SHUTTER_CLICK)
                                    }.padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }
                }
                run {
                    val x = com.dronedirector.veyrocutpro.camera.CaptureExtras
                        ToggleRow(
                            "Separate focus and exposure",
                            "Tap to focus, double-tap to set where the exposure is measured (the amber sun ring)",
                            x.splitAfAe,
                        ) { c.setSplitAfAe(!x.splitAfAe) }
                        ChoiceRow("Fn button", com.dronedirector.veyrocutpro.camera.FnAction.entries.toList(), com.dronedirector.veyrocutpro.camera.FnAction.of(x.fnAction), { it.label }) { c.setFnAction(it) }
                        Text("The Fn button sits at the end of the Pro read-out bar: tap it to run this, hold it to choose another function.", color = Pro.Dim, fontSize = 12.sp)
                        PresetSyncRow(c)
                }
                ToggleRow(
                    "Simple layout",
                    "Only the everyday modes and controls (Photo, Video, Portrait, Pano, Long exposure, Time-lapse, Slow-mo). Turn off for every Pro tool.",
                    com.dronedirector.veyrocutpro.camera.SimpleMode.on,
                ) { c.setSimple(!com.dronedirector.veyrocutpro.camera.SimpleMode.on) }
                Section("LAYOUT")
                Text(
                    "Drag the histogram, audio graph and exposure read-out anywhere on the viewfinder. Positions are remembered for portrait and landscape.",
                    color = Pro.Dim, fontSize = 12.sp,
                )
                val ctx = androidx.compose.ui.platform.LocalContext.current
                androidx.compose.material3.TextButton(onClick = {
                    OverlayLayout.reset(ctx)
                    c.bumpLayoutVersion()
                }) { Text("Reset overlay positions", color = Pro.Amber) }
            }

            SettingsGroup("privacy", "Privacy, title & copyright", "Face blur, title on videos, your name and copyright in photos") {
                Section("PRIVACY & TITLE")
                run {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val x = com.dronedirector.veyrocutpro.camera.CaptureExtras
                    ToggleRow("Face blur", "Pixelates faces live in the viewfinder, in videos and in photos (found on the phone; nothing is uploaded)", x.faceBlur) { c.setFaceBlur(!x.faceBlur) }
                    ToggleRow("Title on videos", "Burns your title into each video when it's saved", x.titleOn) { x.titleOn = !x.titleOn; x.save(ctx) }
                    if (x.titleOn) {
                        androidx.compose.material3.OutlinedTextField(
                            value = x.titleText,
                            onValueChange = { v -> x.titleText = v.take(80); x.save(ctx) },
                            label = { Text("Title text") },
                            singleLine = false, maxLines = 2,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                        ChoiceRow("Position", listOf(0, 1, 2), x.titlePos, { listOf("LOWER THIRD", "CENTRE", "TOP")[it] }) { x.titlePos = it; x.save(ctx) }
                        ChoiceRow("Show for", listOf(3, 5, 10, 0), x.titleSec, { if (it == 0) "WHOLE CLIP" else "${it}s" }) { x.titleSec = it; x.save(ctx) }
                        Text("The title fades in at the start. Adding it takes a short moment after you stop recording.", color = Pro.Dim, fontSize = 12.sp)
                    }
                }

                Section("COPYRIGHT")
                run {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val x = com.dronedirector.veyrocutpro.camera.CaptureExtras
                    androidx.compose.material3.OutlinedTextField(
                        value = x.creator, onValueChange = { v -> x.creator = v.take(80); x.save(ctx) },
                        label = { Text("Your name (creator)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = x.copyright, onValueChange = { v -> x.copyright = v.take(120); x.save(ctx) },
                        label = { Text("Copyright, e.g. © 2026 Your Name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    )
                    Text("Written into every photo you take and every photo you export from the photo editor (Artist and Copyright in the photo's details). Leave empty to skip.", color = Pro.Dim, fontSize = 12.sp)
                }
            }

            SettingsGroup("workflow", "Film-set workflow", "Slate & shot log, clip naming (camera / reel / clip)") {
                Section("SLATE & SHOT LOG")
                run {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val x = com.dronedirector.veyrocutpro.camera.CaptureExtras
                    var logCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(com.dronedirector.veyrocutpro.camera.Slate.count(ctx)) }
                    val exportLog = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { u ->
                        if (u != null) c.toast(if (com.dronedirector.veyrocutpro.camera.Slate.export(ctx, u)) "Shot log saved" else "Couldn't save the shot log")
                    }
                    ToggleRow("Slate", "Scene / shot / take on the viewfinder, in photos and in a shot log for every clip", x.slateOn) { x.slateOn = !x.slateOn; x.save(ctx) }
                    if (x.slateOn) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.OutlinedTextField(value = x.slateScene, onValueChange = { v -> x.slateScene = v.take(12); x.save(ctx) }, label = { Text("Scene") }, singleLine = true, modifier = Modifier.weight(1f))
                            androidx.compose.material3.OutlinedTextField(value = x.slateShot, onValueChange = { v -> x.slateShot = v.take(12); x.save(ctx) }, label = { Text("Shot") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("Take ${x.slateTake}", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Stepper("TK ${x.slateTake}", onMinus = { x.slateTake = (x.slateTake - 1).coerceAtLeast(1); x.save(ctx) }, onPlus = { x.slateTake = (x.slateTake + 1).coerceAtMost(999); x.save(ctx) })
                        }
                        ToggleRow("Next take automatically", "The take number goes up after each recording", x.slateAutoTake) { x.slateAutoTake = !x.slateAutoTake; x.save(ctx) }
                        androidx.compose.material3.OutlinedTextField(value = x.slateNotes, onValueChange = { v -> x.slateNotes = v.take(200); x.save(ctx) }, label = { Text("Notes (e.g. good take, focus soft)") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.TextButton(onClick = { exportLog.launch("AlcorCutPro_shot_log.csv") }) { Text("Export shot log (.csv) · $logCount", color = Pro.Amber) }
                            androidx.compose.material3.TextButton(onClick = { com.dronedirector.veyrocutpro.camera.Slate.clear(ctx); logCount = 0 }) { Text("Clear log", color = Pro.Red) }
                        }
                        Text("The log opens in Excel, Google Sheets or Numbers: date, file, scene, shot, take, notes and camera settings for each clip and photo.", color = Pro.Dim, fontSize = 12.sp)
                    }
                }

                Section("CLIP NAMING")
                com.dronedirector.veyrocutpro.camera.ClipNaming.entries.forEach { n ->
                    ChoiceRadio(
                        title = n.label,
                        subtitle = if (n == com.dronedirector.veyrocutpro.camera.ClipNaming.CINEMA)
                            "Film-set style, e.g. ${nextClipName(s)}" else "e.g. PRO_20261015_143005_120.mp4",
                        selected = s.clipNaming == n,
                    ) { c.setClipNaming(n) }
                }
                if (s.clipNaming == com.dronedirector.veyrocutpro.camera.ClipNaming.CINEMA) {
                    ChoiceRow("Camera", listOf('A', 'B', 'C', 'D', 'E'), s.cameraLetter, { it.toString() }) { c.setCameraLetter(it) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Reel", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Stepper("%03d".format(s.reel), onMinus = { c.setReel(s.reel - 1) }, onPlus = { c.setReel(s.reel + 1) })
                    }
                    Text("Next clip: ${nextClipName(s)}.mp4 · starting a new reel restarts clips at C001.", color = Pro.Dim, fontSize = 12.sp)
                }
            }

            SettingsGroup("remote", "Remote & output", "Remote monitor & control from another device, cast to a TV (Chromecast), clean feed for HDMI / streaming") {
                Section("REMOTE MONITOR")
                ToggleRow(
                    "Remote monitor & control",
                    "Watch the live picture and work the camera from a second phone, tablet or computer on the same Wi-Fi — in any browser, no app needed",
                    c.remoteAddress != null,
                ) { c.setRemote(c.remoteAddress == null) }
                c.remoteAddress?.let { addr ->
                    Text("Open this address on the other device:", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(addr, color = Pro.Amber, fontSize = 18.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(vertical = 6.dp))
                    }
                    Text("The 4-digit number at the end is the PIN — only devices with the full address can connect. No Wi-Fi? Turn on this phone's hotspot and join it from the other device. Turn this off when you're done.", color = Pro.Dim, fontSize = 12.sp)
                }

                // Chromecast: tablets only.
                if (androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >= 600) {
                    Section("CAST TO TV")
                    CastPlaceSetting("cap")
                }

                Section("OUTPUT")
                Text(
                    "Clean feed hides every control so only the picture is on screen. Use it with a USB-C to HDMI adapter " +
                        "(on phones that support video out), screen mirroring or a capture card for a live, clean camera feed. " +
                        "Double-tap the screen to bring the controls back.",
                    color = Pro.Dim, fontSize = 12.sp,
                )
                androidx.compose.material3.TextButton(onClick = {
                    c.setCleanFeed(true)
                    onClose()
                }) { Text("Start clean feed", color = Pro.Amber) }
            }

            SettingsGroup("cap_look", "Appearance", "Dark, light or like the phone, one-handed layout, haptic feedback — the same choice as in the editors") {
                val actx = androidx.compose.ui.platform.LocalContext.current
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    listOf("Dark", "Light", "Like the phone").forEachIndexed { i, n ->
                        val on = AppTheme.pref == i
                        Text(n, color = if (on) Color.Black else Color.White, fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Pro.Amber else Pro.Fg.copy(alpha = 0.08f))
                                .clickable { AppTheme.set(actx, i) }.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                Text("Light makes the menus and panels light with dark text; the controls on the camera picture stay dark so they're easy to read.", color = Pro.Dim, fontSize = 12.sp)
                SettingsKeywords("One-handed", "Haptic feedback", "thumb", "vibration")
                ComfortSettings()
            }

            SettingsGroup("storage", "Storage & backup", "Where photos and videos are saved, backup and restore") {
                Section("SAVE PHOTOS & VIDEOS TO")
                StorageChooser(com.dronedirector.veyrocutpro.camera.StorageArea.CAPTURE) { c.setStorage(it) }
                BackupSection()
            }

            SettingsGroup("app", "App", "App colour, language, mouse pointer, startup screen, guide, what's new, tour, licences, privacy") {
                Section("APP COLOUR")
                AccentPicker()
                LanguagePicker()
                PointerAndWheelSettings(showWheel = false)
                Section("STARTUP")
                var alwaysCapture by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(StartScreen.get(sctx) == AppScreen.CAPTURE) }
                ToggleRow("Always start in Capture", "Skip the start screen and open the camera straight away. Turn off to show the start screen again.", alwaysCapture) {
                    alwaysCapture = !alwaysCapture
                    StartScreen.set(sctx, if (alwaysCapture) AppScreen.CAPTURE else AppScreen.HOME)
                }
                GuideButton(GuideVariant.CAPTURE)
                WhatsNewButton(NewsArea.CAPTURE, onClose)
                TourButton()
                LicensesButton()
                PrivacyButton()
            }

            SettingsGroup("lens", "This lens", "What this camera can do: manual controls, RAW, video sizes, HDR, scenes") {
                Section("THIS LENS")
                val lens = s.lens
                InfoRow("Camera", "${lens?.label ?: "-"}  (id ${lens?.id ?: "-"})")
                InfoRow("Manual ISO / shutter", if (s.manualSensor) "Yes" else "No")
                InfoRow("Manual focus", if (s.minFocusDiopters > 0f) "Yes" else "Fixed focus")
                InfoRow("RAW (DNG)", if (s.rawSupported) "Yes" else "No")
                InfoRow("Video", s.videoResolutions.joinToString(" · ") { it.label } + "  ·  " + s.fpsOptions.joinToString("/") + " fps")
                InfoRow("HLG 10-bit", if (s.hlgSupported) "Yes" else "No")
                InfoRow("Scenes", s.scenes.joinToString(" · ") { it.label })
            }

            Spacer(Modifier.padding(4.dp))
            Text("Photos and videos save to DCIM/AlcorCutPro.", style = Pro.Label.copy(fontSize = 11.sp, letterSpacing = 0.sp))
            }
        }
    }
}

/** C1–C3 sync between devices through one file in a cloud folder the user picks (Google Drive, Dropbox…). */
@Composable
private fun PresetSyncRow(c: com.dronedirector.veyrocutpro.camera.ProCameraController) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val x = com.dronedirector.veyrocutpro.camera.CaptureExtras
    fun use(u: android.net.Uri?, push: Boolean) {
        if (u == null) return
        try { ctx.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Throwable) {}
        x.presetSyncUri = u.toString(); x.save(ctx)
        Thread {
            val ok = if (push) com.dronedirector.veyrocutpro.camera.CamPresets.syncPush(ctx) else com.dronedirector.veyrocutpro.camera.CamPresets.syncPull(ctx) || true
            c.toast(if (ok) "Set-up sync is on" else "Couldn't write the sync file")
        }.start()
    }
    val create = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { use(it, true) }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { use(it, false) }
    Text("Sync C1–C3 between devices", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
    Text(
        if (x.presetSyncUri != null) "On: your set-ups are saved to the sync file whenever you save one, and newer ones from your other devices are taken when the camera opens."
        else "Save the sync file in a cloud folder (e.g. Google Drive) on this device, then pick the same file on your other devices.",
        color = Pro.Dim, fontSize = 12.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text("CREATE SYNC FILE", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { try { create.launch("AlcorCut-camera-setups.json") } catch (_: Throwable) {} }.padding(8.dp))
        Text("USE EXISTING", style = Pro.Label.copy(color = Pro.Amber), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { try { open.launch(arrayOf("application/json", "application/octet-stream", "*/*")) } catch (_: Throwable) {} }.padding(8.dp))
        if (x.presetSyncUri != null) Text("STOP", style = Pro.Label.copy(color = Pro.Red), modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { x.presetSyncUri = null; x.save(ctx) }.padding(8.dp))
    }
}

@Composable
private fun Section(title: String) {
    SettingsKeywords(title)
    Text(title, style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    SettingsKeywords(title, subtitle)
    val tick = rememberTick()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { tick(); onToggle() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(subtitle, color = Pro.Dim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = { tick(); onToggle() },
            colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black),
        )
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SettingsKeywords(title, *options.map(label).toTypedArray())
    val chips = @Composable { mod: Modifier ->
        Row(mod, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                val sel = o == selected
                Text(
                    label(o),
                    style = Pro.Value.copy(fontSize = 12.sp, color = if (sel) Color.Black else Color.White),
                    maxLines = 1, softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (sel) Pro.Amber else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.1f))
                        .clickable { onSelect(o) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
    // Many / long choices: title on its own line, choices underneath (scroll sideways if needed),
    // so the title is never squeezed into a narrow column.
    val stacked = options.size > 4 || options.sumOf { label(it).length } + title.length > 26
    if (stacked) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            chips(Modifier.padding(top = 6.dp).hintedScroll(horizontal = true))
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            chips(Modifier)
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(title, color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun ChoiceRadio(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    SettingsKeywords(title, subtitle)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onClick,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = Pro.Amber),
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(subtitle, color = Pro.Dim, fontSize = 12.sp)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AccentPicker() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Text("Changes the yellow highlights across the app.", color = Pro.Dim, fontSize = 12.sp)
    Spacer(Modifier.size(10.dp))
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Accent.entries.forEach { a ->
            val selected = Pro.accent == a
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(a.color)
                        .then(
                            if (selected) Modifier.border(3.dp, Color.White, androidx.compose.foundation.shape.CircleShape)
                            else Modifier
                        )
                        .clickable { AccentStore.save(ctx, a) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                }
                Text(a.label, color = if (selected) Color.White else Pro.Dim, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    Spacer(Modifier.size(8.dp))
}

private fun nextClipName(s: CameraUiState): String {
    val date = java.text.SimpleDateFormat("yyMMdd", java.util.Locale.US).format(java.util.Date())
    return "%c%03dC%03d_%s_ACLX".format(java.util.Locale.US, s.cameraLetter, s.reel, s.clipNumber, date)
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("−" to onMinus, "" to {}, "+" to onPlus).forEach { (label, action) ->
            if (label.isEmpty()) {
                Text(value, style = Pro.Value.copy(fontSize = 15.sp), modifier = Modifier.padding(horizontal = 6.dp))
            } else {
                Text(
                    label,
                    style = Pro.Value.copy(fontSize = 16.sp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.1f))
                        .clickable(onClick = action)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** A folded group of settings: one line with what's inside; tap to open or close. */
@Composable
internal fun SettingsGroup(key: String, title: String, summary: String, content: @Composable () -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(key) { mutableStateOf(false) }
    val q = LocalSettingsQuery.current
    if (q.isNotBlank()) {
        // Searching: groups whose name, summary or rows match open by themselves; the rest are hidden.
        val hit = androidx.compose.runtime.remember(q) { mutableStateOf(false) }
        val show = settingsMatch(q, title, summary) || hit.value
        if (show) Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.06f)).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(title, color = Pro.Amber, fontSize = 15.sp)
            Text(summary, color = Pro.Dim, fontSize = 12.sp, maxLines = 2)
        }
        Column(
            if (show) Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp, bottom = 6.dp) else Modifier.fillMaxWidth().height(0.dp).clipToBounds(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { androidx.compose.runtime.CompositionLocalProvider(LocalSettingsHit provides hit) { content() } }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.06f))
            .clickable { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, color = if (open) Pro.Amber else Color.White, fontSize = 15.sp)
            Text(summary, color = Pro.Dim, fontSize = 12.sp, maxLines = if (open) 4 else 2)
        }
        Icon(if (open) androidx.compose.material.icons.Icons.Filled.ExpandLess else androidx.compose.material.icons.Icons.Filled.ExpandMore, contentDescription = null, tint = Pro.Dim)
    }
    if (open) androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}
