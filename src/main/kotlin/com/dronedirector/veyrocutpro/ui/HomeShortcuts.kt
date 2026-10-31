package com.dronedirector.veyrocutpro.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dronedirector.veyrocutpro.MainActivity
import com.dronedirector.veyrocutpro.R

/**
 * Home-screen shortcuts: separate icons on the phone's home screen that open Capture, the Photo
 * editor or the Video editor directly. Off until turned on in the start screen's settings (gear).
 */
object HomeShortcuts {
    class Target(val id: String, val label: String, val screen: String)
    val TARGETS = listOf(
        Target("home_capture", "AC Capture", "capture"),
        Target("home_photo", "AC Photo editor", "photo"),
        Target("home_video", "AC Video editor", "video"),
    )

    private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)
    fun enabled(c: Context): Boolean = prefs(c).getBoolean("home_shortcuts", false)

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("home_shortcuts", on).apply()
        val ids = TARGETS.map { it.id }
        try {
            if (on) ShortcutManagerCompat.enableShortcuts(c, TARGETS.map { info(c, it) })
            else ShortcutManagerCompat.disableShortcuts(c, ids, "Turn on home-screen shortcuts in AlcorCut Pro (start screen → gear)")
        } catch (_: Throwable) {}
    }

    private fun info(c: Context, t: Target): ShortcutInfoCompat = ShortcutInfoCompat.Builder(c, t.id)
        .setShortLabel(t.label)
        .setLongLabel(t.label)
        .setIcon(IconCompat.createWithResource(c, R.mipmap.ic_launcher))
        .setIntent(
            Intent(c, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("screen", t.screen)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        .build()

    /** Brings icons already on the home screen up to date (e.g. after the app was renamed). */
    fun refresh(c: Context) {
        if (!enabled(c)) return
        try { ShortcutManagerCompat.updateShortcuts(c, TARGETS.map { info(c, it) }) } catch (_: Throwable) {}
    }

    /** Asks the home screen to add the icon. Returns false if the home screen can't add icons. */
    fun pin(c: Context, t: Target): Boolean = try {
        ShortcutManagerCompat.isRequestPinShortcutSupported(c) && ShortcutManagerCompat.requestPinShortcut(c, info(c, t), null)
    } catch (_: Throwable) { false }
}

/** The start screen's settings (gear): home-screen shortcuts. */
@Composable
fun AppSettingsDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(HomeShortcuts.enabled(ctx)) }
    var note by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.widthIn(max = 460.dp).clip(RoundedCornerShape(18.dp)).background(AppTheme.pick(Color(0xFF15171C), Color(0xFFF4F5F7)))
                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("SETTINGS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 13.sp, letterSpacing = 2.sp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Home-screen shortcuts", color = Color.White, fontSize = 15.sp)
                    Text("Put separate icons for Capture, the Photo editor or the Video editor on your phone's home screen — each opens straight into that part of the app.", color = Pro.Dim, fontSize = 12.sp)
                }
                Switch(
                    checked = on,
                    onCheckedChange = { on = it; HomeShortcuts.setEnabled(ctx, it); note = null },
                    colors = SwitchDefaults.colors(checkedTrackColor = Pro.Amber),
                )
            }
            if (on) {
                for (t in HomeShortcuts.TARGETS) {
                    Text(
                        "ADD “${t.label}” TO HOME SCREEN",
                        style = Pro.Label.copy(color = Pro.Amber, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.06f))
                            .clickable { note = if (HomeShortcuts.pin(ctx, t)) "Confirm on the home screen to place the icon" else "Your home screen doesn't let apps add icons — long-press the app icon instead" }
                            .padding(12.dp),
                    )
                }
            }
            note?.let { Text(it, color = Pro.Dim, fontSize = 12.sp) }
            // The "What's new" card from the last update, any time (e.g. if it was closed by mistake).
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.06f))
                    .clickable { onClose(); WhatsNew.show(NewsArea.ALL) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("What's new", color = Color.White, fontSize = 15.sp)
                    Text("The latest changes in the last three versions, for the whole app", color = Pro.Dim, fontSize = 12.sp)
                }
                Text("→", color = Pro.Amber, fontSize = 18.sp)
            }
            // Updates for the whole app, and which parts' updates notify you.
            UpdatesSection(NewsArea.ALL, onClose)
            UpdateChoices()
            Text(
                "CLOSE", style = Pro.Label.copy(color = Color.White, fontSize = 12.sp),
                modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(10.dp),
            )
        }
    }
}
