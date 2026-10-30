package com.dronedirector.veyrocutpro.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Show / hide panels per screen.
 * Every panel is visible by default; the user's choices are saved and survive restarts.
 * Keys look like "cap.hud" or "ve.inspector".
 */
object Panels {
    private const val PREFS = "acluxpro_panels"
    private val state = mutableStateMapOf<String, Boolean>()
    private var loaded = false

    private fun ensure(c: Context) {
        if (loaded) return
        loaded = true
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (k, v) -> if (v is Boolean) state[k] = v }
    }

    fun isOn(c: Context, key: String): Boolean { ensure(c); return state[key] ?: true }

    fun set(c: Context, key: String, on: Boolean) {
        ensure(c)
        state[key] = on
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, on).apply()
    }

    /** Every panel of a screen back to visible (the original layout). */
    fun reset(c: Context, prefix: String) {
        ensure(c)
        state.keys.filter { it.startsWith("$prefix.") }.forEach { state.remove(it) }
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().apply { p.all.keys.filter { it.startsWith("$prefix.") }.forEach { remove(it) } }.apply()
    }
}

/** Whether panel [key] is shown; recomposes when it changes. */
@Composable
fun panelOn(key: String): Boolean = Panels.isOn(LocalContext.current, key)

/**
 * One entry in the Panels menu. Most panels are stored in [Panels] under [key]; a panel that mirrors
 * an existing setting (e.g. the capture histogram) passes [get] / [put] instead.
 */
class PanelDef(
    val key: String,
    val label: String,
    val detail: String,
    val get: (() -> Boolean)? = null,
    val put: ((Boolean) -> Unit)? = null,
)

/** A saved arrangement: the panels in [on] are shown, every other panel is hidden. */
class PanelPreset(val name: String, val on: Set<String>, val onApply: (() -> Unit)? = null)

/**
 * The layout icon that sits next to the settings gear.
 * Tap: open the Panels menu (presets first, then a switch per panel).
 * Triple-tap: put the whole layout back to how it was originally ([onReset]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelsButton(
    prefix: String,
    panels: List<PanelDef>,
    presets: List<PanelPreset>,
    onReset: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 22.dp,
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Job?>(null) }
    val reset by rememberUpdatedState(onReset)
    val tablet = LocalConfiguration.current.smallestScreenWidthDp >= 600

    fun isOn(p: PanelDef) = p.get?.invoke() ?: Panels.isOn(ctx, p.key)
    fun put(p: PanelDef, on: Boolean) { if (p.put != null) { if (isOn(p) != on) p.put.invoke(on) } else Panels.set(ctx, p.key, on) }
    fun fullReset() {
        Panels.reset(ctx, prefix)
        panels.forEach { if (it.put != null && !isOn(it)) it.put.invoke(true) }
        reset()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Box {
        Box(
            Modifier
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        // Wait briefly so a triple-tap isn't taken as "open the menu".
                        taps++
                        pending?.cancel()
                        if (taps >= 3) { taps = 0; open = false; fullReset() }
                        else pending = scope.launch { delay(320); taps = 0; open = true }
                    })
                }
                .padding(10.dp),
        ) {
            Icon(Icons.Filled.Dashboard, contentDescription = "Panels", tint = Color.White, modifier = Modifier.size(size))
        }

        val content = @Composable {
            PanelsMenu(
                panels = panels, presets = presets,
                isOn = ::isOn, put = ::put,
                onReset = { fullReset(); open = false },
            )
        }
        if (tablet) {
            ScrollMenu(open, onDismissRequest = { open = false }, width = 320.dp) { content() }
        } else if (open) {
            ModalBottomSheet(
                onDismissRequest = { open = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = panelHere(),
            ) { Box(Modifier.navigationBarsPadding()) { content() } }
        }
    }
}

@Composable
private fun PanelsMenu(
    panels: List<PanelDef>,
    presets: List<PanelPreset>,
    isOn: (PanelDef) -> Boolean,
    put: (PanelDef, Boolean) -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("PANELS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp))
        Text("Hide what you don't need — the rest of the screen grows into the space.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

        if (presets.isNotEmpty()) {
            val current = presets.firstOrNull { pr -> panels.all { isOn(it) == (it.key in pr.on) } }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { pr ->
                    val sel = pr === current
                    Text(
                        pr.name.uppercase(),
                        style = Pro.Label.copy(color = if (sel) Color.Black else Color.White, fontSize = 11.sp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (sel) Pro.Amber else fgHere().copy(alpha = 0.1f))
                            .clickable { panels.forEach { put(it, it.key in pr.on) }; pr.onApply?.invoke() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        panels.forEach { p ->
            val on = isOn(p)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { put(p, !on) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.label, color = Color.White, fontSize = 14.sp)
                    Text(p.detail, color = Pro.Dim, fontSize = 11.sp)
                }
                Switch(
                    checked = on, onCheckedChange = { put(p, it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Pro.Amber),
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tip: triple-tap the panels icon to reset", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(
                "RESET LAYOUT",
                style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onReset).padding(10.dp),
            )
        }
    }
}
