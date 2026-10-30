package com.dronedirector.veyrocutpro.ui

import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
