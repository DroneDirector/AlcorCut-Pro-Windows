package com.dronedirector.veyrocutpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.CloudAi

/** "Cloud AI" settings: the user's own fal.ai key, and what runs in the cloud (Topaz models). */
@Composable
fun CloudAiSettings(variant: GuideVariant) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { CloudAi.load(ctx) }
    val uri = LocalUriHandler.current
    var keyText by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf(false) }
    if (guide) GuideDialog(variant, GuideTopic.CLOUD) { guide = false }
    Text("CLOUD AI", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp))
    Text(
        "Optional. Runs Topaz Labs' professional AI models on fast cloud servers through your own fal.ai account " +
            "(you pay fal.ai per use). Photos and clips are uploaded to fal.ai for processing. The on-phone AI stays the default and the fallback.",
        color = Pro.Dim, fontSize = 12.sp,
    )
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (CloudAi.hasKey) "KEY SAVED ✓  · CHANGE" else "ADD YOUR FAL.AI KEY", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f)).clickable { showKey = !showKey }.padding(8.dp),
        )
        Text(
            "HOW TO ADD A KEY", style = Pro.Label.copy(color = Pro.Dim, fontSize = 10.sp),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { guide = true }.padding(8.dp),
        )
    }
    if (showKey) {
        OutlinedTextField(
            value = keyText, onValueChange = { keyText = it.trim() }, singleLine = true,
            label = { Text("fal.ai API key") }, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg, unfocusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg, focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SAVE KEY", style = Pro.Label.copy(color = Pro.Amber, fontSize = 10.sp),
                modifier = Modifier.clickable { if (keyText.isNotBlank()) { CloudAi.saveKey(ctx, keyText); keyText = ""; showKey = false } }.padding(8.dp))
            if (CloudAi.hasKey) Text("REMOVE KEY", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp),
                modifier = Modifier.clickable { CloudAi.saveKey(ctx, null); CloudAi.set(ctx, photos = false, video = false); showKey = false }.padding(8.dp))
        }
        Text("Stored encrypted on this phone and only sent to fal.ai.", color = Pro.Dim, fontSize = 11.sp)
    }
    if (CloudAi.hasKey) {
        CloudSwitch("Photos in the cloud", "AI upscale (Topaz Standard V2) and AI noise reduction (Topaz Denoise)", CloudAi.photos) { CloudAi.set(ctx, photos = it) }
        if (CloudAi.photos) CloudChips("Photo noise reduction", CloudAi.PhotoDenoise.entries.map { it.label }, CloudAi.photoDenoise.ordinal) {
            CloudAi.set(ctx, photoDenoise = CloudAi.PhotoDenoise.entries[it])
        }
        CloudSwitch("Video in the cloud", "AI noise reduction with Topaz Nyx, made for video — minutes instead of up to an hour", CloudAi.video) { CloudAi.set(ctx, video = it) }
        if (CloudAi.video) CloudChips("Video model", CloudAi.VideoModel.entries.map { it.label }, CloudAi.videoModel.ordinal) {
            CloudAi.set(ctx, videoModel = CloudAi.VideoModel.entries[it])
        }
        if (CloudAi.video) Text("Nyx Fast costs about $0.10 per 10 s of 1080p on fal.ai; the other Nyx models cost more for 1080p and 4K.", color = Pro.Dim, fontSize = 11.sp)
        CloudSwitch("Wi-Fi only", "Don't upload over mobile data", CloudAi.wifiOnly) { CloudAi.set(ctx, wifiOnly = it) }
    }
}

@Composable
private fun CloudSwitch(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(detail, color = Pro.Dim, fontSize = 12.sp)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
    }
}

@Composable
private fun CloudChips(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Text(title, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Text(
                o, color = if (sel) Color.Black else Color.White, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) Pro.Amber else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f)).clickable { onSelect(i) }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** "Guide & tips" button for settings menus. */
@Composable
fun GuideButton(variant: GuideVariant) {
    var open by remember { mutableStateOf(false) }
    if (open) GuideDialog(variant, onDismiss = { open = false })
    Text(
        "?  " + variant.title.uppercase(), style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
        modifier = Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.06f)).clickable { open = true }.padding(12.dp),
    )
}
