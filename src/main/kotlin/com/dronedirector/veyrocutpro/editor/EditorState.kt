package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The video editor workspace's simple UI state, kept in one object instead of separate locals so the
 * workspace function stays under Android's method-size limit.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class EditorState(initial: VideoProjectData?, context: Context) {
    /** Reloading the clip on screen so new picture effects (clarity, film looks, privacy blur) show. */
    var fxJob: kotlinx.coroutines.Job? = null
    /** The next flight-data (.SRT) file picked becomes a flight path mini-map instead of read-out text. */
    var srtForMap = false
    var page by mutableStateOf(Page.EDIT)
    var colorTab by mutableStateOf(ColorTab.INPUT)
    var selected by mutableStateOf(0)
    var playing by mutableStateOf(false)
    var posMs by mutableStateOf(0L)  // position inside the current clip (source time)
    var current by mutableStateOf(0)
    var exporting by mutableStateOf(false)
    var progress by mutableStateOf(0)
    var message by mutableStateOf<String?>(null)
    var trackingId by mutableStateOf<String?>(null)
    var trackStop by mutableStateOf(false)
    var deliver by mutableStateOf(initial?.deliver ?: DeliverSettings())
    var soloTrack by mutableStateOf<String?>(null)
    var projectId by mutableStateOf(initial?.id?.takeIf { initial.autosave != true })
    var projectName by mutableStateOf(initial?.name ?: "")
    var askName by mutableStateOf(false)
    var leaveAfterSave by mutableStateOf(false)
    var showLoad by mutableStateOf(false)
    var export by mutableStateOf<TimelineExport?>(null)
    var confirmExit by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    /** File → Project settings is open. */
    var projectSettings by mutableStateOf(false)
    var cloudExport by mutableStateOf(false)
    var cloudJob by mutableStateOf<kotlinx.coroutines.Job?>(null)
    var previewMuted by mutableStateOf(false)
    /** Loop playback: the in–out range (or the whole timeline) plays over and over. */
    var loop by mutableStateOf(false)
    /** Goes up with every jump of the playhead made on purpose (seek, loop): the music follows such jumps at once. */
    var seekCount = 0
    var videoMuted by mutableStateOf(initial?.videoMuted ?: false)
    var videoLocked by mutableStateOf(initial?.videoLocked ?: false)
    var videoHidden by mutableStateOf(initial?.videoHidden ?: false)
    /** The + list of the button bar is open. */
    var editBar by mutableStateOf(false)
    /** The safe margins last shown (the bar's button switches them on and off). */
    var lastSafe by mutableStateOf(0)
    /** Where the magnified preview is looked at (pixels). */
    var magX by mutableStateOf(0f)
    var magY by mutableStateOf(0f)
    var archiveAction by mutableStateOf(0)
    var archiveBusy by mutableStateOf<Int?>(null)
    var snapping by mutableStateOf(context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).getBoolean("tl_snap", true))
    var scopeHist by mutableStateOf<Array<FloatArray>?>(null)
    var scopeFrame by mutableStateOf<Bitmap?>(null)
    var scopeOpen by mutableStateOf(scopeDrawerOpen(context))
    var exportStartAt by mutableStateOf(0L)
    var paneVersion by mutableStateOf(0)
    var adjNowMs by mutableStateOf(0L)
    var selectedLayer by mutableStateOf<String?>(null)
    var music by mutableStateOf(initial?.music)
    var timelineName by mutableStateOf(initial?.timelineName ?: "Timeline 1")
    var libLoading by mutableStateOf(0)
    var showMulticam by mutableStateOf(false)
    var telemetryShow by mutableStateOf(TelemetryShow())
    var queueAt by mutableStateOf(-1)  // next job to start; -1 = not running
    var queueBase by mutableStateOf<DeliverSettings?>(null)
    var askVideoLayer by mutableStateOf(false)
    var videoLayerBelow by mutableStateOf(false)
    var effectsOk by mutableStateOf(true)
    var proxySettings by mutableStateOf(ProxySettings.load(context))
    var proxyCancel by mutableStateOf<(() -> Unit)?>(null)
    var proxyRun by mutableStateOf(0)  // bumps to restart the worker
    var showInfo by mutableStateOf(context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).getBoolean("ve_info", false))
    var wheelFx by mutableStateOf(context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).getBoolean("tl_wheel_fx", true))
    /** Markers, keyframes and in / out flags with the animated shine (gear → Layout & look). */
    var markerFx by mutableStateOf(context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).getBoolean("tl_marker_fx", true))
    /** Shortening a main-video clip: false (normal) = a black gap is left; true = the gap closes and everything after moves up (all tracks). */
    var trimRipple by mutableStateOf(context.getSharedPreferences("procam", android.content.Context.MODE_PRIVATE).getBoolean("tl_trim_ripple", false))
    /** A gap being made after a trim: clips from this index on are drawn this much later until it's in. */
    /** Tools bar → Reveal in library: the file to pick out in the media strip, and a counter to do it again. */
    var revealUri by mutableStateOf<String?>(null)
    var revealTick by mutableStateOf(0)
    var gapHold by mutableStateOf<Pair<String, Long>?>(null)
    var infoText by mutableStateOf("")
    var pendingRestore by mutableStateOf<java.io.File?>(null)
    var loadedId by mutableStateOf<String?>(null)
    var cropMode by mutableStateOf(false)
    var cropEditEnd by mutableStateOf(false)
    var errorRetries by mutableStateOf(0)
    var preloadedId by mutableStateOf<String?>(null)
    var abSplit by mutableStateOf(0f)
    var morphFrame by mutableStateOf<Bitmap?>(null)
    var morphBusy by mutableStateOf(false)
    var nesting by mutableStateOf(false)
    var translateStatus by mutableStateOf<String?>(null)
    var pendingWordCaps by mutableStateOf<Int?>(null)
    var sceneJob by mutableStateOf<kotlinx.coroutines.Job?>(null)
    var sceneProgress by mutableStateOf(-1)
    var matchBusy by mutableStateOf<Int?>(null)
    var deleteAsk by mutableStateOf<Int?>(null)
    var genBusy by mutableStateOf<Int?>(null)
    var genCancel by mutableStateOf<(() -> Unit)?>(null)
    var sourceClip by mutableStateOf<Clip?>(null)
    var tlBounds by mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
    var relinkFrom by mutableStateOf<String?>(null)
    var eraseProgress by mutableStateOf<Int?>(null)
    var eraseStatus by mutableStateOf<String?>(null)
    var eraseJob by mutableStateOf<kotlinx.coroutines.Job?>(null)
    var magicPick by mutableStateOf<MagicTarget?>(null)
    var hlProgress by mutableStateOf(-1)
    var hlJob by mutableStateOf<kotlinx.coroutines.Job?>(null)
    var histPos by mutableStateOf(-1)
    var lastScrub by mutableStateOf(0L)
    var wasExporting by mutableStateOf(false)
}
