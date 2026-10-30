package com.dronedirector.veyrocutpro.editor

import androidx.compose.foundation.combinedClickable
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import com.dronedirector.veyrocutpro.ui.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.hintedScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** LOAD / SAVE buttons that sit next to EXPORT. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun BarButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    Text(
        label, style = Pro.Value.copy(color = if (enabled) Pro.Amber else Pro.Dim, fontSize = 11.sp),
        modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(50)).background(Ed.Raised).combinedClickable(enabled = enabled, onClick = onClick, onLongClick = { com.dronedirector.veyrocutpro.ui.HelpTip.show(label, view) })
            .padding(horizontal = 9.dp, vertical = 7.dp),
    )
}

/** LOAD: pick a saved project to open (replaces what is open now). */
@Composable
fun LoadProjectDialog(video: Boolean, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val list by produceState<List<ProjectEntry>?>(null) { value = withContext(Dispatchers.IO) { ProjectStore.list(context, video) } }
    val autos by produceState<List<ProjectEntry>>(emptyList()) { value = withContext(Dispatchers.IO) { ProjectStore.list(context, video, autosaves = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pro.PanelSolid,
        title = { Text("Load project", color = Color.White) },
        text = {
            Column(Modifier.androidxVerticalScroll()) {
                val l = list
                if (autos.isNotEmpty()) {
                    Text("AUTOSAVES", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(bottom = 4.dp))
                    autos.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onOpen(p.id) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text("YOUR PROJECTS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                }
                when {
                    l == null -> Text("Loading…", color = Pro.Dim)
                    l.isEmpty() -> Text("No saved projects yet. Use SAVE to keep your edit.", color = Pro.Dim)
                    else -> {
                        Text("Unsaved changes in the open edit will be lost.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        l.forEach { p ->
                            val thumb by produceState<ImageBitmap?>(null, p.thumb) {
                                value = withContext(Dispatchers.IO) { p.thumb?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).clickable { onOpen(p.id) }.padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(64.dp, 40.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black)) {
                                    thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                }
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(p.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(p.detail + " · " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(p.savedAt)), style = Pro.Label.copy(fontSize = 10.sp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Pro.Amber) } },
    )
}

@Composable
private fun Modifier.androidxVerticalScroll(): Modifier = this.then(Modifier.verticalScroll(rememberScrollState()))

/** Asks for a project name the first time a project is saved. */
@Composable
fun ProjectNameDialog(
    initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit,
    title: String = "Save project",
    info: String = "Saves your edit so you can open it again later and continue where you left off. Your original media is not copied.",
    fieldLabel: String = "Project name",
    /** Show the saved projects of this editor (true video, false photo) so one can be overwritten; null = name only. */
    video: Boolean? = null,
    /** The project open now (preselected), if it was saved before. */
    currentId: String? = null,
    /** Overwrite saved project [id] with this edit, named [name]. */
    onOverwrite: ((id: String, name: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial) }
    val saved by produceState(emptyList<ProjectEntry>(), video) { value = if (video == null) emptyList() else withContext(Dispatchers.IO) { ProjectStore.list(context, video) } }
    var pick by remember { mutableStateOf(currentId) }
    var confirm by remember { mutableStateOf<ProjectEntry?>(null) }
    confirm?.let { p ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Pro.PanelSolid,
            title = { Text("Overwrite \"${p.name}\"?", color = Color.White) },
            text = { Text("The saved project is replaced with this edit. This can't be undone.", color = Pro.Dim) },
            confirmButton = { TextButton(onClick = { confirm = null; onOverwrite?.invoke(p.id, name.ifBlank { p.name }) }) { Text("Overwrite", color = Pro.Red) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = Color.White) } },
        )
    }
    val chosen = saved.firstOrNull { it.id == pick }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pro.PanelSolid,
        title = { Text(title, color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(info, color = Pro.Dim, fontSize = 12.sp)
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                    label = { Text(fieldLabel) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg, unfocusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg,
                        focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber,
                    ),
                )
                if (onOverwrite != null && saved.isNotEmpty()) {
                    Text("OR OVERWRITE A SAVED PROJECT", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    saved.forEach { p ->
                        val thumb by produceState<ImageBitmap?>(null, p.thumb, p.savedAt) {
                            value = withContext(Dispatchers.IO) { p.thumb?.let { f -> BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() } }
                        }
                        val on = p.id == pick
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (on) Pro.Amber.copy(alpha = 0.18f) else Ed.Raised)
                                .clickable { if (on) pick = null else { pick = p.id; name = p.name } }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(56.dp, 38.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black)) {
                                thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            }
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(p.name + if (p.id == currentId) "  (open now)" else "", color = if (on) Pro.Amber else Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.detail + " · " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(p.savedAt)), style = Pro.Label.copy(fontSize = 10.sp), maxLines = 1)
                            }
                            if (on) Text("✓", color = Pro.Amber, fontSize = 16.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (chosen != null && onOverwrite != null) TextButton(onClick = { confirm = chosen }) { Text("Overwrite", color = Pro.Red) }
                TextButton(onClick = { onSave(name.ifBlank { initial }) }) { Text(if (onOverwrite != null) "Save as new" else "Save", color = Pro.Amber) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )
}

/** Saved projects on an editor's start page; tap to open, ✕ to delete (asks first). */
@Composable
fun SavedProjects(video: Boolean, onOpen: (String) -> Unit, top: androidx.compose.ui.unit.Dp = 18.dp) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val list by produceState(emptyList<ProjectEntry>(), version) { value = withContext(Dispatchers.IO) { ProjectStore.list(context, video) } }
    var confirm by remember { mutableStateOf<ProjectEntry?>(null) }
    confirm?.let { p ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Pro.PanelSolid,
            title = { Text("Delete this project?", color = Color.White) },
            text = { Text("Are you sure you want to delete \"${p.name}\"? Your photos and videos are not deleted.", color = Pro.Dim) },
            confirmButton = { TextButton(onClick = { ProjectStore.delete(context, video, p.id); confirm = null; version++ }) { Text("Yes", color = Pro.Red) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("No", color = Pro.Amber) } },
        )
    }
    if (list.isEmpty()) return
    Text("RECENT PROJECTS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = top, bottom = 6.dp))
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp).hintedScroll(horizontal = true), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { p ->
            val thumb by produceState<ImageBitmap?>(null, p.thumb, p.savedAt) {
                value = withContext(Dispatchers.IO) { p.thumb?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
            }
            Column(Modifier.width(150.dp).clip(RoundedCornerShape(10.dp)).background(Ed.Raised).clickable { onOpen(p.id) }) {
                com.dronedirector.veyrocutpro.ui.ForceDark { Box(Modifier.fillMaxWidth().height(84.dp).background(Color.Black)) {
                    thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).clickable { confirm = p },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, contentDescription = "Delete project", tint = Color.White, modifier = Modifier.size(16.dp)) }
                } }
                Column(Modifier.padding(8.dp)) {
                    Text(p.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.detail + " · " + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(p.savedAt)), style = Pro.Label.copy(fontSize = 10.sp), maxLines = 1)
                }
            }
        }
    }
}
