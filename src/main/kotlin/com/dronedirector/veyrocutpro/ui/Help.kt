package com.dronedirector.veyrocutpro.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Help (question-mark icon next to the gear): the guide and tutorials for the part of the app
 * that's open (only that part), troubleshooting and proof of purchase. Own code.
 */
@Composable
fun HelpButton(v: GuideVariant, size: androidx.compose.ui.unit.Dp = 22.dp) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf<GuideTopic?>(null) }
    var guideAll by remember { mutableStateOf(false) }
    var trouble by remember { mutableStateOf(false) }
    var purchase by remember { mutableStateOf(false) }
    var tutorials by remember { mutableStateOf(false) }
    val app = when (v) { GuideVariant.CAPTURE -> "Capture"; GuideVariant.PHOTO -> "Photo editor"; GuideVariant.VIDEO -> "Video editor" }
    Box {
        Box(Modifier.clip(CircleShape).clickable { open = true }.padding(10.dp)) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help", tint = Color.White, modifier = Modifier.size(size))
        }
        ScrollMenu(open, onDismissRequest = { open = false }, width = 280.dp) {
            @Composable
            fun Item(t: String, sub: Boolean = false, a: () -> Unit) {
                Text(t, color = Color.White, fontSize = if (sub) 13.sp else 14.sp,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = a).padding(start = if (sub) 26.dp else 14.dp, end = 14.dp, top = 9.dp, bottom = 9.dp))
            }
            Text("HELP · " + app.uppercase(), style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            Item("$app documentation") { open = false; guideAll = true }
            Item("Tutorials ▸") { tutorials = !tutorials }
            if (tutorials) {
                Item("Quick tour", sub = true) {
                    open = false
                    if (v == GuideVariant.CAPTURE) Tour.visible = true else EditorTour.show(v)
                }
                Item("Getting started", sub = true) { open = false; guide = GuideTopic.GENERAL }
                Item("Tips and tricks", sub = true) { open = false; guide = GuideTopic.TIPS }
                Item("AI on the phone", sub = true) { open = false; guide = GuideTopic.PHONE_AI }
                Item("Cloud AI: add your key", sub = true) { open = false; guide = GuideTopic.CLOUD }
            }
            Item("Troubleshooting") { open = false; trouble = true }
            HorizontalDivider(color = fgHere().copy(alpha = 0.1f))
            Item("Proof of purchase") { open = false; purchase = true }
        }
    }
    if (guideAll) GuideDialog(v) { guideAll = false }
    guide?.let { t -> GuideDialog(v, open = t) { guide = null } }
    if (trouble) TroubleshootingDialog(v) { trouble = false }
    if (purchase) ProofOfPurchaseDialog { purchase = false }
}

/** Common problems for the part of the app that's open, and tools that fix them. */
@Composable
fun TroubleshootingDialog(v: GuideVariant, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    val report = remember { CrashReport.pending(context) }
    var showLog by remember { mutableStateOf(false) }
    if (showLog) AppLogDialog { showLog = false }
    val problems: List<Pair<String, String>> = when (v) {
        GuideVariant.CAPTURE -> listOf(
            "The picture is black or frozen" to "Close other camera apps, then leave Capture and come back. If it stays black, restart the phone — another app may still hold the camera.",
            "No sound in videos" to "Check Settings → AUDIO is on and that the app may use the microphone (Check permissions below). An unplugged microphone falls back to the phone's own.",
            "Photos or videos aren't saved" to "Settings → SAVE LOCATION: pick internal storage, or check that the SD card / USB drive is still in.",
            "Pro controls are missing" to "Turn off Simple layout (gear → Layout & look).",
            "The phone gets hot" to "High frame rates, 4K and 10-bit video work the processor hard. Use 30 fps or a lower resolution for long recordings.",
        )
        GuideVariant.PHOTO -> listOf(
            "A RAW photo opens slowly" to "The first time, the full RAW is developed; the catalog keeps the result, so it opens fast after that.",
            "Export fails or is smaller than chosen" to "Very large photos with many AI tools need a lot of memory. Close other apps; the app saves at your size and says so if it had to work smaller.",
            "My edits are gone" to "Edits are kept in the catalog (gear → Catalog). File → Open recent or LOAD → AUTOSAVES has your saved and automatic copies.",
            "AI tools don't work" to "The AI models download once (they're not in the app). Check the internet connection the first time.",
        )
        GuideVariant.VIDEO -> listOf(
            "Playback stutters" to "Use the playback resolution button under the picture (1/2 or 1/4), or add heavy clips to the Proxy section.",
            "A clip shows as missing or OFFLINE" to "Its file moved or was deleted: Media → RELINK, or File → Dependencies → Link media.",
            "Export fails" to "Long or heavy edits export in parts automatically. Free some space, close other apps and try again — a problem report is saved if it fails again.",
            "No sound in the export" to "Check the speaker icons on the timeline's track headers and Deliver → Audio.",
            "The team project won't save" to "Someone saved after you or has it open — choose Save on this phone, or open it again after they close it.",
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = panelHere(),
        title = { Text("Troubleshooting", color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState())) {
                for ((q, a) in problems) {
                    Text(q, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(a, color = Pro.Dim, fontSize = 12.sp)
                }
                Text("TOOLS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                @Composable
                fun Tool(t: String, d: String, a: () -> Unit) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(fgHere().copy(alpha = 0.06f)).clickable(onClick = a).padding(10.dp)) {
                        Text(t, color = Color.White, fontSize = 13.sp)
                        Text(d, color = Pro.Dim, fontSize = 11.sp)
                    }
                }
                Tool("Check permissions", "Opens the app's page in Android's settings (camera, microphone, photos and videos, notifications)") {
                    try { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                }
                Tool("Free some space", "Clears the app's temporary files (never your photos, videos or projects)") {
                    val before = context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                    context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                    note = String.format(java.util.Locale.US, "%.0f MB freed", before / 1048576f)
                }
                Tool("Reset the screen layout", "Panels and their sizes go back to how they were at first") {
                    context.getSharedPreferences("procam_layout", Context.MODE_PRIVATE).edit().clear().apply()
                    note = "Layout reset — it takes effect when you next open the screen"
                }
                if (report != null) Tool("Share the last problem report", "Send it to support so the problem can be found") {
                    try {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report).putExtra(Intent.EXTRA_SUBJECT, "AlcorCut Pro problem report"), "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Throwable) {}
                }
                Tool("App log", "What the app did and any problems or crashes, kept on this device — view, share or save it") { showLog = true }
                Tool("Copy system info", "The phone's hardware and drivers (also under gear → System Info), for support") {
                    val txt = try { SystemInfo.asText(SystemInfo.collect(context)) } catch (_: Throwable) { "" }
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("System info", txt))
                    note = "System info copied"
                }
                note?.let { Text(it, color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Pro.Amber) } },
    )
}
