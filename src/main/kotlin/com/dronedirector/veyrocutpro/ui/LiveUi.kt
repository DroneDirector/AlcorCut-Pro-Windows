package com.dronedirector.veyrocutpro.ui

import android.content.Intent
import android.net.Uri
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.dronedirector.veyrocutpro.camera.LivePlatform
import com.dronedirector.veyrocutpro.camera.LiveQuality
import com.dronedirector.veyrocutpro.camera.LiveState
import com.dronedirector.veyrocutpro.camera.LiveStatus
import com.dronedirector.veyrocutpro.camera.LiveStreamer

/** Brand-neutral accent colours for the platform chips (no logos). */
private fun platformColor(p: LivePlatform): Color = when (p) {
    LivePlatform.YOUTUBE -> Color(0xFFFF3D3D)
    LivePlatform.TIKTOK -> Color(0xFF25F4EE)
    LivePlatform.TWITCH -> Color(0xFFA970FF)
    LivePlatform.FACEBOOK -> Color(0xFF4C8DFF)
    LivePlatform.CUSTOM -> Pro.Amber
}

/** Camera preview for LIVE mode, rendered by the streaming engine itself. */
@Composable
fun LiveViewfinder(live: LiveStreamer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = remember { SurfaceView(context) }
    DisposableEffect(view) {
        live.attachPreview(view)
        onDispose { live.detachPreview(view) }
    }
    AndroidView(factory = { view }, modifier = modifier.fillMaxSize())
}

@Composable
fun LiveBadge(ls: LiveState, modifier: Modifier = Modifier) {
    val (dot, text) = when (ls.status) {
        LiveStatus.LIVE -> {
            val s = ls.seconds
            Pro.Red to "LIVE  %02d:%02d:%02d  ·  %.1f Mbps".format(s / 3600, (s / 60) % 60, s % 60, ls.kbps / 1000f)
        }
        LiveStatus.CONNECTING -> Pro.Amber to "CONNECTING TO ${ls.platform.label.uppercase()}…"
        LiveStatus.RECONNECTING -> Pro.Amber to "RECONNECTING…"
        LiveStatus.IDLE -> (if (ls.hasKey) Pro.Green else Pro.Dim) to
            (if (ls.hasKey) "READY · ${ls.platform.label}" else "NOT CONNECTED · ${ls.platform.label}")
    }
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
        Text(text, style = Pro.Value.copy(fontSize = 12.sp))
        if (ls.muted) Text("MIC OFF", style = Pro.Value.copy(fontSize = 11.sp, color = Pro.Red))
    }
}

/** Bottom bar in LIVE mode: pick the platform and open its connection settings. */
@Composable
fun LiveBar(ls: LiveState, live: LiveStreamer, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(max = 680.dp)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Pro.HudPanel)
                .hintedScroll(horizontal = true)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LivePlatform.entries.forEach { p ->
                val sel = ls.platform == p
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                        .border(1.dp, if (sel) platformColor(p) else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable(enabled = !ls.isBusy) { live.selectPlatform(p) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(platformColor(p)))
                    Spacer(Modifier.width(6.dp))
                    Text(p.label, style = Pro.Value.copy(fontSize = 12.sp, color = if (sel) Color.White else Pro.Dim))
                }
            }
            Text(
                if (ls.hasKey) "EDIT" else "CONNECT",
                style = Pro.Value.copy(fontSize = 12.sp, color = Color.Black),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Pro.Amber)
                    .clickable(enabled = !ls.isBusy) { live.showSetup(true) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/** Connection panel: server + stream key, orientation and quality for the chosen platform. */
@Composable
fun LiveSetupPanel(ls: LiveState, live: LiveStreamer) {
    if (!ls.showSetup) return
    val context = LocalContext.current
    var server by remember(ls.platform) { mutableStateOf(ls.server) }
    var key by remember(ls.platform) { mutableStateOf(ls.key) }
    var showKey by remember { mutableStateOf(false) }
    val accent = platformColor(ls.platform)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { live.showSetup(false) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Pro.HudPanelSolid)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .hintedScroll(horizontal = false)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(8.dp))
                Text("Go live on ${ls.platform.label}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(ls.platform.help, color = Pro.Dim, fontSize = 13.sp)
            if (ls.platform.keyPage.isNotEmpty()) {
                OutlinedButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ls.platform.keyPage)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Throwable) {
                    }
                }) { Text("Open ${ls.platform.label} to get your stream key", color = accent) }
            }

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Pro.Amber, unfocusedBorderColor = Pro.HudLine,
                focusedLabelColor = Pro.Amber, unfocusedLabelColor = Pro.Dim,
                focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Pro.Amber,
            )
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text(if (ls.platform == LivePlatform.CUSTOM) "Server URL (rtmp://, rtmps:// or srt://)" else "Server URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(if (ls.platform == LivePlatform.CUSTOM) "Stream key (optional)" else "Stream key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "HIDE" else "SHOW", color = Pro.Dim, fontSize = 11.sp) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Your key is stored only on this device. Anyone with it can stream to your channel.", color = Pro.Dim, fontSize = 11.sp)

            Text("ORIENTATION", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Landscape 16:9", !ls.vertical) { live.setVertical(false) }
                Choice("Vertical 9:16", ls.vertical) { live.setVertical(true) }
            }
            Text("CODEC", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("H.264 (works everywhere)", !ls.hevc) { live.setHevc(false) }
                Choice("H.265 / HEVC", ls.hevc) { live.setHevc(true) }
            }
            if (ls.hevc) Text("H.265 gives better quality at the same bitrate. YouTube (enhanced RTMP) and SRT servers accept it; some platforms only take H.264.", color = Pro.Dim, fontSize = 11.sp)
            Text("QUALITY", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveQuality.entries.forEach { q ->
                    Choice("${q.label} · ${q.bitrate / 1_000_000f} Mb", ls.quality == q) { live.setQuality(q) }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { live.saveConnection(server, key) },
                    enabled = server.length >= 8 && (key.isNotBlank() || ls.platform == LivePlatform.CUSTOM),
                    colors = ButtonDefaults.buttonColors(containerColor = Pro.Amber, contentColor = Color.Black),
                ) { Text("Connect") }
                TextButton(onClick = { live.showSetup(false) }) { Text("Cancel", color = Pro.Dim) }
                Spacer(Modifier.weight(1f))
                if (ls.key.isNotEmpty()) {
                    TextButton(onClick = { key = ""; live.forgetKey() }) { Text("Forget key", color = Pro.Red) }
                }
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = Pro.Value.copy(fontSize = 12.sp, color = if (selected) Color.Black else Color.White),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Pro.Amber else Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
