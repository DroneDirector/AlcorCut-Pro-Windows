package com.dronedirector.veyrocutpro.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import com.dronedirector.veyrocutpro.ui.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Network (NAS)" in the add lists: find a NAS on the network, open its shared folders, pick
 * files and copy them in. [onPicked] gets the copies. Own code.
 */
@Composable
fun NasBrowserDialog(
    kinds: NasKinds, onDismiss: () -> Unit,
    /** Folder mode (team projects): only folders are shown, and "Use this folder" hands the open NAS over. */
    pickFolder: ((link: NasLink, share: String, path: String) -> Unit)? = null,
    onPicked: (List<Uri>) -> Unit = {},
) {
    var handedOver by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val finder = remember { NasFinder(context) }
    var link by remember { mutableStateOf<NasLink?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var signInFor by remember { mutableStateOf<NasServer?>(null) }
    var wrong by remember { mutableStateOf(false) }
    var rememberAsk by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var pending by remember { mutableStateOf<CharArray?>(null) }
    var server by remember { mutableStateOf<NasServer?>(null) }
    var shares by remember { mutableStateOf<List<String>>(emptyList()) }
    var share by remember { mutableStateOf<String?>(null) }
    var path by remember { mutableStateOf(listOf<String>()) }
    var entries by remember { mutableStateOf<List<NasEntry>>(emptyList()) }
    val picked = remember { mutableStateMapOf<String, NasPick>() }
    var copying by remember { mutableStateOf(false) }
    var copyFile by remember { mutableIntStateOf(0) }
    var copyFrac by remember { mutableFloatStateOf(0f) }
    var stop by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { NasSignIns.load(context); PhoneLock.load(context); finder.start(scope) }
    val unlock = rememberPhoneUnlock()
    DisposableEffect(Unit) {
        onDispose {
            finder.stop()
            stop = true
            val l = link
            if (l != null && !handedOver) kotlin.concurrent.thread { l.close() }
            pending?.fill('\u0000')
        }
    }

    fun loadFolder(l: NasLink, s: String, p: List<String>) {
        busy = "Opening…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { try { Result.success(l.list(s, p.joinToString("\\"), kinds)) } catch (t: Throwable) { Result.failure(t) } }
            busy = null
            r.onSuccess { entries = it; share = s; path = p }
            r.onFailure { t -> error = if (NasLink.refused(t)) "This shared folder needs a sign-in with access to it" else "Couldn't open that folder" + (t.message?.let { " — $it" } ?: "") }
        }
    }

    fun connect(s: NasServer, user: String? = null, pw: CharArray? = null, domain: String = "", useSaved: Boolean = true) {
        finder.stop()
        busy = "Connecting to ${s.name}…"; error = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { Nas.open(context, s.host, user, pw, domain, useSaved) }
            busy = null
            when (r) {
                is Nas.Opened.Ok -> {
                    link?.let { old -> kotlin.concurrent.thread { old.close() } }
                    link = r.link; server = s; signInFor = null
                    shares = withContext(Dispatchers.IO) { try { r.link.shares() } catch (_: Throwable) { emptyList() } }
                    share = null; path = emptyList(); entries = emptyList()
                    // Signed in by hand: offer to keep it (encrypted) for next time.
                    if (user != null && pw != null) { pending = pw.copyOf(); rememberAsk = Triple(s.host, user, domain) }
                    pw?.fill('\u0000')
                }
                is Nas.Opened.NeedsSignIn -> { pw?.fill('\u0000'); wrong = r.wrong; signInFor = s }
                is Nas.Opened.Failed -> { pw?.fill('\u0000'); error = r.why }
            }
        }
    }

    // A saved sign-in is only used after the phone's own lock (fingerprint, PIN or pattern) — if one is set up.
    fun openServer(s: NasServer) {
        val saved = NasSignIns.has(context, s.host)
        if (!saved || !PhoneLock.nasLock) { connect(s); return }
        if (!PhoneLock.isSet(context)) {
            error = "No fingerprint, PIN or pattern is set up on this phone, so there's no extra lock — only the saved sign-in protects your NAS. Set one up in the phone's settings to lock it."
            connect(s); return
        }
        unlock("Unlock the sign-in for ${s.name}") { ok ->
            if (ok) connect(s)
            else error = "Not unlocked — the saved sign-in wasn't used."
        }
    }

    fun back() {
        when {
            copying -> stop = true
            share != null && path.isNotEmpty() -> link?.let { loadFolder(it, share!!, path.dropLast(1)) }
            share != null -> { share = null; entries = emptyList() }
            link != null -> { link?.let { old -> kotlin.concurrent.thread { old.close() } }; link = null; server = null; picked.clear(); finder.start(scope) }
            else -> onDismiss()
        }
    }

    Dialog(onDismissRequest = { if (!copying) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { back() }
        Column(Modifier.fillMaxSize().background(Ed.Bg).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable { back() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text("Network (NAS)", color = Color.White, fontSize = 18.sp)
                    val where = listOfNotNull(server?.name, share) + path
                    Text(if (where.isEmpty()) "Shared folders on your network" else where.joinToString(" › "), color = Pro.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable { if (!copying) onDismiss() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Close", tint = Color.White)
                }
            }
            error?.let { Text(it, color = Pro.Red, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)) }
            busy?.let {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Pro.Amber, strokeWidth = 2.dp)
                    Text(it, color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    copying -> Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Copying ${copyFile + 1} of ${picked.size}…", color = Color.White, fontSize = 15.sp)
                        LinearProgressIndicator(
                            progress = { ((copyFile + copyFrac) / picked.size.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), color = Pro.Amber,
                        )
                        Text("The files are copied into the app, so your edit keeps working when the NAS is off.", color = Pro.Dim, fontSize = 12.sp)
                        TextButton(onClick = { stop = true }) { Text("Stop", color = Pro.Red) }
                    }
                    link == null -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (finder.searching) "Searching your network…" else "Found on your network", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.weight(1f))
                                if (finder.searching) CircularProgressIndicator(Modifier.size(14.dp), color = Pro.Amber, strokeWidth = 2.dp)
                                else Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).clickable { finder.start(scope) }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Refresh, "Search again", tint = Pro.Amber, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        items(finder.found, key = { it.host }) { s ->
                            NasRow(Icons.Filled.Storage, s.name, if (s.name != s.host) s.host else null,
                                trailing = if (NasSignIns.saved.any { it.host == s.host }) Icons.Filled.Lock else null) { openServer(s) }
                        }
                        if (!finder.searching && finder.found.isEmpty()) item {
                            Text("Nothing found. Check that the phone is on the same Wi-Fi as the NAS and that file sharing (SMB) is turned on, or type its address below.", color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        item {
                            Text("OR TYPE ITS ADDRESS", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(address, { address = it.trim().take(120) }, singleLine = true, placeholder = { Text("192.168.1.20 or nas.local", color = Pro.Dim) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.weight(1f), colors = nasFieldColors())
                                TextButton(onClick = {
                                    val a = address.removePrefix("smb://").removePrefix("\\\\").substringBefore('/').substringBefore('\\')
                                    if (a.isNotBlank()) openServer(NasServer(a, a))
                                }) { Text("Connect", color = Pro.Amber) }
                            }
                            Text("Works with Synology and other NAS systems, and with computers sharing folders on the network (SMB).", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    share == null -> LazyColumn(Modifier.fillMaxSize()) {
                        if (shares.isEmpty() && busy == null) item { Text("No shared folders you can open here.", color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(shares, key = { it }) { s -> NasRow(Icons.Filled.FolderShared, s, null) { link?.let { loadFolder(it, s, emptyList()) } } }
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        if (entries.isEmpty() && busy == null) item { Text("Nothing here the editor can use.", color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(entries, key = { it.name }) { e ->
                            val sh = share!!
                            val full = (path + e.name).joinToString("\\")
                            val k = "$sh\\$full"
                            if (e.folder) NasRow(Icons.Filled.Folder, e.name, null) { link?.let { loadFolder(it, sh, path + e.name) } }
                            else {
                                val on = k in picked
                                val ext = e.name.substringAfterLast('.', "").lowercase()
                                val icon = when { ext in Nas.AUDIO -> Icons.Filled.AudioFile; ext in Nas.VIDEO -> Icons.Filled.Movie; else -> Icons.Filled.Image }
                                NasRow(icon, e.name, nasSize(e.size), trailing = if (on) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, selected = on) {
                                    if (on) picked.remove(k) else picked[k] = NasPick(sh, full, e.name, e.size)
                                }
                            }
                        }
                    }
                }
            }
            if (pickFolder != null && link != null && share != null) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("The team project is kept in the folder that's open now.", color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val l = link ?: return@TextButton
                        handedOver = true
                        pickFolder(l, share!!, path.joinToString("\\"))
                        onDismiss()
                    }) { Text("Use this folder", color = Pro.Amber, fontSize = 16.sp) }
                }
            }
            if (pickFolder == null && link != null && !copying) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (picked.isEmpty()) "Tap files to pick them" else "${picked.size} picked · ${nasSize(picked.values.sumOf { it.size })}", color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (picked.isNotEmpty()) TextButton(onClick = { picked.clear() }) { Text("Clear", color = Pro.Dim) }
                    TextButton(enabled = picked.isNotEmpty(), onClick = {
                        val l = link ?: return@TextButton
                        val list = picked.values.toList()
                        copying = true; stop = false; copyFile = 0; copyFrac = 0f
                        nasCopy(context, l, list, scope, keepGoing = { !stop },
                            onProgress = { i, f -> scope.launch { copyFile = i; copyFrac = f } }) { uris, err ->
                            copying = false
                            if (err != null) error = err
                            if (uris.isNotEmpty()) { onPicked(uris); onDismiss() }
                        }
                    }) { Text("Add", color = if (picked.isEmpty()) Pro.Dim else Pro.Amber, fontSize = 16.sp) }
                }
            }
        }
    }

    signInFor?.let { s -> NasSignInDialog(s, wrong, onDismiss = { signInFor = null }) { u, pw, d -> connect(s, u, pw, d) } }
    rememberAsk?.let { (host, user, domain) ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = Pro.PanelSolid,
            title = { Text("Remember this sign-in?", color = Color.White) },
            text = { Text("Saved encrypted on this phone, for $user on $host. The password is never shown, and you can remove the sign-in any time under the gear icon → Network (NAS). Next time the phone's own lock (fingerprint, PIN or pattern) is asked for before it's used — your fingerprint stays in the phone and is never shared with the app.", color = Pro.Dim) },
            confirmButton = { TextButton(onClick = {
                pending?.let { NasSignIns.save(context, host, user, it, domain) }
                pending?.fill('\u0000'); pending = null; rememberAsk = null
            }) { Text("Yes", color = Pro.Amber) } },
            dismissButton = { TextButton(onClick = { pending?.fill('\u0000'); pending = null; rememberAsk = null }) { Text("No", color = Color.White) } },
        )
    }
}

/** User name and password for a NAS that asks for them. The password is masked and never shown. */
@Composable
private fun NasSignInDialog(s: NasServer, wrong: Boolean, onDismiss: () -> Unit, onSignIn: (String, CharArray, String) -> Unit) {
    var user by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pro.PanelSolid,
        title = { Text("Sign in to ${s.name}", color = Color.White) },
        text = {
            Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (wrong) "That didn't work — check the user name and password." else "This NAS asks for a user name and password.", color = if (wrong) Pro.Red else Pro.Dim, fontSize = 13.sp)
                OutlinedTextField(user, { user = it.take(120) }, singleLine = true, label = { Text("User name") }, modifier = Modifier.fillMaxWidth(), colors = nasFieldColors())
                OutlinedTextField(pw, { pw = it.take(256) }, singleLine = true, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), colors = nasFieldColors())
                OutlinedTextField(domain, { domain = it.take(120) }, singleLine = true, label = { Text("Domain (usually empty)") }, modifier = Modifier.fillMaxWidth(), colors = nasFieldColors())
            }
        },
        confirmButton = { TextButton(enabled = user.isNotBlank(), onClick = {
            val a = pw.toCharArray(); pw = ""
            onSignIn(user.trim(), a, domain.trim())
        }) { Text("Sign in", color = Pro.Amber) } },
        dismissButton = { TextButton(onClick = { pw = ""; onDismiss() }) { Text("Cancel", color = Color.White) } },
    )
}

/** Gear icon → Network sign-ins: the saved NAS sign-ins (server and user only) with Remove. */
@Composable
fun NasSignInsSettings() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { NasSignIns.load(context) }
    var confirm by remember { mutableStateOf<NasSignIns.Shown?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Network sign-ins", color = Color.White, fontSize = 15.sp)
        Text("NAS sign-ins you chose to remember. They're encrypted on this phone, left out of backups, and the passwords are never shown.", color = Pro.Dim, fontSize = 12.sp)
        LaunchedEffect(Unit) { PhoneLock.load(context) }
        val lockSet = remember { PhoneLock.isSet(context) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Unlock with the phone's lock", color = Color.White, fontSize = 14.sp)
                Text("Before a saved sign-in is used, the phone asks for your fingerprint, PIN or pattern — whatever you've set up on the phone.", color = Pro.Dim, fontSize = 12.sp)
                Text(PhoneLock.NOTE, color = Pro.Dim, fontSize = 12.sp)
                if (!lockSet) Text("No fingerprint, PIN or pattern is set up on this phone, so nothing but the sign-in itself protects your NAS. Set one up in the phone's settings to use this.", color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            androidx.compose.material3.Switch(PhoneLock.nasLock, { PhoneLock.setNasLock(context, it) },
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Pro.Amber, checkedThumbColor = Color.Black))
        }
        if (NasSignIns.saved.isEmpty()) Text("None saved.", color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        for (s in NasSignIns.saved) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Ed.Raised).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = Pro.Amber, modifier = Modifier.size(16.dp))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(s.host, color = Color.White, fontSize = 14.sp)
                    Text(s.user, color = Pro.Dim, fontSize = 12.sp)
                }
                TextButton(onClick = { confirm = s }) { Text("Remove", color = Pro.Red) }
            }
        }
    }
    confirm?.let { s ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Pro.PanelSolid,
            title = { Text("Remove this sign-in?", color = Color.White) },
            text = { Text("${s.user} on ${s.host} is forgotten. The NAS asks again next time.", color = Pro.Dim) },
            confirmButton = { TextButton(onClick = { NasSignIns.remove(context, s.host); confirm = null }) { Text("Remove", color = Pro.Red) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel", color = Color.White) } },
        )
    }
}

@Composable
private fun NasRow(icon: ImageVector, title: String, sub: String?, trailing: ImageVector? = null, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) Pro.Amber.copy(alpha = 0.18f) else Ed.Raised).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Pro.Amber, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = Pro.Dim, fontSize = 12.sp)
        }
        if (trailing != null) Icon(trailing, null, tint = if (selected) Pro.Amber else Pro.Dim, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun nasFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg, unfocusedTextColor = com.dronedirector.veyrocutpro.ui.Pro.Fg,
    focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber,
)

private fun nasSize(b: Long): String = when {
    b >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", b / (1L shl 30).toFloat())
    b >= 1L shl 20 -> String.format(java.util.Locale.US, "%.0f MB", b / (1L shl 20).toFloat())
    else -> "${(b / 1024).coerceAtLeast(1)} KB"
}
