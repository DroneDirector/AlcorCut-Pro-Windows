package com.dronedirector.veyrocutpro.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaActionSound
import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Capture on Windows: the same controls as on the phone, driving a camera on the PC (webcam, capture
 * card, camera in webcam mode) through the app's own Media Foundation library. Own code.
 *
 * What the phone's camera does and a PC camera can't (flash, RAW, scene modes, a gyroscope) is left
 * out of the choices the screen offers, the way the phone does for lenses without them.
 */
class ProCameraController(private val context: Context) {

    private val prefs get() = context.getSharedPreferences("procam", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        CameraUiState(
            cameraSounds = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getBoolean("camera_sounds", true),
            videoCodec = prefEnum(context, "video_codec", VideoCodec.H264),
            hevcSupported = true,
            audioGainDb = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getInt("audio_gain", 0),
            audioLimiter = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getBoolean("audio_limiter", true),
            headphoneMonitor = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getBoolean("hp_monitor", false),
            monitorVolume = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getInt("hp_volume", 80),
            clipNaming = prefEnum(context, "clip_naming", ClipNaming.STANDARD),
            mode = lastModeAtStart(context),
            cameraLetter = (context.getSharedPreferences("procam", Context.MODE_PRIVATE).getString("camera_letter", "A") ?: "A").first(),
            reel = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getInt("reel", 1),
            clipNumber = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getInt("clip", 1),
            headphones = "PC",
            // No motion sensor on a PC: no spirit level.
            showLevel = false,
            flatSupported = true,
            filtersAvailable = true,
        )
    )
    val state: StateFlow<CameraUiState> = _state.asStateFlow()
    private val _analysis = MutableStateFlow<AnalysisResult?>(null)
    val analysis: StateFlow<AnalysisResult?> = _analysis.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "capture-save").apply { isDaemon = true } }
    private val analysisExec = Executors.newSingleThreadExecutor { r -> Thread(r, "capture-analysis").apply { isDaemon = true } }
    private val analysisBusy = AtomicBoolean(false)
    private val sound = MediaActionSound()

    /** PCs have no gyroscope, so Gyroflow logging isn't offered. */
    val gyroAvailable: Boolean get() = false
    val audioMeter = AudioMeter(context)
    val live = LiveStreamer(context)
    var forceScreenAnalysis = false

    init {
        val p = prefs
        audioMeter.gainDb = p.getInt("audio_gain", 0)
        audioMeter.limiter = p.getBoolean("audio_limiter", true)
        audioMeter.monitorVolume = p.getInt("hp_volume", 80) / 100f
        audioMeter.headphones = _state.value.headphoneMonitor
        live.frameSource = { latestFrame() }
    }

    private val st: CameraUiState get() = _state.value

    @Volatile private var analysisConfig = AnalysisConfig()
    private val analyzer = FrameAnalyzer({ analysisConfig }) { r -> _analysis.value = r }

    private fun set(f: (CameraUiState) -> CameraUiState) {
        _state.update(f)
        val s = _state.value
        analysisConfig = AnalysisConfig(histogram = s.proHistogram, zebras = s.proZebras, zebraLevel = s.zebraLevel, peaking = s.proPeaking, falseColor = s.proFalseColor)
    }

    fun toast(message: String) = set { it.copy(message = message, messageId = it.messageId + 1) }

    fun refreshLastMedia() {
        scope.launch {
            val latest = withContext(Dispatchers.IO) { MediaLibrary.query(context).firstOrNull()?.uri }
            set { it.copy(lastMedia = latest) }
        }
    }

    // ---------------------------------------------------------------- headphones / monitoring

    private fun updateMonitorOutput() { audioMeter.headphones = st.headphoneMonitor }
    fun toggleHeadphoneMonitor() {
        val on = !st.headphoneMonitor
        prefs.edit().putBoolean("hp_monitor", on).apply()
        set { it.copy(headphoneMonitor = on) }
        updateMonitorOutput()
        if (on) toast("Use headphones to listen — speakers would feed back into the microphone")
    }
    fun setMonitorVolume(v: Int) {
        val x = v.coerceIn(0, 100)
        prefs.edit().putInt("hp_volume", x).apply()
        audioMeter.monitorVolume = x / 100f
        set { it.copy(monitorVolume = x) }
    }
    val monitorWhileRecording: Boolean get() = st.headphoneMonitor

    /** Sound devices on this PC that can record (for the settings read-out). */
    fun inputDevices(): List<String> = android.media.AudioManager.list(true).map { it.mixerName }

    // ---------------------------------------------------------------- the camera

    private var previewView: PreviewView? = null
    private var cam: PcCamera? = null
    private var devices: List<PcDevice> = emptyList()
    private var phones: List<PhoneDevice> = emptyList()
    private var tethers: List<TetherDevice> = emptyList()
    /** The tethered pro camera in use, or null. */
    @Volatile private var tether: TetherCamera? = null
    private var tetherJob: kotlinx.coroutines.Job? = null
    private var tIso: TetherCamera.Prop? = null
    private var tShutter: TetherCamera.Prop? = null
    private var tWb: TetherCamera.Prop? = null
    private var tEv: TetherCamera.Prop? = null
    private var watchJob: kotlinx.coroutines.Job? = null
    /** The phone in use as the camera (Connect to PC), or null for a PC camera. */
    @Volatile private var phone: PhoneLink? = null
    private val viaPhone: Boolean get() = phone != null
    /** A phone waiting for its PIN (the PIN dialog shows while this is set). */
    var pinRequest by androidx.compose.runtime.mutableStateOf<PhoneDevice?>(null); private set
    var pinError by androidx.compose.runtime.mutableStateOf<String?>(null); private set

    /** With the phone as the camera, a control goes to the phone. */
    private fun fwd(cmd: String, value: Any? = null): Boolean { val p = phone ?: return false; p.command(cmd, value); return true }

    private fun updateLenses() {
        val pcs = devices.map { Lens(it.link, androidx.camera.core.CameraSelector.LENS_FACING_FRONT, it.name, androidx.camera.core.CameraInfo()) }
        val ph = phones.map { Lens(it.link, androidx.camera.core.CameraSelector.LENS_FACING_BACK, "Phone · " + it.name, androidx.camera.core.CameraInfo()) }
        val tc = tethers.map { Lens(it.link, androidx.camera.core.CameraSelector.LENS_FACING_BACK, "Camera · " + it.name, androidx.camera.core.CameraInfo()) }
        set { it.copy(lenses = pcs + ph + tc) }
    }

    /** The PIN typed on the PC for [d] (shown on the phone). */
    fun connectPhone(d: PhoneDevice, pin: String) {
        pinError = null
        scope.launch {
            val link = PhoneLink(d, ::onFrame, { m -> scope.launch { mirror(m) } }, { f -> scope.launch { onPhoneFile(f) } }) { why ->
                scope.launch {
                    if (phone != null) { phone = null; android.hardware.Motion.phoneLinked = false; android.hardware.Motion.phoneRoll = null; set { it.copy(ready = false, recording = false) }; toast(why ?: "The phone disconnected") }
                }
            }
            val err = withContext(Dispatchers.IO) { link.connect(pin.trim()) }
            if (err != null) { pinError = err; return@launch }
            prefs.edit().putString("phone_pin_${d.name}", pin.trim()).apply()
            pinRequest = null
            withContext(Dispatchers.IO) { cam?.close() }; cam = null
            previewView?.clear()
            phone = link
            android.hardware.Motion.phoneLinked = true
            refreshPipes()
            set { it.copy(ready = true) }
            toast("Connected to ${d.name} — photos and videos are taken on the phone and copied here")
        }
    }
    fun cancelPin() { pinRequest = null; pinError = null }
    fun lastPin(d: PhoneDevice): String = prefs.getString("phone_pin_${d.name}", "") ?: ""

    private fun closeTether() {
        tetherJob?.cancel(); tetherJob = null
        val t = tether; tether = null
        if (t != null) io.execute { t.close() }
    }

    /** A pro camera on USB: its settings, live view (Nikon, Canon EOS) and every new photo copied here. */
    private suspend fun openTether(d: TetherDevice) {
        withContext(Dispatchers.IO) { cam?.close() }; cam = null
        val t = withContext(Dispatchers.IO) { TetherCamera(d).takeIf { it.open() } }
        if (t == null) { toast("Couldn't open ${d.name}: " + com.dronedirector.veyrocutpro.windows.MfNative.error().ifBlank { "set the camera's USB mode to PC / PTP and close other camera apps" }); return }
        tether = t
        previewView?.clear()
        withContext(Dispatchers.IO) { tIso = t.prop(TetherCamera.ISO); tShutter = t.prop(TetherCamera.SHUTTER); tWb = t.prop(TetherCamera.WB); tEv = t.prop(TetherCamera.EV) }
        set { s ->
            s.copy(
                ready = true, manualSensor = tIso != null || tShutter != null,
                isoStops = tIso?.allowed?.map { it.toInt() }?.filter { it in 25..409600 }.orEmpty(),
                shutterStops = tShutter?.allowed?.map { it * 100_000L }?.filter { it > 0 }.orEmpty(),
                iso = tIso?.value?.toInt() ?: s.iso, shutterNs = tShutter?.value?.let { it * 100_000L } ?: s.shutterNs,
                manualIso = tIso != null, manualShutter = tShutter != null,
                evMin = if (tEv != null) -15 else 0, evMax = if (tEv != null) 15 else 0, evStep = 1f / 3f,
                minFocusDiopters = 0f, hasFlash = false, rawSupported = false, minZoom = 1f, maxZoom = 1f, zoom = 1f,
                sensorReport = t.model + (if (t.hasLiveView) " · live view" else ""),
            )
        }
        toast("${t.model} connected — new photos are copied to this PC" + if (!t.hasLiveView) " (this camera gives no live view over USB)" else "")
        tetherJob = scope.launch(Dispatchers.IO) {
            var lastFetch = 0L
            val dirFor = { name: String -> Storage.newFile(context, StorageArea.CAPTURE, if (name.substringAfterLast('.').lowercase() in setOf("mov", "mp4")) Storage.Kind.VIDEO else Storage.Kind.IMAGE, name) }
            while (tether === t) {
                val now = System.currentTimeMillis()
                if (now - lastFetch > 1500) {
                    lastFetch = now
                    val files = try { t.fetchNew(dirFor) } catch (_: Throwable) { emptyList() }
                    for (f in files) withContext(Dispatchers.Main) { set { it.copy(lastMedia = android.net.Uri.fromFile(f)) }; toast("Copied from the camera: ${f.name}") }
                    if (!t.hasLiveView) files.lastOrNull { it.extension.lowercase() in setOf("jpg", "jpeg") }?.let { f -> showStill(f) }
                }
                if (t.hasLiveView) {
                    val jpg = try { t.liveFrame() } catch (_: Throwable) { null }
                    val img = jpg?.let { try { javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(it)) } catch (_: Throwable) { null } }
                    if (img != null) {
                        val px = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
                        onFrame(px, img.width, img.height, System.nanoTime() / 100)
                    } else delay(200)
                    delay(40)
                } else delay(300)
            }
        }
    }

    private fun showStill(f: java.io.File) {
        val img = try { javax.imageio.ImageIO.read(f) } catch (_: Throwable) { null } ?: return
        val k = minOf(1f, 1920f / maxOf(img.width, img.height))
        val w = (img.width * k).toInt(); val h = (img.height * k).toInt()
        val sc = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
        sc.createGraphics().apply { setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR); drawImage(img, 0, 0, w, h, null); dispose() }
        onFrame(sc.getRGB(0, 0, w, h, null, 0, w), w, h, 0)
    }

    /** Sends a setting to the tethered camera (nearest value it allows). */
    private fun tset(p: TetherCamera.Prop?, want: Long): Boolean {
        val t = tether ?: return false
        if (p == null) { toast("Change this on the camera itself"); return true }
        val v = if (p.allowed.isEmpty()) want else p.allowed.minByOrNull { kotlin.math.abs(it - want) } ?: want
        io.execute { if (!t.set(p, v)) toast("The camera didn't take the setting") }
        return true
    }

    /** The phone's camera settings, shown on the PC. */
    private fun mirror(m: Map<String, String>) {
        android.hardware.Motion.phoneRoll = m["roll"]?.toFloatOrNull()
        fun b(k: String) = m[k] == "true"
        fun i(k: String) = m[k]?.toDoubleOrNull()?.toInt()
        fun l(k: String) = m[k]?.toDoubleOrNull()?.toLong()
        fun f(k: String) = m[k]?.toFloatOrNull()
        fun ints(k: String) = m[k]?.trim('[', ']')?.split(',')?.mapNotNull { it.trim().toDoubleOrNull()?.toInt() }.orEmpty()
        fun longs(k: String) = m[k]?.trim('[', ']')?.split(',')?.mapNotNull { it.trim().toDoubleOrNull()?.toLong() }.orEmpty()
        fun strs(k: String) = m[k]?.trim('[', ']')?.split(',')?.map { it.trim().trim('"') }?.filter { it.isNotEmpty() }.orEmpty()
        set { s ->
            s.copy(
                mode = CaptureMode.entries.firstOrNull { it.name == m["mode"] } ?: s.mode,
                ready = b("ready"), recording = b("recording"), paused = b("paused"), recordSeconds = l("seconds") ?: 0,
                manualSensor = b("manualSensor"), manualIso = b("manualIso"), manualShutter = b("manualShutter"),
                iso = i("iso") ?: s.iso, shutterNs = l("shutterNs") ?: s.shutterNs,
                liveIso = i("liveIso")?.takeIf { it > 0 }, liveShutterNs = l("liveShutterNs")?.takeIf { it > 0 },
                isoStops = ints("isoStops"), shutterStops = longs("shutterStops"),
                manualFocus = b("manualFocus"), focusDiopters = f("focus") ?: 0f, minFocusDiopters = f("minFocus") ?: 0f,
                whiteBalance = WhiteBalance.entries.firstOrNull { it.name == m["wb"] } ?: s.whiteBalance,
                ev = i("ev") ?: 0, evMin = i("evMin") ?: 0, evMax = i("evMax") ?: 0, evStep = f("evStep") ?: s.evStep,
                aeLock = b("aeLock"), awbLock = b("awbLock"), zoom = f("zoom") ?: 1f, minZoom = f("minZoom") ?: 1f, maxZoom = f("maxZoom") ?: 1f,
                flash = FlashSetting.entries.firstOrNull { it.name == m["flash"] } ?: FlashSetting.OFF, hasFlash = b("hasFlash"),
                rawSupported = b("raw"), photoFormat = PhotoFormat.entries.firstOrNull { it.name == m["format"] } ?: PhotoFormat.JPEG,
                videoRes = VideoRes.entries.firstOrNull { it.name == m["videoRes"] } ?: s.videoRes,
                videoResolutions = strs("resolutions").mapNotNull { n -> VideoRes.entries.firstOrNull { it.name == n } }.ifEmpty { s.videoResolutions },
                fps = i("fps") ?: s.fps, fpsOptions = ints("fpsOptions").ifEmpty { s.fpsOptions },
                profile = VideoProfile.entries.firstOrNull { it.name == m["profile"] } ?: s.profile, hlgSupported = b("hlg"),
                videoCodec = VideoCodec.entries.firstOrNull { it.name == m["codec"] } ?: s.videoCodec, hevcSupported = b("hevc"),
                audioLevel = f("audioLevel") ?: 0f, capturing = b("capturing"),
                stabilization = b("stabilization"), stabilizationSupported = b("stabSupported"),
                timerSec = i("timer") ?: s.timerSec, countdown = i("countdown") ?: 0,
            )
        }
        phoneLenses = strs("lenses"); phoneLens = i("lens") ?: 0
    }
    private var phoneLenses: List<String> = emptyList()
    private var phoneLens = 0

    private fun onPhoneFile(f: java.io.File) {
        set { it.copy(lastMedia = android.net.Uri.fromFile(f)) }
        toast("Copied from the phone: ${f.name}")
    }
    @Volatile private var recorder: WinRecorder? = null
    private var recordFile: java.io.File? = null
    private var recordJob: kotlinx.coroutines.Job? = null
    private var recordStartWall = 0L

    // Picture work buffers (camera thread).
    private var work = IntArray(0)
    private var shown = IntArray(0)
    private var toRecord = IntArray(0)
    @Volatile private var latest: IntArray? = null
    @Volatile private var latestW = 0
    @Volatile private var latestH = 0
    private val latestLock = Any()
    @Volatile private var previewPipe: ColorPipe? = null
    @Volatile private var recordPipe: ColorPipe? = null
    @Volatile private var digitalZoom = 1f
    private var lastShown = 0L
    private val stabilizer = Stabilizer()
    @Volatile private var preRoll: PreRoll? = null
    @Volatile private var photoSrc: IntArray? = null
    private var cropBuf: IntArray? = null
    @Volatile private var cropX = 0
    @Volatile private var cropY = 0
    private var frameNo = 0L
    private var lapseLast = 0L
    @Volatile private var ndFrames = 0
    private var ndAcc: IntArray? = null
    private var ndCount = 0

    /** Leaving Capture: camera, microphone and live stream are let go. */
    fun detach() {
        try { if (st.recording) shutter() } catch (_: Throwable) {}
        preRoll?.close(); preRoll = null
        audioMeter.stopMonitoring()
        try { live.close() } catch (_: Throwable) {}
        closeCamera()
        previewView = null
        set { it.copy(ready = false) }
    }

    fun attach(owner: LifecycleOwner, view: PreviewView) {
        set { it.copy(cameraSounds = prefs.getBoolean("camera_sounds", true)) }
        DeviceClass.check(context)
        pullPresetSync()
        previewView = view
        refreshStorage()
        refreshPipes()
        scope.launch {
            devices = withContext(Dispatchers.IO) { PcCamera.list() }
            phones = withContext(Dispatchers.IO) { PhoneLink.discover() }
            tethers = withContext(Dispatchers.IO) { TetherCamera.list() }
            if (devices.isEmpty() && phones.isEmpty() && tethers.isEmpty()) {
                toast(if (com.dronedirector.veyrocutpro.windows.MfNative.ok) "No camera found — plug in a webcam or capture card, or connect your phone (Connect to PC)"
                else "The camera part of the app is missing — reinstall AlcorCut Pro")
                set { it.copy(lenses = emptyList(), ready = false) }
            } else {
                updateLenses()
                val lastId = prefs.getString("last_lens_pc", null)
                val idx = st.lenses.indexOfFirst { it.id == lastId }.takeIf { it >= 0 } ?: st.lenses.indexOfFirst { !it.id.startsWith("phone:") }.coerceAtLeast(0)
                set { it.copy(lensIndex = idx) }
                if (st.mode == CaptureMode.LIVE) { openCamera(); live.open(); set { it.copy(ready = true) } } else openCamera()
            }
            // Phones turning on Connect to PC later show up by themselves.
            watchJob?.cancel()
            watchJob = scope.launch {
                while (previewView != null) {
                    delay(6000)
                    if (phone != null) continue
                    val now = withContext(Dispatchers.IO) { PhoneLink.discover() }
                    val pcs = withContext(Dispatchers.IO) { PcCamera.list() }
                    val tcs = if (tether == null) withContext(Dispatchers.IO) { TetherCamera.list() } else tethers
                    if (now.map { it.link }.toSet() != phones.map { it.link }.toSet() || pcs != devices || tcs != tethers) {
                        phones = now; devices = pcs; tethers = tcs
                        val keep = st.lens?.id
                        updateLenses()
                        st.lenses.indexOfFirst { it.id == keep }.takeIf { it >= 0 }?.let { i -> set { it.copy(lensIndex = i) } }
                        if (cam == null && phone == null && devices.isNotEmpty() && st.lens?.id?.startsWith("phone:") != true) openCamera()
                    }
                }
            }
        }
    }

    fun release() {
        CaptureLocation.stop(context)
        setRemote(false)
        live.close()
        audioMeter.stopMonitoring()
        closeCamera()
        io.shutdown(); analysisExec.shutdown()
        sound.release()
    }

    private fun closeCamera() {
        watchJob?.cancel(); watchJob = null
        closeTether()
        phone?.close(); phone = null; android.hardware.Motion.phoneLinked = false; android.hardware.Motion.phoneRoll = null
        cam?.close(); cam = null
        synchronized(latestLock) { latest = null }
    }

    /** Opens the chosen PC camera and reads what it can do. */
    private suspend fun openCamera() {
        val lens = st.lens ?: return
        phones.firstOrNull { it.link == lens.id }?.let { p ->
            if (phone?.device?.link == p.link) return
            phone?.close(); phone = null; android.hardware.Motion.phoneLinked = false; android.hardware.Motion.phoneRoll = null
            pinError = null
            pinRequest = p
            return
        }
        phone?.close(); phone = null; android.hardware.Motion.phoneLinked = false; android.hardware.Motion.phoneRoll = null
        closeTether()
        tethers.firstOrNull { it.link == lens.id }?.let { t -> openTether(t); return }
        val dev = devices.firstOrNull { it.link == lens.id } ?: return
        val c = withContext(Dispatchers.IO) { cam?.close(); PcCamera(dev).takeIf { it.open() } }
        if (c == null) {
            toast("Couldn't open ${dev.name}: " + com.dronedirector.veyrocutpro.windows.MfNative.error().ifBlank { "another app may be using it" })
            set { it.copy(ready = false) }
            return
        }
        cam = c
        readCapabilities(c)
        bind()
    }

    // Control ranges, translated to the phone's terms.
    private var exposureCtl: PcControl? = null
    private var gainCtl: PcControl? = null
    private var focusCtl: PcControl? = null
    private var wbCtl: PcControl? = null
    private var zoomCtl: PcControl? = null
    private var brightCtl: PcControl? = null

    private fun readCapabilities(c: PcCamera) {
        exposureCtl = c.control(0, PcCamera.EXPOSURE)
        gainCtl = c.control(1, PcCamera.GAIN)
        focusCtl = c.control(0, PcCamera.FOCUS)
        wbCtl = c.control(1, PcCamera.WHITE_BALANCE)
        zoomCtl = c.control(0, PcCamera.ZOOM)?.takeIf { it.max > it.min }
        brightCtl = c.control(1, PcCamera.BRIGHTNESS)?.takeIf { it.max > it.min }
        val f = c.formats
        // Shutter speeds the camera offers: its exposure steps are powers of two of a second.
        val shutters = exposureCtl?.let { e -> (e.min..e.max step max(1, e.step)).map { v -> (1e9 * 2.0.pow(v)).toLong() }.filter { it in 50_000L..2_000_000_000L } }.orEmpty()
        val isos = gainCtl?.let { listOf(100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200) }.orEmpty()
        val heights = f.map { it.height }.toSet()
        val res = VideoRes.entries.filter { v -> f.any { it.height == v.quality.height && it.width == v.quality.width } || (v.quality.height in heights && f.any { it.height == v.quality.height }) }
        val bestFps = { h: Int -> f.filter { it.height == h }.map { it.fps.roundToInt() }.toSet() }
        val fpsAt = bestFps(st.videoRes.quality.height).ifEmpty { f.map { it.fps.roundToInt() }.toSet() }
        val fpsList = listOf(24, 25, 30, 48, 50, 60, 100, 120).filter { want -> fpsAt.any { it >= want - 1 } }.ifEmpty { listOf(30) }
        val slow = f.filter { it.fps >= 59f && it.height >= 480 }.map { it.fps.roundToInt() }.distinct().sorted()
        val sizes = f.map { it.width to it.height }.distinct().sortedByDescending { it.first * it.second }
            .map { (w, h) -> PhotoSize(w, h, false) }
        val savedSize = prefs.getString("pc_photo_size_${c.device.link}", null)
        val maxZoom = zoomCtl?.let { z -> (z.max.toFloat() / z.min.coerceAtLeast(1)).coerceIn(2f, 10f) } ?: 4f
        set { s ->
            s.copy(
                manualSensor = exposureCtl != null,
                isoStops = isos, shutterStops = shutters,
                minFocusDiopters = if (focusCtl != null) 10f else 0f,
                evMin = if (brightCtl != null) -6 else 0, evMax = if (brightCtl != null) 6 else 0, evStep = 1f / 3f,
                rawSupported = false, hasFlash = true, scenes = listOf(Scene.STANDARD),
                videoResolutions = res.ifEmpty { listOf(VideoRes.HD) },
                videoRes = if (s.videoRes in res) s.videoRes else res.firstOrNull { it == VideoRes.FHD } ?: res.firstOrNull() ?: VideoRes.HD,
                fpsOptions = fpsList, fps = if (s.fps in fpsList) s.fps else fpsList.firstOrNull { it == 30 } ?: fpsList.first(),
                stabilizationSupported = true, hlgSupported = false, flatSupported = true,
                photoSizes = sizes, photoSize = sizes.firstOrNull { it.key == savedSize } ?: sizes.firstOrNull { sz -> f.any { it.width == sz.width && it.height == sz.height && it.fps >= 14f } } ?: sizes.firstOrNull(),
                sensorReport = c.device.name + (sizes.firstOrNull()?.let { " · up to ${it.width}×${it.height}" } ?: ""),
                minZoom = 1f, maxZoom = maxZoom, zoom = 1f,
                slowMoFps = slow.filter { it < 100 }, superSlowMoFps = slow.filter { it >= 100 }, slowFallbackFps = 0,
                slowFps = slow.maxOrNull() ?: 0,
                flash = FlashSetting.OFF, scene = Scene.STANDARD,
                photoFormat = PhotoFormat.JPEG,
                previewStabilizationSupported = false,
            )
        }
    }

    /** Starts the camera in the format the mode needs. */
    private fun bind() {
        if (phone != null || tether != null) return
        val c = cam ?: return
        val s = st
        if (s.mode == CaptureMode.LIVE && !live.state.value.isBusy) { /* the stream reads from here too */ }
        val f = c.formats
        val format: PcFormat? = when {
            s.mode.isSlowMo -> f.filter { it.fps.roundToInt() >= (s.slowFps.takeIf { v -> v > 0 } ?: 60) - 1 }.maxByOrNull { it.width * it.height }
            s.isVideo || s.mode == CaptureMode.LIVE -> {
                val q = if (s.mode == CaptureMode.LIVE) live.state.value.quality.let { it.width to it.height } else s.videoRes.quality.let { it.width to it.height }
                c.pick(q.first, q.second, s.fps.toFloat())
                    ?: f.filter { it.height <= q.second }.maxWithOrNull(compareBy<PcFormat>({ it.height }, { -abs(it.fps - s.fps) }))
                    ?: f.minByOrNull { it.height }
            }
            else -> s.photoSize?.let { c.pick(it.width, it.height, 30f) } ?: f.maxByOrNull { it.width * it.height }
        }
        if (format == null) { toast("This camera reports no picture formats"); return }
        if (c.current == format && c.width > 0) { set { it.copy(ready = true) }; applyControls(); return }
        set { it.copy(ready = false) }
        scope.launch(Dispatchers.IO) {
            val ok = c.start(format, ::onFrame) { scope.launch { toast("The camera was disconnected"); set { it.copy(ready = false) } } }
            withContext(Dispatchers.Main) {
                if (!ok) toast("Couldn't start the camera at ${format.width}×${format.height}: " + com.dronedirector.veyrocutpro.windows.MfNative.error())
                set { it.copy(ready = ok) }
                applyControls()
            }
        }
    }

    // ---------------------------------------------------------------- every picture

    private fun onFrame(px: IntArray, w: Int, h: Int, ts: Long) {
        frameNo++
        val n = w * h
        if (work.size != n) { work = IntArray(n); shown = IntArray(n); toRecord = IntArray(n) }
        // Digital zoom (when the camera has no zoom of its own) and mirror.
        val z = digitalZoom
        val flip = CaptureExtras.flipH
        val src = if (z > 1.01f || flip) { transform(px, work, w, h, z, flip); work } else px
        // Privacy: pixelate the faces found.
        if (CaptureExtras.faceBlur) pixelateFaces(src, w, h)
        // Digital stabilization (video) and the hyperlapse's steadying.
        val s0 = st
        if ((s0.stabilization && s0.mode.isNormalVideo) || (s0.mode == CaptureMode.HYPERLAPSE && s0.lapseStabilize)) stabilizer.apply(src, w, h)
        // Photos are kept without the monitoring LUT (it is only for looking).
        photoSrc = if (!s0.isVideo && CaptureExtras.monitorLut != null) (photoSrc?.takeIf { it.size == n } ?: IntArray(n)).also { System.arraycopy(src, 0, it, 0, n) } else null
        // The picture for the recording / stream (with the log profile) and the one shown.
        val rp = recordPipe; val pp = previewPipe
        rp?.apply(src, toRecord, n) ?: System.arraycopy(src, 0, toRecord, 0, n)
        // Electronic ND (motion blur): several pictures averaged.
        val nd = ndFrames
        if (nd > 1 && st.mode.isNormalVideo) blend(toRecord, n, nd)
        if (CaptureExtras.titleOn && CaptureExtras.titleText.isNotBlank() && recorder != null) drawTitle(toRecord, w, h)
        val s = st
        recorder?.let { r0 ->
            // The frame guide's shape: only the middle part goes into the clip.
            val r = r0
            val frame = if (r.width != w || r.height != h) {
                val cb = cropBuf?.takeIf { it.size == r.width * r.height } ?: IntArray(r.width * r.height).also { cropBuf = it }
                for (y in 0 until r.height) System.arraycopy(toRecord, (y + cropY) * w + cropX, cb, y * r.width, r.width)
                cb
            } else toRecord
            when {
                s.mode == CaptureMode.TIMELAPSE -> { val now = System.currentTimeMillis(); if (now - lapseLast >= s.lapseIntervalMs) { lapseLast = now; r.frame(frame); set { it.copy(lapseFrames = r.frames + 1) } } }
                s.mode == CaptureMode.HYPERLAPSE -> if (frameNo % s.hyperSpeed.coerceAtLeast(1) == 0L) { r.frame(frame); set { it.copy(lapseFrames = r.frames + 1) } }
                else -> r.frame(frame)
            }
        }
        val out = if (pp === rp) toRecord else { pp?.apply(src, shown, n) ?: System.arraycopy(src, 0, shown, 0, n); shown }
        synchronized(latestLock) {
            val l = latest?.takeIf { it.size == n } ?: IntArray(n)
            System.arraycopy(out, 0, l, 0, n)
            latest = l; latestW = w; latestH = h
        }
        live.frame(toRecord, w, h)
        if (recorder == null) preRoll?.frame(toRecord, w, h)
        // The screen (up to ~30 pictures a second).
        val now = System.nanoTime()
        if (now - lastShown > 30_000_000L) {
            lastShown = now
            val v = previewView
            if (v != null) {
                if (w > 2560) { val (sp, sw, sh) = shrink(out, w, h, 1920); v.post(sp, sw, sh) } else v.post(out, w, h)
            }
        }
        // Histogram, zebras, focus peaking, false colour, face finding.
        if ((s.proHistogram || s.proZebras || s.proPeaking || s.proFalseColor || CaptureExtras.faceBlur) && frameNo % 2 == 0L && analysisBusy.compareAndSet(false, true)) {
            val step = max(1, w / 480)
            val lw = w / step; val lh = h / step
            val luma = ByteArray(lw * lh)
            for (y in 0 until lh) { val row = y * step * w; for (x in 0 until lw) { val c = out[row + x * step]; luma[y * lw + x] = ((((c shr 16) and 0xFF) * 77 + ((c shr 8) and 0xFF) * 150 + (c and 0xFF) * 29) shr 8).toByte() } }
            analysisExec.execute {
                try {
                    val img = androidx.camera.core.ImageProxy(lw, lh, arrayOf(androidx.camera.core.ImageProxy.PlaneProxy(java.nio.ByteBuffer.wrap(luma), lw, 1)))
                    if (CaptureExtras.faceBlur) FaceBlur.submitLuma(luma, lw, lh, 0)
                    if (s.proHistogram || s.proZebras || s.proPeaking || s.proFalseColor) analyzer.analyze(img)
                } catch (_: Throwable) {} finally { analysisBusy.set(false) }
            }
        } else if (!(s.proHistogram || s.proZebras || s.proPeaking || s.proFalseColor) && _analysis.value != null && frameNo % 15 == 0L) _analysis.value = null
    }

    private fun shrink(px: IntArray, w: Int, h: Int, maxW: Int): Triple<IntArray, Int, Int> {
        val k = w.toFloat() / maxW
        val ow = maxW; val oh = (h / k).toInt()
        val o = IntArray(ow * oh)
        parallel(oh * ow) { a, b -> for (i in a until b) { val y = i / ow; val x = i % ow; o[i] = px[(y * k).toInt().coerceAtMost(h - 1) * w + (x * k).toInt().coerceAtMost(w - 1)] } }
        return Triple(o, ow, oh)
    }

    /** Crops the middle 1/[z] of the picture back to full size (bilinear), mirrored when [flip]. */
    private fun transform(src: IntArray, dst: IntArray, w: Int, h: Int, z: Float, flip: Boolean) {
        val cw = w / z; val ch = h / z
        val x0 = (w - cw) / 2f; val y0 = (h - ch) / 2f
        val kx = cw / w; val ky = ch / h
        parallel(w * h) { a, b ->
            for (i in a until b) {
                val y = i / w; var x = i % w
                if (flip) x = w - 1 - x
                val fx = (x0 + x * kx).coerceIn(0f, w - 1.001f); val fy = (y0 + y * ky).coerceIn(0f, h - 1.001f)
                val ix = fx.toInt(); val iy = fy.toInt(); val dx = fx - ix; val dy = fy - iy
                if (z <= 1.01f) { dst[i] = src[iy * w + ix]; continue }
                val p00 = src[iy * w + ix]; val p10 = src[iy * w + ix + 1]; val p01 = src[(iy + 1) * w + ix]; val p11 = src[(iy + 1) * w + ix + 1]
                fun ch(sh: Int): Int {
                    val a0 = ((p00 shr sh) and 0xFF) * (1 - dx) + ((p10 shr sh) and 0xFF) * dx
                    val a1 = ((p01 shr sh) and 0xFF) * (1 - dx) + ((p11 shr sh) and 0xFF) * dx
                    return (a0 * (1 - dy) + a1 * dy + 0.5f).toInt()
                }
                dst[i] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
    }

    private fun pixelateFaces(px: IntArray, w: Int, h: Int) {
        val r = FaceBlur.current()
        var k = 0
        while (k + 3 < r.size) {
            val cx = r[k] * w; val cy = r[k + 1] * h; val hw = r[k + 2] * w; val hh = r[k + 3] * h
            k += 4
            val cell = max(4, (hw / 6).toInt())
            val xa = (cx - hw).toInt().coerceIn(0, w - 1); val xb = (cx + hw).toInt().coerceIn(0, w - 1)
            val ya = (cy - hh).toInt().coerceIn(0, h - 1); val yb = (cy + hh).toInt().coerceIn(0, h - 1)
            var by = ya
            while (by <= yb) {
                var bx = xa
                while (bx <= xb) {
                    val c = px[min(by + cell / 2, h - 1) * w + min(bx + cell / 2, w - 1)]
                    for (yy in by until min(by + cell, yb + 1)) for (xx in bx until min(bx + cell, xb + 1)) {
                        val dx = (xx - cx) / hw; val dy = (yy - cy) / hh
                        if (dx * dx + dy * dy < 1f) px[yy * w + xx] = c
                    }
                    bx += cell
                }
                by += cell
            }
        }
    }

    private fun blend(px: IntArray, n: Int, frames: Int) {
        val acc = ndAcc?.takeIf { it.size == n * 3 } ?: IntArray(n * 3).also { ndAcc = it; ndCount = 0 }
        val k = 1f / min(frames, ndCount + 1)
        for (i in 0 until n) {
            val c = px[i]
            for (ch in 0..2) {
                val v = (c shr (16 - ch * 8)) and 0xFF
                val a = if (ndCount == 0) v * 256 else acc[i * 3 + ch]
                acc[i * 3 + ch] = (a + (v * 256 - a) * k).toInt()
            }
            px[i] = (0xFF shl 24) or ((acc[i * 3] shr 8) shl 16) or ((acc[i * 3 + 1] shr 8) shl 8) or (acc[i * 3 + 2] shr 8)
        }
        ndCount++
    }

    /** Title (Settings → TITLE) over the first seconds of each clip. */
    private fun drawTitle(px: IntArray, w: Int, h: Int) {
        val r = recorder ?: return
        if (r.seconds >= CaptureExtras.titleSec) return
        val img = java.awt.image.BufferedImage(java.awt.image.DirectColorModel(32, 0xFF0000, 0xFF00, 0xFF, 0),
            java.awt.image.Raster.createPackedRaster(java.awt.image.DataBufferInt(px, w * h), w, h, w, intArrayOf(0xFF0000, 0xFF00, 0xFF), null), false, null)
        val g = img.createGraphics()
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, max(18, h / 16))
        val fm = g.fontMetrics; val text = CaptureExtras.titleText
        val tx = (w - fm.stringWidth(text)) / 2
        val ty = when (CaptureExtras.titlePos) { 0 -> h / 8 + fm.ascent; 1 -> (h + fm.ascent) / 2; else -> h - h / 8 }
        g.color = java.awt.Color(0, 0, 0, 150); g.drawString(text, tx + 2, ty + 2)
        g.color = java.awt.Color.WHITE; g.drawString(text, tx, ty)
        g.dispose()
    }

    /** A copy of what the viewfinder shows (coloured as shown), at most [longSide] px. */
    private fun grabViewfinder(longSide: Int): Bitmap? {
        val (px, w, h) = synchronized(latestLock) { Triple(latest?.copyOf() ?: return null, latestW, latestH) }
        val b = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val k = longSide.toFloat() / max(w, h)
        return if (k < 1f) Bitmap.createScaledBitmap(b, (w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1), true) else b
    }
    private fun latestFrame(): Triple<IntArray, Int, Int>? = synchronized(latestLock) { latest?.let { Triple(it, latestW, latestH) } }
    fun castFrame(): Bitmap? = grabViewfinder(1280)

    // ---------------------------------------------------------------- colour

    private fun logCurve(s: CameraUiState): Int = if (s.mode == CaptureMode.PRO_VIDEO && s.profile.isLog) s.profile.curve else 0

    /** Rebuilds the colour tables (filter, log profile, monitoring LUT) in the background. */
    private fun refreshPipes() {
        val s = st
        val lut = CaptureExtras.monitorLut?.let { com.dronedirector.veyrocutpro.editor.LutLibrary.get(it) }
        val curve = logCurve(s)
        val filter = s.filter
        if (viaPhone || tether != null) { recordPipe = null; previewPipe = null; return }
        io.execute {
            val rec = ColorPipe(filter, curve, if (CaptureExtras.bakeLut) lut else null)
            val view = if (!s.logAssist && (lut == null || CaptureExtras.bakeLut)) rec else ColorPipe(filter, if (s.logAssist) 0 else curve, lut)
            recordPipe = rec.takeIf { it.table != null }
            previewPipe = view.takeIf { it.table != null }
        }
    }

    // ---------------------------------------------------------------- controls

    /** Sends exposure, focus, white balance, zoom, flicker and brightness to the camera. */
    private fun applyControls() {
        if (phone != null || tether != null) return
        val c = cam ?: return
        val s = st
        scope.launch(Dispatchers.IO) {
            exposureCtl?.let { e ->
                val manual = (s.isPro && s.manualShutter) || s.aeLock
                if (manual) {
                    val ns = if (s.aeLock && !s.manualShutter) (1e9 * 2.0.pow(e.value)).toLong() else s.shutterNs
                    c.set(0, PcCamera.EXPOSURE, log2(ns / 1e9).roundToInt(), false)
                } else c.set(0, PcCamera.EXPOSURE, e.value, true)
            }
            gainCtl?.let { g ->
                if (s.isPro && s.manualIso) {
                    val t = (ln(s.iso / 100.0) / ln(32.0)).coerceIn(0.0, 1.0)
                    c.set(1, PcCamera.GAIN, (g.min + (g.max - g.min) * t).roundToInt(), false)
                } else if (g.canAuto) c.set(1, PcCamera.GAIN, g.value, true)
            }
            focusCtl?.let { f ->
                if (s.manualFocusActive) c.set(0, PcCamera.FOCUS, (f.min + (f.max - f.min) * (s.focusDiopters / 10f)).roundToInt(), false)
                else c.set(0, PcCamera.FOCUS, f.value, true)
            }
            wbCtl?.let { w ->
                val k = s.whiteBalance.label.removeSuffix("K").toIntOrNull()
                when {
                    s.isPro && k != null -> c.set(1, PcCamera.WHITE_BALANCE, k, false)
                    s.isPro && s.awbLock -> c.set(1, PcCamera.WHITE_BALANCE, w.value, false)
                    else -> c.set(1, PcCamera.WHITE_BALANCE, w.value, true)
                }
            }
            brightCtl?.let { b ->
                val mid = b.default
                val span = max(b.max - mid, mid - b.min)
                c.set(1, PcCamera.BRIGHTNESS, (mid + span * (s.ev / 6f) * 0.6f).roundToInt(), false)
            }
            c.control(1, PcCamera.POWERLINE)?.let { c.set(1, PcCamera.POWERLINE, when (CaptureExtras.antiFlicker) { 1 -> 1; 2 -> 2; 3 -> 0; else -> 3 }, false) }
            zoomCtl?.let { z -> c.set(0, PcCamera.ZOOM, (z.min + (z.max - z.min) * ((s.zoom - 1f) / (s.maxZoom - 1f).coerceAtLeast(0.01f))).roundToInt(), false) }
            // Live read-outs for the exposure display.
            c.refreshControls()
            val e = c.control(0, PcCamera.EXPOSURE)
            val shut = e?.let { (1e9 * 2.0.pow(it.value)).toLong() }
            withContext(Dispatchers.Main) { set { it.copy(liveShutterNs = shut, liveIso = gainCtl?.let { 100 } ) } }
        }
        digitalZoom = if (zoomCtl == null) s.zoom.coerceAtLeast(1f) else 1f
    }

    private var meteredExposure = 100.0 * 8_333_333.0
    fun effectiveExposure(s: CameraUiState = st): Pair<Int, Long> = when {
        s.manualIso && s.manualShutter -> s.iso to s.shutterNs
        s.manualIso -> s.iso to s.shutterNs
        else -> s.iso to s.shutterNs
    }
    fun priorityActive(s: CameraUiState = st): Boolean = s.isPro && s.manualSensor && (s.manualIso != s.manualShutter) && !s.aeLock

    // ---------------------------------------------------------------- the phone's public controls

    fun setMonitorLandscape(on: Boolean) {}
    fun setDisplayRotation(r: Int) {}

    fun refreshSizes() { cam?.let { readCapabilities(it) }; bind() }
    fun setPhotoSize(size: PhotoSize) {
        if (st.photoSize == size) return
        cam?.let { prefs.edit().putString("pc_photo_size_${it.device.link}", size.key).apply() }
        set { it.copy(photoSize = size) }
        if (!st.isVideo && st.mode != CaptureMode.LIVE) bind()
    }
    fun photoFormats(): List<PhotoFormat> = listOf(PhotoFormat.JPEG)
    /** Pre-record: keeps the last seconds while a normal video mode waits. */
    fun refreshPreRecord() {
        val s = st
        val want = CaptureExtras.preRecordSec > 0 && s.mode.isNormalVideo && !s.recording && phone == null && tether == null && !CaptureExtras.recordAspect
        if (want && preRoll == null) preRoll = PreRoll(CaptureExtras.preRecordSec)
        else if (!want) { preRoll?.close(); preRoll = null }
    }

    private var lastSmartMode = CaptureMode.TIMELAPSE
    private var lastRegularMode = CaptureMode.PHOTO

    fun setMode(mode: CaptureMode) {
        val s = st
        if (s.recording || s.stitching || s.mode == mode) return
        if (phone != null && mode != CaptureMode.LIVE) { prefs.edit().putString("last_mode", mode.name).apply(); set { it.copy(mode = mode, activeParam = null, showFilters = false) }; fwd("mode", mode.name); return }
        if (s.mode == CaptureMode.LIVE && live.state.value.isBusy) { toast("End the live stream first"); return }
        if (mode == CaptureMode.PANORAMA) { toast("Panorama follows how a phone turns — use it in Capture on the phone, or connect your phone (Connect to PC)"); return }
        stopInterval()
        if (s.mode == CaptureMode.LIVE) live.close()
        if (mode.isSmart) lastSmartMode = mode else lastRegularMode = mode
        prefs.edit().putString("last_mode", mode.name).apply()
        set { it.copy(mode = mode, activeParam = null, showFilters = false, slowFps = if (mode.isSlowMo) (if (mode == CaptureMode.SUPER_SLOW_MO) it.superSlowMoFps.maxOrNull() else it.slowMoFps.maxOrNull()) ?: it.slowFps else it.slowFps) }
        if (mode.isSlowMo && st.slowMoFps.isEmpty() && st.superSlowMoFps.isEmpty()) toast("This camera can't film fast enough for slow motion (it needs 60 fps or more)")
        if (mode == CaptureMode.LIVE) { audioMeter.stopMonitoring(); live.open() }
        refreshPipes()
        bind()
        refreshPreRecord()
    }

    fun showModeGroup(smart: Boolean) {
        if (st.mode.isSmart == smart) return
        val want = if (smart) lastSmartMode else lastRegularMode
        setMode(if (SimpleMode.allowed(want)) want else if (smart) SimpleMode.smart().first { it != CaptureMode.PANORAMA } else SimpleMode.regular().first())
    }

    fun setSimple(on: Boolean) {
        SimpleMode.set(context, on)
        if (on && !SimpleMode.allowed(st.mode) && !st.recording) setMode(if (st.isVideo) CaptureMode.VIDEO else CaptureMode.PHOTO)
    }

    fun savePreset(slot: Int) {
        CamPresets.save(context, slot, CamPreset.from(st))
        toast("C$slot saved")
        if (CaptureExtras.presetSyncUri != null) scope.launch(Dispatchers.IO) { if (!CamPresets.syncPush(context)) toast("Couldn't update the set-up sync file") }
    }
    fun pullPresetSync() {
        if (CaptureExtras.presetSyncUri == null) return
        scope.launch(Dispatchers.IO) { if (CamPresets.syncPull(context)) toast("C1–C3 updated from your other device") }
    }
    fun recallPreset(slot: Int) {
        val p = CamPresets.load(context, slot) ?: run { toast("C$slot is empty — hold it to save your current settings"); return }
        applySetup(p, "C$slot")
    }
    fun recentSetups(): List<CamPreset> = CamPresets.recent(context)
    fun recallRecent(i: Int) { CamPresets.recent(context).getOrNull(i)?.let { applySetup(it, "Last set-up") } }
    private fun rememberSetup() { val p = CamPreset.from(st); if (p.mode != CaptureMode.LIVE) io.execute { CamPresets.addRecent(context, p) } }

    private fun applySetup(p: CamPreset, label: String) {
        if (st.recording) { toast("Stop recording first"); return }
        if (p.mode == CaptureMode.LIVE || p.mode == CaptureMode.PANORAMA) return
        if (p.mode != st.mode) setMode(p.mode)
        set { s ->
            s.copy(
                manualIso = p.manualIso && s.manualSensor, manualShutter = p.manualShutter && s.manualSensor,
                iso = p.iso, shutterNs = p.shutterNs,
                manualFocus = p.manualFocus && s.minFocusDiopters > 0f, focusDiopters = p.focusDiopters,
                whiteBalance = p.whiteBalance, ev = p.ev.coerceIn(s.evMin, s.evMax),
                aspect = p.aspect, timerSec = p.timerSec, burst = p.burst, grid = p.grid,
                zebras = p.zebras, zebraLevel = p.zebraLevel, peaking = p.peaking, falseColor = p.falseColor,
                videoRes = if (p.videoRes in s.videoResolutions) p.videoRes else s.videoRes,
                fps = if (p.fps in s.fpsOptions) p.fps else s.fps,
                profile = if (p.profile == VideoProfile.HLG) VideoProfile.STANDARD else p.profile,
                highBitrate = p.highBitrate, logAssist = p.logAssist, videoCodec = p.videoCodec,
                audioGainDb = p.audioGainDb, audioLimiter = p.audioLimiter, filter = p.filter, guide = p.guide,
                aeLock = false, aeLockPoint = null, activeParam = null,
            )
        }
        refreshPipes(); bind(); applyControls()
        toast(label)
    }

    fun selectLens(index: Int) {
        if (st.recording || index == st.lensIndex || index !in st.lenses.indices) return
        set { it.copy(lensIndex = index, manualFocus = false) }
        st.lenses.getOrNull(index)?.let { l -> prefs.edit().putString("last_lens_pc", l.id).apply() }
        scope.launch { openCamera() }
    }

    fun setAntiFlicker(v: Int) { CaptureExtras.antiFlicker = v; CaptureExtras.save(context); applyControls() }
    fun setDualCam(on: Boolean) { toast("Two cameras at once needs a phone that can run both") }
    fun macroLensIndex(): Int? = null
    /** Macro on a PC camera: focus as close as it can (webcams with adjustable focus), or the phone's macro. */
    fun macro() {
        if (fwd("macro")) return
        val near = st.minFocusDiopters
        if (near <= 0f || focusCtl == null) { toast("This camera has a fixed focus: macro needs one with adjustable focus (or your phone as the camera)"); return }
        set { it.copy(manualFocus = true, focusDiopters = near) }
        applyControls()
        toast("Macro: focused as close as this camera goes — move it near the subject until it's sharp")
    }

    /** Next camera on the PC (with the phone: the phone's next lens). */
    fun flipCamera() {
        if (phone != null) { if (phoneLenses.size > 1) fwd("lens", (phoneLens + 1) % phoneLenses.size); return }
        val s = st; if (s.lenses.size > 1) selectLens((s.lensIndex + 1) % s.lenses.size)
    }

    fun setActiveParam(p: ProParam?) = set { it.copy(activeParam = if (it.activeParam == p) null else p) }

    fun setIso(iso: Int) {
        if (fwd("iso", iso)) return
        if (tether != null) { tset(tIso, iso.toLong()); set { it.copy(iso = iso, manualIso = true) }; return }
        if (gainCtl == null) { toast("This camera has no manual gain (ISO)"); return }
        set { it.copy(manualIso = true, iso = iso) }; applyControls()
    }
    fun setShutter(ns: Long) {
        if (fwd("shutterNs", ns)) return
        if (tether != null) { tset(tShutter, ns / 100_000L); set { it.copy(shutterNs = ns, manualShutter = true) }; return }
        if (!st.manualSensor) return
        set { it.copy(manualShutter = true, shutterNs = ns) }; applyControls()
    }
    fun setIsoAuto() {
        if (fwd("isoAuto")) return
        set { it.copy(manualIso = false) }; applyControls() }
    fun setShutterAuto() {
        if (fwd("shutterAuto")) return
        set { it.copy(manualShutter = false) }; applyControls() }

    fun setFocus(diopters: Float) {
        if (fwd("focus", diopters)) return
        if (st.minFocusDiopters <= 0f) return
        set { it.copy(manualFocus = true, focusDiopters = diopters.coerceIn(0f, it.minFocusDiopters)) }
        applyControls()
    }

    private var rackJob: kotlinx.coroutines.Job? = null
    fun rackFocus(target: Float, durMs: Long) {
        if (st.minFocusDiopters <= 0f) return
        rackJob?.cancel()
        val from = st.focusDiopters
        rackJob = scope.launch {
            val t0 = System.currentTimeMillis()
            while (true) {
                val p = ((System.currentTimeMillis() - t0).toFloat() / durMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                setFocus(from + (target - from) * p * p * (3f - 2f * p))
                if (p >= 1f) break
                delay(33)
            }
        }
    }

    fun holdManualFocus(x: Float, y: Float) {
        val s = st
        if (s.mode == CaptureMode.LIVE) return
        if (s.minFocusDiopters <= 0f) { toast("This camera has fixed focus"); return }
        rackJob?.cancel()
        set { it.copy(manualFocus = true, focusPoint = x to y, focusId = it.focusId + 1) }
        applyControls()
        if (!s.manualFocus) toast("Manual focus · slide on the right edge · ✕ for auto")
    }

    fun setFocusAuto() {
        if (fwd("focusAuto")) return
        rackJob?.cancel(); set { it.copy(manualFocus = false) }; applyControls() }

    fun setWhiteBalance(wb: WhiteBalance) {
        if (fwd("wb", wb.name)) return
        if (tether != null) {
            val code = when (wb) { WhiteBalance.AUTO -> 2L; WhiteBalance.DAYLIGHT -> 4L; WhiteBalance.FLUORESCENT -> 5L; WhiteBalance.TUNGSTEN -> 6L; else -> -1L }
            if (code < 0 || tWb?.allowed?.let { it.isNotEmpty() && code !in it } == true) toast("Pick this white balance on the camera itself") else { tset(tWb, code); set { it.copy(whiteBalance = wb) } }
            return
        }
        if (wbCtl == null && wb != WhiteBalance.AUTO) { toast("This camera sets its white balance itself"); return }
        set { it.copy(whiteBalance = wb, awbLock = false) }; applyControls()
    }
    fun toggleAwbLock() {
        if (fwd("awbLock")) return
        set { it.copy(awbLock = !it.awbLock) }; applyControls() }
    fun toggleAeLock() {
        if (fwd("aeLock")) return
        set { it.copy(aeLock = !it.aeLock, aeLockPoint = null) }; applyControls() }

    fun lockExposureAt(x: Float, y: Float) {
        if (st.manualExposure) { toast("Exposure is manual — no lock needed"); return }
        set { it.copy(aeLock = false, aeLockPoint = x to y, focusPoint = x to y, focusId = it.focusId + 1) }
        scope.launch { delay(500); if (st.aeLockPoint == (x to y)) { set { it.copy(aeLock = true) }; applyControls() } }
    }
    fun unlockExposure() { set { it.copy(aeLock = false, aeLockPoint = null, ev = 0) }; applyControls() }

    fun setEv(index: Int) {
        if (fwd("ev", index)) return
        if (tether != null) { tset(tEv, (index * st.evStep * 1000).roundToInt().toLong()); set { it.copy(ev = index) }; return }
        set { it.copy(ev = index.coerceIn(it.evMin, it.evMax)) }; applyControls() }

    fun setZoom(ratio: Float) {
        if (fwd("zoom", ratio.coerceIn(st.minZoom, st.maxZoom))) return
        val s = st
        val z = ratio.coerceIn(s.minZoom, s.maxZoom)
        set { it.copy(zoom = z) }
        applyControls()
    }

    /** The window is white while a photo is taken with the screen as the flash. */
    var screenFlash by androidx.compose.runtime.mutableStateOf(false)
        private set

    /**
     * Flash on a PC camera: the screen lights your face for the photo (webcams face you), and network
     * video lights (Elgato Key Light and alike) flash to full. Torch = those lights on.
     */
    fun setFlash(f: FlashSetting) {
        if (fwd("flash", f.name)) return
        if (tether != null) { if (f != FlashSetting.OFF) toast("Set the flash on the camera itself"); return }
        val was = st.flash
        set { it.copy(flash = f) }
        when {
            f == FlashSetting.TORCH -> io.execute {
                KeyLight.find()
                if (KeyLight.on(100) == 0) { toast("No network video light found (Elgato Key Light and alike): the torch needs one on the same network"); set { it.copy(flash = FlashSetting.OFF) } }
                else toast("Video light on")
            }
            was == FlashSetting.TORCH -> io.execute { KeyLight.restore() }
            f == FlashSetting.ON || f == FlashSetting.AUTO -> { toast("Flash: the screen lights you up for the photo" + if (KeyLight.lights.isNotEmpty()) ", with your video lights" else ""); io.execute { KeyLight.find() } }
        }
    }

    /** Whether the picture is dark enough for the automatic flash. */
    private fun darkScene(): Boolean = synchronized(latestLock) {
        val px = latest ?: return false
        var sum = 0L; var n = 0
        var i = 0
        while (i < px.size) { val c = px[i]; sum += ((c shr 16 and 0xFF) * 3 + (c shr 8 and 0xFF) * 6 + (c and 0xFF)) / 10; n++; i += 97 }
        n > 0 && sum / n < 55
    }

    /** Lights the scene for a photo (screen + video lights); returns whether it did. */
    private suspend fun flashUp(): Boolean {
        val s = st
        if (tether != null || phone != null || !s.hasFlash) return false
        if (!(s.flash == FlashSetting.ON || (s.flash == FlashSetting.AUTO && darkScene()))) return false
        screenFlash = true
        withContext(Dispatchers.IO) { if (KeyLight.lights.isNotEmpty()) KeyLight.on(100) }
        delay(450)                     // the camera's exposure settles on the brighter scene
        return true
    }

    private fun flashDown() {
        screenFlash = false
        if (st.flash != FlashSetting.TORCH) io.execute { KeyLight.restore() }
    }
    fun setPhotoFormat(f: PhotoFormat) { if (fwd("format", f.name)) return; if (f != PhotoFormat.JPEG) toast("RAW comes with tethered cameras") }
    fun setAspect(a: AspectMode) { set { it.copy(aspect = a) } }
    fun setScene(scene: Scene) {}
    fun setTimer(sec: Int) { if (!fwd("timer", sec)) set { it.copy(timerSec = sec) } }
    fun setBurst(count: Int) = set { it.copy(burst = count) }
    fun setGrid(g: GridMode) = set { it.copy(grid = g) }
    fun bumpLayoutVersion() = set { it.copy(layoutVersion = it.layoutVersion + 1) }
    fun toggleLevel() {
        if (!st.showLevel && !android.hardware.Motion.available) { toast("This PC has no motion sensor: the level works on a tablet or 2-in-1, or with your phone as the camera"); return }
        set { it.copy(showLevel = !it.showLevel) }
    }
    fun toggleHistogram() = set { it.copy(showHistogram = !it.showHistogram) }
    fun toggleZebras() = set { it.copy(zebras = !it.zebras) }
    fun setZebraLevel(level: Int) = set { it.copy(zebraLevel = level.coerceIn(70, 100)) }
    fun togglePeaking() = set { it.copy(peaking = !it.peaking) }
    fun toggleFalseColor() = set { it.copy(falseColor = !it.falseColor) }
    fun toggleVolumeShutter() = set { it.copy(volumeShutter = !it.volumeShutter) }
    fun toggleCameraSounds() {
        val on = !st.cameraSounds
        prefs.edit().putBoolean("camera_sounds", on).apply()
        set { it.copy(cameraSounds = on) }
    }

    fun setVideoCodec(codec: VideoCodec) {
        if (fwd("codec", codec.name)) return
        if (st.recording || st.videoCodec == codec) return
        prefs.edit().putString("video_codec", codec.name).apply()
        set { it.copy(videoCodec = codec) }
    }
    fun setAudioGain(db: Int) {
        val v = db.coerceIn(-12, 24)
        prefs.edit().putInt("audio_gain", v).apply()
        audioMeter.gainDb = v
        set { it.copy(audioGainDb = v) }
    }
    fun toggleAudioLimiter() {
        val on = !st.audioLimiter
        prefs.edit().putBoolean("audio_limiter", on).apply()
        audioMeter.limiter = on
        set { it.copy(audioLimiter = on) }
    }
    fun setClipNaming(n: ClipNaming) { prefs.edit().putString("clip_naming", n.name).apply(); set { it.copy(clipNaming = n) } }
    fun setCameraLetter(c: Char) { prefs.edit().putString("camera_letter", c.toString()).apply(); set { it.copy(cameraLetter = c) } }
    fun setReel(reel: Int) { val r = reel.coerceIn(1, 999); prefs.edit().putInt("reel", r).putInt("clip", 1).apply(); set { it.copy(reel = r, clipNumber = 1) } }
    fun setCleanFeed(on: Boolean) = set { it.copy(cleanFeed = on) }

    private fun clipName(suffix: String = ""): String {
        val s = st
        if (s.clipNaming != ClipNaming.CINEMA) return stamp() + suffix
        val date = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
        val name = "%c%03dC%03d_%s_ACLX".format(Locale.US, s.cameraLetter, s.reel, s.clipNumber, date)
        val next = (s.clipNumber + 1).coerceAtMost(999)
        prefs.edit().putInt("clip", next).apply()
        set { it.copy(clipNumber = next) }
        return name + suffix
    }

    private fun playSound(type: Int) {
        if (!prefs.getBoolean("camera_sounds", true)) return
        val style = ShutterSounds.style(context)
        if (type == MediaActionSound.SHUTTER_CLICK && style > 0) ShutterSounds.play(style) else sound.play(type)
    }

    fun toggleAudio() = set { it.copy(audio = !it.audio) }
    fun setVideoRes(r: VideoRes) { if (fwd("videoRes", r.name)) return; if (st.recording || st.videoRes == r) return; set { it.copy(videoRes = r) }; cam?.let { readCapabilities(it) }; bind() }
    fun setFps(fps: Int) { if (fwd("fps", fps)) return; if (st.recording || st.fps == fps) return; set { it.copy(fps = fps) }; bind() }
    fun toggleStabilization() { if (fwd("stabilization")) return; if (!st.recording) { set { it.copy(stabilization = !it.stabilization) }; stabilizer.reset() } }
    fun setStabilizationMode(mode: Int) {
        if (st.recording) return
        if (mode == 2) { toast("Gyroflow needs motion data: use your phone as the camera (Connect to PC), or a camera that records its own gyro data"); return }
        set { it.copy(stabilization = mode == 1) }; stabilizer.reset()
        if (mode == 1) toast("Stabilization on: the picture is cut in a little to steady it")
    }
    val stabilizationMode: Int get() = if (st.stabilization) 1 else 0

    fun toggleBitrate() {
        if (st.recording) return
        CaptureExtras.bitrateTier = (CaptureExtras.bitrateTier + 1) % 4
        CaptureExtras.save(context)
        set { it.copy(highBitrate = CaptureExtras.bitrateTier >= 2) }
    }
    fun setBitrateTier(t: Int) {
        if (st.recording) return
        CaptureExtras.bitrateTier = t.coerceIn(0, 3); CaptureExtras.save(context)
        set { it.copy(highBitrate = CaptureExtras.bitrateTier >= 2) }
    }
    fun setProfile(p: VideoProfile) {
        if (fwd("profile", p.name)) return
        if (st.recording || st.profile == p) return
        if (p == VideoProfile.HLG) { toast("HLG needs a 10-bit camera"); return }
        set { it.copy(profile = p) }
        refreshPipes()
    }

    fun tapToFocus(x: Float, y: Float) {
        set { it.copy(focusPoint = x to y, focusId = it.focusId + 1) }
        phone?.let { p -> share(x, y)?.let { (a, b) -> p.command("tapN", null, mapOf("x" to a, "y" to b)) }; return }
        // PC cameras focus on the whole picture: a tap starts their autofocus again.
        if (!st.manualFocusActive) focusCtl?.let { f -> scope.launch(Dispatchers.IO) { cam?.set(0, PcCamera.FOCUS, f.value, false); cam?.set(0, PcCamera.FOCUS, f.value, true) } }
    }
    fun meterAt(x: Float, y: Float) {
        CaptureExtras.aePoint = x to y
        phone?.let { p -> share(x, y)?.let { (a, b) -> p.command("meterN", null, mapOf("x" to a, "y" to b)) } }
    }

    /** A spot on the picture as a share (0–1) of its width and height. */
    private fun share(x: Float, y: Float): Pair<Float, Float>? {
        val v = previewView ?: return null
        if (v.width <= 0 || v.height <= 0) return null
        return (x / v.width).coerceIn(0f, 1f) to (y / v.height).coerceIn(0f, 1f)
    }
    fun setSplitAfAe(on: Boolean) { CaptureExtras.splitAfAe = on; if (!on) CaptureExtras.aePoint = null; CaptureExtras.save(context) }

    private val pullJobs = HashMap<String, kotlinx.coroutines.Job>()
    private fun glide(key: String, durMs: Long, step: (Float) -> Unit) {
        pullJobs[key]?.cancel()
        pullJobs[key] = scope.launch {
            val t0 = System.currentTimeMillis()
            while (true) {
                val p = ((System.currentTimeMillis() - t0).toFloat() / durMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                step(p * p * (3f - 2f * p)); if (p >= 1f) break; delay(33)
            }
        }
    }
    fun pullZoom(target: Float, durMs: Long) {
        val from = st.zoom.coerceAtLeast(0.01f); val to = target.coerceIn(st.minZoom, st.maxZoom).coerceAtLeast(0.01f)
        glide("zoom", durMs) { e -> setZoom(kotlin.math.exp(ln(from) + (ln(to) - ln(from)) * e)) }
    }
    fun pullEv(target: Int, durMs: Long) { val from = st.ev; glide("ev", durMs) { e -> val v = (from + (target - from) * e).roundToInt(); if (v != st.ev) setEv(v) } }
    fun pullAll(toB: Boolean) {
        val x = CaptureExtras; val d = x.pullSec * 1000L
        (if (toB) x.focusB else x.focusA)?.let { rackFocus(it, d) }
        (if (toB) x.zoomB else x.zoomA)?.let { pullZoom(it, d) }
        (if (toB) x.evB else x.evA)?.let { pullEv(it, d) }
        if (listOf(x.focusA, x.focusB, x.zoomA, x.zoomB, x.evA, x.evB).all { it == null }) toast("Set A and B points first (focus, zoom or EV)")
    }

    // ---------------------------------------------------------------- shutter

    private var captureJob: kotlinx.coroutines.Job? = null

    fun shutter() {
        val s = st
        if (s.mode == CaptureMode.LIVE) { live.toggleLive(); return }
        if (fwd("shutter")) return
        tether?.let { t ->
            if (s.isVideo) { toast("Start and stop video on the camera; the clip is copied here afterwards"); return }
            playSound(MediaActionSound.SHUTTER_CLICK); set { it.copy(shutterFlash = it.shutterFlash + 1) }
            io.execute { if (!t.shoot()) toast("The camera didn't take the photo — check its focus and USB mode") }
            return
        }
        if (!s.ready || s.stitching) return
        if (!s.recording && !s.intervalRunning) rememberSetup()
        if (s.countdown > 0) { cancelCountdown(); return }
        if (s.isVideo) {
            if (s.recording) stopRecording()
            else captureJob = scope.launch { countdown(s.timerSec); startRecording() }
            return
        }
        if (s.mode == CaptureMode.LONG_EXPOSURE) { if (longJob?.isActive == true) longStop = true else startLongExposure(); return }
        if (s.intervalRunning) { stopInterval(); return }
        if (s.capturing) return
        if (s.intervalSec > 0) { startInterval(); return }
        captureJob = scope.launch {
            countdown(s.timerSec)
            set { it.copy(capturing = true) }
            val saved = captureSet(st)
            set { it.copy(capturing = false) }
            if (saved > 1) toast("$saved photos saved")
        }
    }

    private suspend fun countdown(sec: Int) {
        if (sec <= 0) return
        for (i in sec downTo 1) { set { it.copy(countdown = i) }; delay(1000) }
        set { it.copy(countdown = 0) }
    }

    private suspend fun captureSet(s: CameraUiState): Int {
        var saved = 0
        if (s.mode == CaptureMode.BURST) { repeat(s.burstModeCount) { if (takePhoto()) saved++ }; return saved }
        if (s.mode == CaptureMode.PRO_PHOTO && s.bracket != Bracket.OFF) {
            val n = s.bracket.stops
            val base = s.ev
            for (stops in listOf(-n, 0, n)) {
                set { it.copy(ev = (base + stops * 3).coerceIn(it.evMin, it.evMax)) }; applyControls()
                delay(700)
                if (takePhoto()) saved++
            }
            set { it.copy(ev = base) }; applyControls()
            return saved
        }
        if (CaptureExtras.focusBracket > 1 && focusCtl != null) return focusBracketSet(s)
        repeat(max(1, s.burst)) { if (takePhoto()) saved++ }
        return saved
    }

    private suspend fun focusBracketSet(s: CameraUiState): Int {
        val n = CaptureExtras.focusBracket.coerceIn(2, 6)
        val shots = ArrayList<Uri>()
        val was = st.manualFocus to st.focusDiopters
        for (i in 0 until n) {
            setFocus(10f * (1f - i / (n - 1f)))
            delay(600)
            val before = st.lastMedia
            if (takePhoto()) st.lastMedia?.takeIf { it != before }?.let { shots += it }
        }
        set { it.copy(manualFocus = was.first, focusDiopters = was.second) }; applyControls()
        if (shots.size >= 2) {
            com.dronedirector.veyrocutpro.editor.PendingMerge.focus = shots.toList()
            toast("Focus set of ${shots.size} saved — open the Photo editor to stack it")
        }
        return shots.size
    }

    private fun startInterval() {
        val s = st
        set { it.copy(intervalRunning = true, intervalCount = 0) }
        captureJob = scope.launch {
            while (st.intervalRunning) {
                set { it.copy(capturing = true) }
                val n = captureSet(st)
                set { it.copy(capturing = false, intervalCount = it.intervalCount + n) }
                for (i in s.intervalSec downTo 1) { if (!st.intervalRunning) break; delay(1000) }
            }
        }
    }
    private fun stopInterval() {
        if (!st.intervalRunning) return
        captureJob?.cancel(); captureJob = null
        val count = st.intervalCount
        set { it.copy(intervalRunning = false, capturing = false) }
        toast("Interval finished: $count photos")
    }
    fun setInterval(sec: Int) { stopInterval(); set { it.copy(intervalSec = sec) } }
    fun setBracket(b: Bracket) = set { it.copy(bracket = b) }
    fun setGuide(g: FrameGuide) = set { it.copy(guide = g) }
    fun setFlip(on: Boolean) { if (st.recording) return; CaptureExtras.flipH = on; CaptureExtras.save(context) }
    fun setRecordAspect(on: Boolean) {
        if (st.recording) return
        CaptureExtras.recordAspect = on; CaptureExtras.save(context)
        if (on && st.guide == FrameGuide.OFF) set { it.copy(guide = FrameGuide.SCOPE) }
    }

    fun runFn() {
        val s = st
        when (FnAction.of(CaptureExtras.fnAction)) {
            FnAction.ZEBRAS -> toggleZebras()
            FnAction.PEAKING -> togglePeaking()
            FnAction.FALSE_COLOR -> toggleFalseColor()
            FnAction.SCOPE -> { CaptureExtras.scope = (CaptureExtras.scope + 1) % 4; if (!s.showHistogram) toggleHistogram(); CaptureExtras.save(context) }
            FnAction.AE_LOCK -> toggleAeLock()
            FnAction.AWB_LOCK -> toggleAwbLock()
            FnAction.SPLIT_AFAE -> { setSplitAfAe(!CaptureExtras.splitAfAe); toast(if (CaptureExtras.splitAfAe) "Focus: tap · exposure: double-tap" else "Focus and exposure together") }
            FnAction.PULL_A -> pullAll(false)
            FnAction.PULL_B -> pullAll(true)
            FnAction.GUIDE -> setGuide(FrameGuide.entries[(s.guide.ordinal + 1) % FrameGuide.entries.size])
            FnAction.SAFE_AREAS -> { CaptureExtras.safeAreas = !CaptureExtras.safeAreas; CaptureExtras.save(context) }
            FnAction.LOG_ASSIST -> toggleLogAssist()
            FnAction.FLIP -> setFlip(!CaptureExtras.flipH)
            FnAction.TORCH -> setFlash(FlashSetting.TORCH)
            FnAction.NEXT_LENS -> flipCamera()
            FnAction.MAGNIFIER -> { CaptureExtras.focusMagnifier = !CaptureExtras.focusMagnifier; CaptureExtras.save(context) }
            FnAction.CLEAN_FEED -> setCleanFeed(!s.cleanFeed)
        }
    }
    fun setFnAction(a: FnAction) { CaptureExtras.fnAction = a.name; CaptureExtras.save(context); toast("Fn = ${a.label}") }
    fun setWavAudio(on: Boolean) { if (!st.recording) { CaptureExtras.wavAudio = on; CaptureExtras.save(context) } }
    fun setNtsc(on: Boolean) { if (!st.recording) { CaptureExtras.ntsc = on; CaptureExtras.save(context) } }

    fun toggleFilterStrip() = set { it.copy(showFilters = !it.showFilters, activeParam = null) }
    fun setNdBlur(frames: Int) {
        if (st.recording) { toast("Change motion blur between recordings"); return }
        CaptureExtras.ndBlur = frames; CaptureExtras.save(context)
        ndFrames = frames; ndCount = 0
        if (frames > 1) toast("Motion blur ×$frames — best with a fast shutter in bright light")
    }
    fun setFaceBlur(on: Boolean) { CaptureExtras.faceBlur = on; CaptureExtras.save(context); if (!on) FaceBlur.clear() }
    fun refreshMonitorLut() = refreshPipes()
    fun toggleLogAssist() { set { it.copy(logAssist = !it.logAssist) }; refreshPipes() }
    fun setFilter(f: Filter) { if (st.filter == f) return; set { it.copy(filter = f) }; refreshPipes() }

    fun refreshStorage() {
        val options = Storage.options(context)
        val volume = Storage.load(context)
        set { it.copy(storageOptions = options, storageVolume = volume) }
    }
    fun setStorage(volume: String) { Storage.choose(context, StorageArea.CAPTURE, if (volume == Storage.INTERNAL) null else java.io.File(volume)); refreshStorage() }

    /** The current picture as a photo (e.g. while recording). */
    fun snapshot() {
        val bmp = grabViewfinder(8192) ?: return
        set { it.copy(shutterFlash = it.shutterFlash + 1) }
        scope.launch {
            val uri = withContext(Dispatchers.IO) { saveBitmap(bmp, stamp() + "_snap.jpg") }
            if (uri != null) { set { it.copy(lastMedia = uri) }; toast("Snapshot saved") } else toast("Couldn't save snapshot")
        }
    }

    // ---------------------------------------------------------------- remote monitor

    @Volatile private var remoteJpeg: ByteArray? = null
    private var remoteJob: kotlinx.coroutines.Job? = null
    var remoteAddress by androidx.compose.runtime.mutableStateOf<String?>(null); private set
    private val remote = RemoteMonitor(context, frame = { remoteJpeg }, status = { remoteStatus() }, command = { cmd -> scope.launch { remoteCommand(cmd) } })

    fun setRemote(on: Boolean) {
        if (!on) { remote.stop(); remoteJob?.cancel(); remoteJob = null; remoteJpeg = null; remoteAddress = null; return }
        if (!remote.start()) { toast("Couldn't start the remote monitor"); return }
        val addr = remote.address()
        if (addr == null) { remote.stop(); toast("Connect the PC to a network first"); return }
        remoteAddress = addr
        remoteJob?.cancel()
        remoteJob = scope.launch {
            while (true) {
                if (remote.viewerCount > 0 || remoteJpeg == null) {
                    val b = grabViewfinder(960)
                    if (b != null) remoteJpeg = withContext(Dispatchers.Default) { RemoteMonitor.jpeg(b) }
                }
                delay(if (remote.viewerCount > 0) 110 else 1000)
            }
        }
    }
    private fun remoteStatus(): String {
        val s = st; val t = s.recordSeconds
        fun q(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        return "{\"mode\":${q(s.mode.label)},\"video\":${s.isVideo},\"recording\":${s.recording}," +
            "\"time\":${q("%d:%02d".format(t / 60, t % 60))},\"zoom\":${q(Stops.zoomLabel(s.zoom))},\"exposure\":${q("")}}"
    }
    private fun remoteCommand(cmd: String) {
        when (cmd) {
            "shutter" -> shutter(); "zoom_in" -> setZoom(st.zoom * 1.25f); "zoom_out" -> setZoom(st.zoom / 1.25f)
            "mode_photo" -> setMode(CaptureMode.PHOTO); "mode_video" -> setMode(CaptureMode.VIDEO); "mode_pro_video" -> setMode(CaptureMode.PRO_VIDEO)
            "af" -> setFocusAuto(); "ae_lock" -> toggleAeLock()
        }
    }

    // ---------------------------------------------------------------- long exposure

    private var longJob: kotlinx.coroutines.Job? = null
    @Volatile private var longStop = false
    private val _longPreview = MutableStateFlow<Bitmap?>(null)
    val longPreview: StateFlow<Bitmap?> = _longPreview.asStateFlow()
    fun setLongKind(k: Int) { CaptureExtras.longKind = k.coerceIn(0, LongKind.entries.size - 1); CaptureExtras.save(context) }
    fun setLongSec(sec: Int) { CaptureExtras.longSec = sec; CaptureExtras.save(context) }

    private fun startLongExposure() {
        val s = st
        if (s.capturing || !s.ready) return
        val kind = LongKind.entries[CaptureExtras.longKind.coerceIn(0, LongKind.entries.size - 1)]
        val totalMs = CaptureExtras.longSec * 1000L
        longStop = false
        longJob = scope.launch {
            countdown(s.timerSec)
            val first = grabViewfinder(4096) ?: run { toast("Couldn't read the camera picture"); return@launch }
            val stacker = LongStacker(first.width, first.height, kind)
            set { it.copy(capturing = true, longProgress = 0f) }
            playSound(MediaActionSound.SHUTTER_CLICK)
            val t0 = System.currentTimeMillis()
            var lastPreview = 0L
            var frame: Bitmap? = first
            try {
                while (!longStop) {
                    val el = System.currentTimeMillis() - t0
                    if (el >= totalMs) break
                    val f = frame ?: grabViewfinder(4096)
                    frame = null
                    if (f != null && f.width == stacker.width) withContext(Dispatchers.Default) { stacker.add(f) }
                    if (el - lastPreview > 600) { lastPreview = el; _longPreview.value = withContext(Dispatchers.Default) { stacker.result(3) } }
                    set { it.copy(longProgress = (el.toFloat() / totalMs).coerceIn(0f, 1f)) }
                    delay(25)
                }
                set { it.copy(longProgress = 1f) }
                val uri = withContext(Dispatchers.Default) { stacker.result()?.let { saveBitmap(it, stamp() + "_long_" + kind.short.lowercase(Locale.US) + ".jpg") } }
                playSound(MediaActionSound.SHUTTER_CLICK)
                set { it.copy(lastMedia = uri ?: it.lastMedia) }
                toast(if (uri != null) "${kind.label} saved (${stacker.frames} frames)" else "Couldn't save the long exposure")
            } finally {
                _longPreview.value = null
                set { it.copy(capturing = false, longProgress = -1f) }
            }
        }
    }

    // ---------------------------------------------------------------- photos

    private fun stamp(): String = "PRO_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    private fun saveBitmap(bmp: Bitmap, fileName: String): Uri? {
        val values = ContentValues().apply { put("_display_name", fileName); put("mime_type", "image/jpeg") }
        val uri = Storage.insert(context, Storage.load(context), values, Storage.Kind.IMAGE) ?: return null
        return try {
            context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            uri
        } catch (_: Throwable) { Storage.discard(context, uri); null }
    }

    /** One photo: the camera's full picture (filter applied), with the camera details in the file. */
    private suspend fun takePhoto(): Boolean {
        val flashed = flashUp()
        val raw = photoSrc?.let { ps -> Triple(IntArray(ps.size).also { ColorPipe(st.filter, 0, null).apply(ps, it, ps.size) }, latestW, latestH) }
            ?: synchronized(latestLock) { latest?.let { Triple(it.copyOf(), latestW, latestH) } } ?: run { if (flashed) flashDown(); return false }
        if (flashed) flashDown()
        playSound(MediaActionSound.SHUTTER_CLICK)
        set { it.copy(shutterFlash = it.shutterFlash + 1) }
        val s = st
        val name = stamp() + ".jpg"
        val uri = withContext(Dispatchers.IO) {
            var (px, w, h) = raw
            // Square (1:1) and 16:9 / 4:3 framing for photos.
            val want = when (s.aspect) { AspectMode.R1_1 -> 1f; AspectMode.R16_9 -> 16f / 9f; else -> 4f / 3f }
            var bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
            val have = w.toFloat() / h
            if (abs(have - want) > 0.02f) {
                val cw = if (have > want) (h * want).toInt() else w; val ch = if (have > want) h else (w / want).toInt()
                bmp = Bitmap.createBitmap(bmp, (w - cw) / 2, (h - ch) / 2, cw, ch)
            }
            val u = saveBitmap(bmp, name) ?: return@withContext null
            try {
                val f = java.io.File(u.path!!)
                val ex = androidx.exifinterface.media.ExifInterface(f.path)
                val now = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date())
                ex.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL, now)
                ex.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME, now)
                ex.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL, s.lens?.label ?: "PC camera")
                ex.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_SOFTWARE, "AlcorCut Pro")
                if (s.isPro && s.manualShutter) ex.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME, (s.shutterNs / 1e9).toString())
                CaptureLocation.current(context)?.let { l -> ex.setLatLong(l.latitude, l.longitude) }
                ex.saveAttributes()
            } catch (_: Throwable) {}
            u
        } ?: run { toast("Couldn't save the photo"); return false }
        val portrait = s.mode.isPortrait
        io.execute { Copyright.stampUri(context, uri) }
        if (CaptureExtras.slateOn) { val snap = st; io.execute { Slate.stampPhoto(context, uri); Slate.log(context, uri, snap, false) } }
        if (portrait) {
            set { it.copy(processing = true) }
            val f = if (CaptureExtras.portraitAuto) Portrait.AUTO else CaptureExtras.portraitF; val keep = CaptureExtras.portraitKeepOriginal
            io.execute {
                val out = try { Portrait.applyToSaved(context, uri, f, keep) } catch (_: Throwable) { null }
                set { it.copy(processing = false, lastMedia = out?.first ?: it.lastMedia) }
                toast(if (out != null) "Portrait saved (${if (f <= 0f) "auto " else ""}${Portrait.label(out.second)})" else "No person or subject found — saved without background blur")
            }
        }
        if (CaptureExtras.autoEnhance) io.execute { com.dronedirector.veyrocutpro.editor.AutoEnhance.saveCopy(context, uri) }
        val aiMp = if (UpscaleSettings.enabled) s.photoSize?.aiMp ?: 0 else 0
        if (aiMp > 0) io.execute { Upscaler.enqueue(context, uri, aiMp) }
        set { it.copy(lastMedia = uri) }
        return true
    }

    // ---------------------------------------------------------------- burst

    private var holdBurstJob: kotlinx.coroutines.Job? = null
    fun setBurstModeCount(n: Int) = set { it.copy(burstModeCount = n) }
    fun startHoldBurst() {
        if (fwd("shutter")) return
        if (st.mode != CaptureMode.BURST || st.capturing || holdBurstJob != null) return
        set { it.copy(holdBursting = true, capturing = true) }
        holdBurstJob = scope.launch {
            var n = 0
            while (st.holdBursting && n < 100) { if (takePhoto()) n++; delay(60) }
            set { it.copy(holdBursting = false, capturing = false) }
            holdBurstJob = null
            toast("$n photos saved")
        }
    }
    fun stopHoldBurst() = set { it.copy(holdBursting = false) }
    fun cancelCountdown() { captureJob?.cancel(); captureJob = null; set { it.copy(countdown = 0, capturing = false) } }

    // ---------------------------------------------------------------- time-lapse, slow motion

    fun setLapseInterval(ms: Int) = set { it.copy(lapseIntervalMs = ms) }
    fun setLapseLimit(min: Int) = set { it.copy(lapseLimitMin = min) }
    fun setHyperSpeed(x: Int) = set { it.copy(hyperSpeed = x) }
    fun toggleLapseStabilize() { set { it.copy(lapseStabilize = !it.lapseStabilize) } }
    fun setSlowFps(fps: Int) { if (st.recording || st.slowFps == fps) return; set { it.copy(slowFps = fps) }; bind() }

    // ---------------------------------------------------------------- recording

    private fun bitrate(s: CameraUiState, w: Int, h: Int, fps: Float): Int {
        val base = when { h >= 2000 -> 50_000_000; h >= 1000 -> 20_000_000; h >= 700 -> 10_000_000; else -> 5_000_000 }
        val tier = listOf(0.5, 1.0, 1.6, 2.5)[CaptureExtras.bitrateTier.coerceIn(0, 3)]
        return (base * tier * (fps / 30.0).coerceIn(1.0, 4.0) * (if (s.videoCodec == VideoCodec.HEVC) 0.7 else 1.0)).toInt()
    }

    private fun warnBeforeRecording() {
        try {
            val dir = Storage.folder(context, StorageArea.CAPTURE, Storage.Kind.VIDEO)
            val minutes = (dir.usableSpace * 8.0 / (bitrate(st, 1920, 1080, st.fps.toFloat()).toDouble()) / 60.0).toInt()
            val battery = android.os.BatteryManager.battery()
            val notes = buildList {
                if (minutes < 15) add("about $minutes min of ${st.videoRes.label} left on this drive")
                if (battery in 1..14) add("battery at $battery %")
            }
            if (notes.isNotEmpty()) toast("Heads up: " + notes.joinToString(", "))
        } catch (_: Throwable) {}
    }

    private fun startRecording() {
        val c = cam ?: return
        val s = st
        if (c.width <= 0) { toast("The camera isn't ready"); return }
        warnBeforeRecording()
        val lapse = s.mode.isLapse
        val slow = s.mode.isSlowMo
        val suffix = when { s.mode == CaptureMode.TIMELAPSE -> "_timelapse"; s.mode == CaptureMode.HYPERLAPSE -> "_hyperlapse"; slow -> "_slowmo"; else -> "" }
        val values = ContentValues().apply { put("_display_name", clipName(suffix) + ".mp4"); put("mime_type", "video/mp4") }
        val uri = Storage.insert(context, Storage.load(context), values, Storage.Kind.VIDEO) ?: run { toast("Couldn't create the video file"); return }
        val file = java.io.File(uri.path!!)
        val fps = c.current?.fps ?: s.fps.toFloat()
        val withAudio = s.mode.isNormalVideo && s.audio
        // Record in the frame guide's shape: the middle of the picture, cut to the guide.
        val g = s.guide.ratio
        val have = c.width.toFloat() / c.height
        val (w, h) = if (CaptureExtras.recordAspect && g > 0f && abs(g - have) > 0.01f) {
            if (g > have) c.width to (c.width / g).toInt() else (c.height * g).toInt() to c.height
        } else c.width to c.height
        val rw = w and 1.inv(); val rh = h and 1.inv()
        cropX = (c.width - rw) / 2; cropY = (c.height - rh) / 2
        if (!monitorWhileRecording) audioMeter.stopMonitoring()
        val rec = try {
            WinRecorder(file, rw, rh, fps, bitrate(s, rw, rh, if (lapse) 30f else fps), s.videoCodec == VideoCodec.HEVC, withAudio,
                { st.audioGainDb }, { st.audioLimiter }, { rms, l, r -> if (!monitorWhileRecording) audioMeter.pushProcessed(rms); set { it.copy(audioLevel = rms) } },
                timing = if (lapse) 1 else if (slow) 2 else 0, wav = withAudio && CaptureExtras.wavAudio, windFilter = CaptureExtras.windFilter,
                noiseReduce = if (withAudio) CaptureExtras.noiseReduce else 0, ntsc = CaptureExtras.ntsc && !lapse && !slow,
                pre = preRoll?.let { p -> preRoll = null; p.take().takeIf { t -> t.width == rw && t.height == rh && s.mode.isNormalVideo } })
        } catch (t: Throwable) {
            // No HEVC encoder on this PC: H.264 instead.
            if (s.videoCodec == VideoCodec.HEVC) {
                toast("No HEVC encoder on this PC — recording H.264")
                set { it.copy(videoCodec = VideoCodec.H264) }
                prefs.edit().putString("video_codec", VideoCodec.H264.name).apply()
                file.delete()
                startRecording()
                return
            }
            file.delete(); toast("Couldn't start recording: ${t.message}"); return
        }
        recorder = rec; recordFile = file; lapseLast = 0L; ndCount = 0; stabilizer.reset()
        recordStartWall = System.currentTimeMillis()
        playSound(MediaActionSound.START_VIDEO_RECORDING)
        set { it.copy(recording = true, paused = false, recordSeconds = 0, lapseFrames = 0) }
        recordJob = scope.launch {
            while (st.recording) {
                delay(250)
                val r = recorder ?: break
                set { it.copy(recordSeconds = r.seconds) }
                val limit = st.lapseLimitMin
                if (lapse && limit > 0 && r.seconds >= limit * 60L) { stopRecording(); break }
                r.error?.let { e -> toast(e) }
            }
        }
    }

    private fun stopRecording() {
        val r = recorder ?: return
        recorder = null
        recordJob?.cancel()
        playSound(MediaActionSound.STOP_VIDEO_RECORDING)
        val s0 = st
        set { it.copy(recording = false, paused = false, processing = true, audioLevel = 0f) }
        val startWall = recordStartWall
        scope.launch {
            val out = withContext(Dispatchers.IO) { r.stop() }
            val uri = out?.let { Uri.fromFile(it) }
            if (uri != null && CaptureExtras.timecode > 0 && startWall > 0) {
                val ok = withContext(Dispatchers.IO) { Timecode.add(context, uri, startWall, s0.fps) }
                if (!ok) toast("Couldn't add the timecode to this clip")
            }
            if (uri != null && CaptureExtras.slateOn) { val snap = st; io.execute { Slate.log(context, uri, snap, true) } }
            if (uri != null) r.wavFile?.takeIf { it.length() > 44 }?.let { toast("WAV saved next to the clip") }
            set { it.copy(processing = false, lastMedia = uri ?: it.lastMedia) }
            when {
                uri == null -> toast("Nothing recorded — keep recording a little longer")
                s0.mode.isLapse -> toast("Saved · ${r.frames} frames → %.1f s of video".format(r.frames / 30f))
                s0.mode.isSlowMo -> toast("Slow-motion video saved (${"%.0f".format((cam?.current?.fps ?: 60f) / 30f)}× slower)")
                r.dropped > 10 -> toast("Video saved — the PC couldn't keep up with ${r.dropped} pictures; try a lower resolution or frame rate")
            }
            if (s0.mode.isNormalVideo && s0.audio) setAudioMonitoring(true)
            refreshPreRecord()
        }
    }

    fun setAudioMonitoring(on: Boolean) { if (on) audioMeter.startMonitoring() else audioMeter.stopMonitoring() }
    fun pauseResume() {
        if (fwd("pause")) return
        val r = recorder ?: return
        r.paused = !r.paused
        set { it.copy(paused = r.paused) }
    }
}

private fun lastModeAtStart(context: Context): CaptureMode {
    if (com.dronedirector.veyrocutpro.ui.SafeMode.active) return CaptureMode.PHOTO
    val m = prefEnum(context, "last_mode", CaptureMode.PHOTO)
    return if (m == CaptureMode.LIVE || m == CaptureMode.PANORAMA || m.isSlowMo || !SimpleMode.allowed(m)) CaptureMode.PHOTO else m
}

private inline fun <reified E : Enum<E>> prefEnum(context: Context, key: String, default: E): E {
    val name = context.getSharedPreferences("procam", Context.MODE_PRIVATE).getString(key, null) ?: return default
    return enumValues<E>().firstOrNull { it.name == name } ?: default
}
