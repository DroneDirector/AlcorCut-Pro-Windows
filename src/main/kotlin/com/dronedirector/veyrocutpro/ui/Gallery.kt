package com.dronedirector.veyrocutpro.ui

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.DateFormat
import android.text.format.Formatter
import android.util.Size
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.dronedirector.veyrocutpro.camera.MediaItem
import com.dronedirector.veyrocutpro.camera.MediaLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

private enum class GalleryTab(val label: String) { ALL("All"), PHOTOS("Photos"), VIDEOS("Videos"), RAW("RAW"), FAVORITES("♥"), TRASH("Trash") }

/** Android 11+ has a system trash: deleted items are kept 30 days and can be restored. */
private val hasTrash: Boolean get() = Build.VERSION.SDK_INT >= 30

/**
 * In-app gallery of everything AlcorCut Pro saved: a grid with All / Photos / Videos tabs and a
 * full-screen swipe viewer (pinch / double-tap zoom, video playback, share, delete, open in another app).
 */
@Composable
fun GalleryScreen(onClose: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<MediaItem>?>(null) }
    var tab by remember { mutableStateOf(GalleryTab.ALL) }
    var viewing by remember { mutableStateOf<Int?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<MediaItem?>(null) }
    var confirmItem by remember { mutableStateOf<MediaItem?>(null) }
    var favorites by remember { mutableStateOf(com.dronedirector.veyrocutpro.camera.Favorites.all(context)) }
    var grading by remember { mutableStateOf<MediaItem?>(null) }
    // Culling: star ratings, pick / reject flags and a filter.
    var marks by remember { mutableStateOf(com.dronedirector.veyrocutpro.camera.Ratings.all(context)) }
    var rateFilter by remember { mutableIntStateOf(0) }
    var confirmRejects by remember { mutableStateOf(false) }
    // Selecting several photos / videos (hold one to start): share, delete, favourite or mark them together.
    var selecting by remember { mutableStateOf(false) }
    val picked = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var confirmPicked by remember { mutableStateOf(false) }
    fun mark(item: MediaItem, m: com.dronedirector.veyrocutpro.camera.Ratings.Mark) {
        com.dronedirector.veyrocutpro.camera.Ratings.set(context, item.uri, m)
        marks = com.dronedirector.veyrocutpro.camera.Ratings.all(context)
    }

    fun reload() { reloadKey++ }

    // Reading other files in the folder (e.g. after a reinstall) needs media read access.
    val readPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { reload() }
    LaunchedEffect(Unit) {
        val missing = readPermissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) permLauncher.launch(readPermissions)
    }

    var trash by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var trashPick by remember { mutableStateOf<MediaItem?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    LaunchedEffect(reloadKey) {
        items = withContext(Dispatchers.IO) { MediaLibrary.query(context) }
        trash = withContext(Dispatchers.IO) { MediaLibrary.queryTrash(context) }
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            // On Android 10 the system only grants permission; retry the delete now.
            if (Build.VERSION.SDK_INT == 29) pendingDelete?.let { runCatching { context.contentResolver.delete(it.uri, null, null) } }
            onChanged()
        }
        pendingDelete = null
        reload()
    }

    fun delete(item: MediaItem) {
        // Android 11+: to the trash (30 days, can be restored) instead of gone for good.
        if (Build.VERSION.SDK_INT >= 30 && item.uri.authority == MediaStore.AUTHORITY) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { MediaLibrary.setTrashed(context, listOf(item.uri), true) }
                if (ok) {
                    onChanged(); reload()
                    // No "are you sure?": a bar to take it back instead.
                    val app = context.applicationContext
                    UndoBar.show(if (item.isVideo) "Video moved to Trash" else "Photo moved to Trash") {
                        Thread { MediaLibrary.setTrashed(app, listOf(item.uri), false) }.start()
                        scope.launch { delay(400); try { onChanged(); reload() } catch (_: Throwable) {} }
                    }
                }
                else {
                    pendingDelete = null
                    if (Build.VERSION.SDK_INT >= 30) deleteLauncher.launch(IntentSenderRequest.Builder(MediaStore.createTrashRequest(context.contentResolver, listOf(item.uri), true).intentSender).build())
                }
            }
            return
        }
        scope.launch {
            val needsSystem: IntentSenderRequest? = withContext(Dispatchers.IO) {
                try {
                    if (item.uri.authority != MediaStore.AUTHORITY) com.dronedirector.veyrocutpro.camera.Storage.discard(context, item.uri)
                    else context.contentResolver.delete(item.uri, null, null)
                    com.dronedirector.veyrocutpro.camera.Favorites.remove(context, item.uri)
                    null
                } catch (e: SecurityException) {
                    when {
                        Build.VERSION.SDK_INT >= 30 ->
                            IntentSenderRequest.Builder(MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri)).intentSender).build()
                        Build.VERSION.SDK_INT == 29 && e is RecoverableSecurityException ->
                            IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                        else -> null
                    }
                }
            }
            if (needsSystem != null) {
                pendingDelete = item
                deleteLauncher.launch(needsSystem)
            } else {
                onChanged()
                reload()
            }
        }
    }

    val all = items ?: emptyList()
    val shown = when (tab) {
        GalleryTab.ALL -> all
        GalleryTab.PHOTOS -> all.filter { !it.isVideo && !it.isRaw }
        GalleryTab.VIDEOS -> all.filter { it.isVideo }
        GalleryTab.RAW -> all.filter { it.isRaw }
        GalleryTab.FAVORITES -> all.filter { it.uri.toString() in favorites }
        GalleryTab.TRASH -> trash
    }.filter { item ->
        val m = marks[item.uri.toString()]
        when (rateFilter) {
            1 -> (m?.stars ?: 0) >= 1
            2 -> (m?.stars ?: 0) >= 3
            3 -> (m?.stars ?: 0) >= 5
            4 -> m?.flag == 1
            5 -> m?.flag == -1
            6 -> m?.flag != -1
            else -> true
        }
    }

    BackHandler { if (viewing != null) viewing = null else onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF07080A))
            // Swallow touches so nothing reaches the camera underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            GalleryHeader(count = all.size, onBack = onClose)
            TabRow(tab, all, favorites, trash.size) { tab = it }
            if (tab == GalleryTab.TRASH) Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Deleted items stay here for 30 days, then Android removes them. Tap one to restore it.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (trash.isNotEmpty()) Text(
                    "EMPTY TRASH", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { confirmEmpty = true }.padding(8.dp),
                )
            }
            // Rating filter for culling.
            Row(
                Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("Any", "★ 1+", "★ 3+", "★ 5", "✓ Picks", "✕ Rejects", "Hide rejects").forEachIndexed { i, label ->
                    val sel = rateFilter == i
                    Text(
                        label, color = if (sel) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) Pro.Amber else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.06f))
                            .clickable { rateFilter = i }.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                if (rateFilter == 5 && shown.isNotEmpty()) Text(
                    "DELETE ${shown.size} REJECTS", style = Pro.Label.copy(color = Pro.Red, fontSize = 10.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { confirmRejects = true }.padding(8.dp),
                )
            }
            if (selecting) {
                val sel = shown.filter { it.uri.toString() in picked }
                Row(
                    Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${picked.size} selected", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    @Composable fun act(label: String, color: Color = Color.White, onClick: () -> Unit) = Text(
                        label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    act("Select all") { picked.clear(); picked.addAll(shown.map { it.uri.toString() }) }
                    if (sel.isNotEmpty()) {
                        act("Share") { shareMany(context, sel) }
                        act("♥ Favourite") {
                            sel.forEach { com.dronedirector.veyrocutpro.camera.Favorites.toggle(context, it.uri) }
                            favorites = com.dronedirector.veyrocutpro.camera.Favorites.all(context)
                        }
                        act("✓ Pick") { sel.forEach { mark(it, (marks[it.uri.toString()] ?: com.dronedirector.veyrocutpro.camera.Ratings.Mark()).copy(flag = 1)) } }
                        act("✕ Reject") { sel.forEach { mark(it, (marks[it.uri.toString()] ?: com.dronedirector.veyrocutpro.camera.Ratings.Mark()).copy(flag = -1)) } }
                        act("Delete", Pro.Red) { confirmPicked = true }
                    }
                    act("Done", Pro.Amber) { selecting = false; picked.clear() }
                }
            }
            when {
                items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Pro.Amber)
                }
                shown.isEmpty() -> EmptyGallery(tab)
                else -> MediaGrid(shown, favorites, marks, if (selecting) picked.toSet() else null,
                    onLongPress = { i -> if (tab != GalleryTab.TRASH) shown.getOrNull(i)?.let { selecting = true; val k = it.uri.toString(); if (k !in picked) picked.add(k) } },
                ) { i ->
                    val it0 = shown.getOrNull(i)
                    if (selecting && it0 != null) { val k = it0.uri.toString(); if (k in picked) picked.remove(k) else picked.add(k); if (picked.isEmpty()) selecting = false }
                    else if (tab == GalleryTab.TRASH) trashPick = it0 else viewing = i
                }
            }
        }

        val v = viewing
        if (v != null && shown.isNotEmpty()) {
            MediaViewer(
                items = shown,
                start = v.coerceIn(0, shown.lastIndex),
                onClose = { viewing = null },
                onDelete = { item ->
                    if (hasTrash && item.uri.authority == MediaStore.AUTHORITY) { if (shown.size <= 1) viewing = null; delete(item) } else confirmItem = item
                },
                favorites = favorites,
                onFavorite = { item ->
                    com.dronedirector.veyrocutpro.camera.Favorites.toggle(context, item.uri)
                    favorites = com.dronedirector.veyrocutpro.camera.Favorites.all(context)
                },
                onGrade = { grading = it },
                marks = marks,
                onMark = { item, m -> mark(item, m) },
            )
        }

        grading?.let { g ->
            GradeEditor(
                item = g,
                onClose = { grading = null },
                onSaved = { reload(); onChanged() },
            )
        }

        if (confirmPicked) {
            val del = shown.filter { it.uri.toString() in picked }
            AlertDialog(
                onDismissRequest = { confirmPicked = false },
                containerColor = Pro.PanelSolid,
                title = { Text("Delete ${del.size} item" + (if (del.size == 1) "" else "s") + "?", color = Color.White) },
                text = { Text(if (Build.VERSION.SDK_INT >= 30) "They go to the trash (Trash tab) for 30 days, where you can restore them." else "They will be permanently removed from your device.", color = Pro.Dim) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmPicked = false; selecting = false; picked.clear()
                        scope.launch {
                            val media = del.filter { it.uri.authority == MediaStore.AUTHORITY }.map { it.uri }
                            withContext(Dispatchers.IO) { for (r in del) if (r.uri.authority != MediaStore.AUTHORITY) try { com.dronedirector.veyrocutpro.camera.Storage.discard(context, r.uri) } catch (_: Throwable) {} }
                            if (media.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
                                deleteLauncher.launch(IntentSenderRequest.Builder(MediaStore.createTrashRequest(context.contentResolver, media, true).intentSender).build())
                            } else {
                                withContext(Dispatchers.IO) { for (u in media) try { context.contentResolver.delete(u, null, null) } catch (_: Throwable) {} }
                                onChanged(); reload()
                            }
                        }
                    }) { Text("Delete", color = Pro.Red, fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { confirmPicked = false }) { Text("Cancel", color = Color.White) } },
            )
        }

        if (confirmRejects) {
            val rejects = shown.toList()
            AlertDialog(
                onDismissRequest = { confirmRejects = false },
                containerColor = Pro.PanelSolid,
                title = { Text("Delete ${rejects.size} rejects?", color = Color.White) },
                text = { Text("Every photo and video marked as rejected will be permanently removed from your device.", color = Pro.Dim) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRejects = false
                        scope.launch {
                            val locked = mutableListOf<Uri>()
                            withContext(Dispatchers.IO) {
                                for (r in rejects) try {
                                    if (r.uri.authority != MediaStore.AUTHORITY) com.dronedirector.veyrocutpro.camera.Storage.discard(context, r.uri)
                                    else context.contentResolver.delete(r.uri, null, null)
                                    com.dronedirector.veyrocutpro.camera.Ratings.set(context, r.uri, com.dronedirector.veyrocutpro.camera.Ratings.Mark())
                                } catch (_: SecurityException) { locked += r.uri }
                            }
                            // Files the app didn't make need the system's OK (one dialog for all).
                            if (locked.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
                                deleteLauncher.launch(IntentSenderRequest.Builder(MediaStore.createDeleteRequest(context.contentResolver, locked).intentSender).build())
                            } else { onChanged(); reload() }
                            marks = com.dronedirector.veyrocutpro.camera.Ratings.all(context)
                        }
                    }) { Text("Delete", color = Pro.Red, fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { confirmRejects = false }) { Text("Cancel", color = Color.White) } },
            )
        }

        trashPick?.let { t ->
            AlertDialog(
                onDismissRequest = { trashPick = null },
                containerColor = Pro.PanelSolid,
                title = { Text(t.name, color = Color.White) },
                text = { Text("Restore it to the gallery, or delete it for good now?", color = Pro.Dim) },
                confirmButton = {
                    TextButton(onClick = {
                        trashPick = null
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { MediaLibrary.setTrashed(context, listOf(t.uri), false) }
                            if (ok) { onChanged(); reload() }
                            else if (Build.VERSION.SDK_INT >= 30) deleteLauncher.launch(IntentSenderRequest.Builder(MediaStore.createTrashRequest(context.contentResolver, listOf(t.uri), false).intentSender).build())
                        }
                    }) { Text("Restore", color = Pro.Amber, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        trashPick = null
                        if (Build.VERSION.SDK_INT >= 30) deleteLauncher.launch(IntentSenderRequest.Builder(MediaStore.createDeleteRequest(context.contentResolver, listOf(t.uri)).intentSender).build())
                    }) { Text("Delete forever", color = Pro.Red) }
                },
            )
        }
        if (confirmEmpty) AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            containerColor = Pro.PanelSolid,
            title = { Text("Empty the trash?", color = Color.White) },
            text = { Text("${trash.size} items will be permanently removed from your device.", color = Pro.Dim) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    if (trash.isNotEmpty() && Build.VERSION.SDK_INT >= 30) deleteLauncher.launch(IntentSenderRequest.Builder(MediaStore.createDeleteRequest(context.contentResolver, trash.map { it.uri }).intentSender).build())
                }) { Text("Delete all", color = Pro.Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel", color = Color.White) } },
        )

        val confirm = confirmItem
        if (confirm != null) {
            AlertDialog(
                onDismissRequest = { confirmItem = null },
                containerColor = Pro.PanelSolid,
                title = { Text(if (confirm.isVideo) "Delete video?" else "Delete photo?", color = Color.White) },
                text = { Text(if (hasTrash && confirm.uri.authority == MediaStore.AUTHORITY) "${confirm.name} moves to the Trash, where you can restore it for 30 days." else "${confirm.name} will be permanently removed from your device.", color = Pro.Dim) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmItem = null
                        // Leave the viewer if it was the last item in the list.
                        if (shown.size <= 1) viewing = null
                        delete(confirm)
                    }) { Text("Delete", color = Pro.Red, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmItem = null }) { Text("Cancel", color = Color.White) }
                },
            )
        }
    }
}

@Composable
private fun GalleryHeader(count: Int, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to camera", tint = Color.White) }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text("Gallery", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("AlcorCut Pro · $count ${if (count == 1) "item" else "items"}", style = Pro.Label)
        }
    }
}

@Composable
private fun TabRow(tab: GalleryTab, all: List<MediaItem>, favorites: Set<String>, trashCount: Int, onTab: (GalleryTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().hintedScroll(horizontal = true).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GalleryTab.entries.filter { it != GalleryTab.TRASH || hasTrash }.forEach { t ->
            val n = when (t) {
                GalleryTab.TRASH -> trashCount
                GalleryTab.ALL -> all.size
                GalleryTab.PHOTOS -> all.count { !it.isVideo && !it.isRaw }
                GalleryTab.VIDEOS -> all.count { it.isVideo }
                GalleryTab.RAW -> all.count { it.isRaw }
                GalleryTab.FAVORITES -> all.count { it.uri.toString() in favorites }
            }
            val sel = t == tab
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) Pro.Amber else com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.08f))
                    .border(1.dp, if (sel) Pro.Amber else Pro.Line, RoundedCornerShape(50))
                    .clickable { onTab(t) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    "${t.label}  $n",
                    color = if (sel) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EmptyGallery(tab: GalleryTab) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = Pro.Amber, modifier = Modifier.size(48.dp))
            Text(
                when (tab) {
                    GalleryTab.ALL -> "No photos or videos yet"
                    GalleryTab.PHOTOS -> "No photos yet"
                    GalleryTab.VIDEOS -> "No videos yet"
                    GalleryTab.RAW -> "No RAW photos yet"
                    GalleryTab.FAVORITES -> "No favourites yet"
                    GalleryTab.TRASH -> "The trash is empty"
                },
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                when (tab) {
                    GalleryTab.FAVORITES -> "Open a photo or video and tap the heart to add it here."
                    GalleryTab.RAW -> "In PRO photo mode, set FORMAT to RAW or RAW+J to save DNG files."
                    else -> "Everything you shoot with AlcorCut Pro shows up here."
                },
                color = Pro.Dim, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MediaGrid(items: List<MediaItem>, favorites: Set<String>, marks: Map<String, com.dronedirector.veyrocutpro.camera.Ratings.Mark>, picked: Set<String>? = null, onLongPress: (Int) -> Unit = {}, onOpen: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        var lastDay = ""
        items.forEachIndexed { index, item ->
            val day = dayLabel(item.dateTaken)
            if (day != lastDay) {
                lastDay = day
                item(key = "h$day$index", span = { GridItemSpan(maxLineSpan) }) {
                    Text(day, style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp))
                }
            }
            item(key = item.uri.toString()) { GridCell(item, item.uri.toString() in favorites, marks[item.uri.toString()], picked?.let { item.uri.toString() in it }, onLongClick = { onLongPress(index) }) { onOpen(index) } }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GridCell(item: MediaItem, favorite: Boolean, mark: com.dronedirector.veyrocutpro.camera.Ratings.Mark? = null, selected: Boolean? = null, onLongClick: () -> Unit = {}, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceThumb(context, item.uri, 320, dng = item.isRaw && (item.name.endsWith(".dng", true) || item.mime.contains("dng", true)))
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(com.dronedirector.veyrocutpro.ui.Pro.Fg.copy(alpha = 0.06f))
            .then(if (selected == true) Modifier.border(3.dp, Pro.Amber, RoundedCornerShape(6.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val t = thumb
        if (t != null) Image(t, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (item.isRaw) {
            Text(
                "RAW",
                style = Pro.Label.copy(color = Color.Black, fontSize = 9.sp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Pro.Amber)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        if (favorite) {
            Icon(
                Icons.Filled.Favorite, contentDescription = "Favourite", tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(16.dp),
            )
        }
        // Culling marks: rejected = dimmed, pick = green tick, stars along the bottom.
        if (mark?.flag == -1) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
            Text("✕", color = Pro.Red, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (mark?.flag == 1) Text("✓", color = Color(0xFF34C759), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
        if ((mark?.stars ?: 0) > 0) Text(
            "★".repeat(mark!!.stars), color = Pro.Amber, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 3.dp),
        )
        if (item.isVideo) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))
                )
            )
            Row(
                Modifier.align(Alignment.BottomStart).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(duration(item.durationMs), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (selected != null) Text(
            if (selected) "✓" else "", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).clip(CircleShape)
                .background(if (selected) Pro.Amber else Color.Black.copy(alpha = 0.45f)).border(1.5.dp, Color.White, CircleShape).padding(top = 1.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun produceThumb(context: Context, uri: Uri, px: Int, dng: Boolean = false) =
    androidx.compose.runtime.produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val sys = if (Build.VERSION.SDK_INT >= 29) {
                    try { context.contentResolver.loadThumbnail(uri, Size(px, px), null) } catch (_: Throwable) { null }
                } else null
                var b = sys ?: decodeImage(context, uri, px)
                // DNG: neither the system thumbnail nor the decoder turns it by the file's orientation tag.
                if (dng && b != null) {
                    val rot = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
                    if (rot != 0) b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
                }
                b?.asImageBitmap()
            } catch (_: Throwable) {
                null
            }
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaViewer(
    items: List<MediaItem>,
    start: Int,
    onClose: () -> Unit,
    onDelete: (MediaItem) -> Unit,
    favorites: Set<String>,
    onFavorite: (MediaItem) -> Unit,
    onGrade: (MediaItem) -> Unit,
    marks: Map<String, com.dronedirector.veyrocutpro.camera.Ratings.Mark> = emptyMap(),
    onMark: (MediaItem, com.dronedirector.veyrocutpro.camera.Ratings.Mark) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = start) { items.size }
    var chrome by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { items.getOrNull(it)?.uri?.toString() ?: it },
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            if (item.isVideo) {
                VideoPage(item, active = pager.currentPage == page, onToggleChrome = { chrome = !chrome })
            } else {
                PhotoPage(item, onToggleChrome = { chrome = !chrome })
            }
        }

        val current = items.getOrNull(pager.currentPage)
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.75f), 1f to Color.Transparent))
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to gallery", tint = Color.White) }
                if (current != null) {
                    Column(Modifier.weight(1f)) {
                        Text(fullDate(current.dateTaken), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(details(context, current), style = Pro.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${pager.currentPage + 1} / ${items.size}", style = Pro.Value.copy(color = Pro.Amber), modifier = Modifier.padding(end = 12.dp))
                }
            }
        }

        AnimatedVisibility(chrome && current != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))).padding(top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // Rating: tap a star (tap it again to clear), ✓ pick, ✕ reject.
            if (current != null) {
                val m = marks[current.uri.toString()] ?: com.dronedirector.veyrocutpro.camera.Ratings.Mark()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("✕", color = if (m.flag == -1) Pro.Red else Color.White.copy(alpha = 0.6f), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).clickable { onMark(current, m.copy(flag = if (m.flag == -1) 0 else -1)) }.padding(horizontal = 12.dp, vertical = 6.dp))
                    for (n in 1..5) Text(if (n <= m.stars) "★" else "☆", color = if (n <= m.stars) Pro.Amber else Color.White.copy(alpha = 0.6f), fontSize = 24.sp,
                        modifier = Modifier.clip(CircleShape).clickable { onMark(current, m.copy(stars = if (m.stars == n) 0 else n)) }.padding(horizontal = 4.dp, vertical = 4.dp))
                    Text("✓", color = if (m.flag == 1) Color(0xFF34C759) else Color.White.copy(alpha = 0.6f), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).clickable { onMark(current, m.copy(flag = if (m.flag == 1) 0 else 1)) }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                current ?: return@Row
                val fav = current.uri.toString() in favorites
                ViewerAction(
                    if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    "Favourite", tint = if (fav) Pro.Red else Color.White,
                ) { onFavorite(current) }
                ViewerAction(Icons.Filled.Tune, "Grade", tint = Pro.Amber) { onGrade(current) }
                ViewerAction(Icons.Filled.Share, "Share") { share(context, current) }
                ViewerAction(Icons.AutoMirrored.Filled.OpenInNew, "Open") { openWith(context, current) }
                ViewerAction(Icons.Filled.Delete, "Delete", tint = Pro.Red) { onDelete(current) }
            }
            }
        }
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 60.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun PhotoPage(item: MediaItem, onToggleChrome: () -> Unit) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val target = maxOf(constraints.maxWidth, constraints.maxHeight).coerceAtMost(3072)
        val bitmap by androidx.compose.runtime.produceState<ImageBitmap?>(null, item.uri) {
            value = withContext(Dispatchers.IO) { decodeImage(context, item.uri, target, raw = item.isRaw && (item.name.endsWith(".dng", true) || item.mime.contains("dng", true)))?.asImageBitmap() }
        }
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        fun clamp(o: Offset, s: Float) = Offset(
            o.x.coerceIn(-w * (s - 1f) / 2f, w * (s - 1f) / 2f),
            o.y.coerceIn(-h * (s - 1f) / 2f, h * (s - 1f) / 2f),
        )
        val b = bitmap
        if (b == null) {
            CircularProgressIndicator(color = Pro.Amber)
        } else {
            Image(
                b,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.uri) {
                        detectTapGestures(
                            onTap = { onToggleChrome() },
                            onDoubleTap = { tap ->
                                if (scale > 1f) {
                                    scale = 1f; offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                    offset = clamp(Offset((w / 2f - tap.x) * 1.5f, (h / 2f - tap.y) * 1.5f), scale)
                                }
                            },
                        )
                    }
                    .pointerInput(item.uri) {
                        // Pinch to zoom; pan while zoomed. Single-finger swipes at 1x go to the pager.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed > 1 || scale > 1.01f) {
                                    val zoom = event.calculateZoom()
                                    val newScale = (scale * zoom).coerceIn(1f, 6f)
                                    offset = if (newScale <= 1f) Offset.Zero else clamp(offset + event.calculatePan(), newScale)
                                    scale = newScale
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
            )
        }
    }
}

@Composable
private fun VideoPage(item: MediaItem, active: Boolean, onToggleChrome: () -> Unit) {
    val context = LocalContext.current
    var playing by remember(item.uri) { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) playing = false }
    val poster by produceThumb(context, item.uri, 1024)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (playing) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoURI(item.uri)
                        setOnPreparedListener { mp -> mp.isLooping = false; start(); controller.show(2500) }
                        setOnCompletionListener { playing = false }
                        setOnErrorListener { _, _, _ -> playing = false; true }
                    }
                },
                onRelease = { it.stopPlayback() },
                // VideoView sizes itself to the largest fit for the clip (portrait or landscape).
                modifier = Modifier.wrapContentSize(),
            )
        } else {
            val p = poster
            if (p != null) {
                Image(
                    p, contentDescription = item.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onToggleChrome() }) },
                )
            }
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(2.dp, Pro.Amber, CircleShape)
                    .clickable { playing = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Text(
                duration(item.durationMs),
                style = Pro.Value,
                modifier = Modifier.align(Alignment.Center).padding(top = 120.dp),
            )
        }
    }
}

// ---- helpers ----

private fun decodeImage(context: Context, uri: Uri, maxPx: Int, raw: Boolean = false): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= 28) {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxPx) {
                val f = maxPx.toFloat() / longest
                decoder.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        // DNG: the decoder doesn't turn the picture by the file's orientation tag (it does for JPEG).
        val rot = if (raw) context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0 else 0
        if (rot != 0) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot.toFloat()) }, true) else bmp
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        val rotation = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        if (bmp != null && rotation != 0) {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        } else bmp
    }
} catch (_: Throwable) {
    null
}

private fun share(context: Context, item: MediaItem) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(item.mime)
        .putExtra(Intent.EXTRA_STREAM, item.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun shareMany(context: Context, items: List<MediaItem>) {
    if (items.size == 1) { share(context, items[0]); return }
    val mime = items.map { it.mime.substringBefore('/') }.distinct().singleOrNull()?.let { "$it/*" } ?: "*/*"
    val send = Intent(Intent.ACTION_SEND_MULTIPLE)
        .setType(mime)
        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openWith(context: Context, item: MediaItem) {
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(item.uri, item.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(view, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun duration(ms: Long): String {
    val total = ((ms + 999) / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun dayLabel(millis: Long): String {
    val now = System.currentTimeMillis()
    val dayMs = 24L * 3600 * 1000
    val today = DateFormat.format("yyyyMMdd", now).toString()
    val that = DateFormat.format("yyyyMMdd", millis).toString()
    return when {
        that == today -> "TODAY"
        that == DateFormat.format("yyyyMMdd", now - dayMs).toString() -> "YESTERDAY"
        else -> DateFormat.format("EEE d MMM yyyy", millis).toString().uppercase()
    }
}

private fun fullDate(millis: Long): String =
    DateFormat.format("d MMM yyyy · HH:mm", Date(millis)).toString()

private fun details(context: Context, item: MediaItem): String = buildList {
    if (item.width > 0 && item.height > 0) {
        add("${item.width}×${item.height}")
        if (!item.isVideo) add("%.1f MP".format(item.width * item.height / 1_000_000f))
    }
    if (item.isVideo) add(duration(item.durationMs))
    if (item.size > 0) add(Formatter.formatShortFileSize(context, item.size))
}.joinToString("  ·  ")
