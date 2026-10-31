package com.dronedirector.veyrocutpro.editor

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Client review for videos: a .zip with a web page and the video. The client plays it in any browser,
 * pauses where something should change and writes a note — each note keeps its exact time. "Send my
 * comments" gives a list; pasted back into Deliver → CLIENT REVIEW, the notes become markers on the timeline.
 */
object VideoReview {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun build(c: Context, video: Uri, title: String, email: String, progress: (String) -> Unit): Uri? {
        val ext = (try { c.contentResolver.query(video, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } } catch (_: Throwable) { null })
            ?.substringAfterLast('.', "mp4")?.lowercase()?.takeIf { it.length in 2..4 } ?: "mp4"
        val safe = title.ifBlank { "Review" }.replace(Regex("[^A-Za-z0-9 _\\-]"), "").trim().replace(' ', '_').ifBlank { "Review" }
        val name = "${safe}_review.zip"
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/AlcorCut Pro")
        }
        val dst = (if (Build.VERSION.SDK_INT >= 29) try { c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) } catch (_: Throwable) { null } else null)
            ?: Uri.fromFile(java.io.File(c.getExternalFilesDir(null), name))
        (c.contentResolver.openOutputStream(dst) ?: return null).use { os ->
            ZipOutputStream(os).use { z ->
                progress("Copying the video…")
                z.setLevel(0)   // the video is already compressed
                z.putNextEntry(ZipEntry("video.$ext"))
                c.contentResolver.openInputStream(video)?.use { it.copyTo(z, 1 shl 20) } ?: return null
                z.closeEntry()
                z.setLevel(9)
                z.putNextEntry(ZipEntry("index.html")); z.write(page(title.ifBlank { "Review" }, email, "video.$ext").toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("READ ME.txt"))
                z.write("Unzip this folder and open index.html in a web browser. Pause where you want a change, write your note and press \"Add note\". When you're done, press \"Send my notes\".\n".toByteArray()); z.closeEntry()
            }
        }
        com.dronedirector.veyrocutpro.ui.AppLog.i("Video review page made: $name")
        return dst
    }

    private fun page(title: String, email: String, file: String) = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(title)}</title>
<style>
body{margin:0;background:#111;color:#eee;font-family:system-ui,sans-serif}
.w{max-width:1100px;margin:0 auto;padding:16px}
h1{font-size:22px;margin:6px 0 12px}
video{width:100%;background:#000;border-radius:8px}
.r{display:flex;gap:8px;margin:10px 0;flex-wrap:wrap}
textarea{flex:1;min-width:220px;background:#222;color:#eee;border:1px solid #333;border-radius:8px;padding:8px;font:inherit}
button{background:#f0a830;color:#000;border:0;border-radius:20px;padding:9px 16px;font-weight:600;cursor:pointer}
button.o{background:#2a2a2a;color:#eee}
.n{background:#1c1c1c;border-radius:8px;padding:8px 10px;margin:6px 0;display:flex;gap:10px;align-items:flex-start}
.t{color:#f0a830;font-variant-numeric:tabular-nums;cursor:pointer;white-space:nowrap}
.x{margin-left:auto;color:#888;cursor:pointer}
#out{white-space:pre-wrap;background:#1c1c1c;padding:12px;border-radius:8px;display:none;font-size:12px}
</style></head><body><div class="w">
<h1>${esc(title)}</h1>
<video id="v" src="$file" controls playsinline></video>
<div class="r"><textarea id="tx" rows="2" placeholder="Pause where something should change, then write your note here"></textarea>
<div style="display:flex;flex-direction:column;gap:6px"><button onclick="add()">Add note</button><button class="o" onclick="send()">Send my notes</button></div></div>
<div id="list"></div><div id="out"></div></div>
<script>
var K='review_'+${"\"" + esc(title).replace("\"", "") + "\""},N=[],mail="${esc(email)}",v=document.getElementById('v');
try{N=JSON.parse(localStorage.getItem(K)||'[]')}catch(e){}
function save(){try{localStorage.setItem(K,JSON.stringify(N))}catch(e){}}
function tc(s){var m=Math.floor(s/60),x=(s%60);return String(Math.floor(m/60)).padStart(2,'0')+':'+String(m%60).padStart(2,'0')+':'+x.toFixed(2).padStart(5,'0')}
function draw(){N.sort(function(a,b){return a.t-b.t});var h='';N.forEach(function(n,i){h+='<div class="n"><span class="t" onclick="v.currentTime='+n.t+'">'+tc(n.t)+'</span><span>'+n.x.replace(/</g,'&lt;')+'</span><span class="x" onclick="del('+i+')">✕</span></div>'});document.getElementById('list').innerHTML=h}
function add(){var t=document.getElementById('tx');if(!t.value.trim())return;N.push({t:v.currentTime,x:t.value.trim()});t.value='';save();draw()}
function del(i){N.splice(i,1);save();draw()}
function send(){var t='AlcorCut review: '+document.title+'\n';N.forEach(function(n){t+=tc(n.t)+' | '+n.x.replace(/\n/g,' ')+'\n'});
var o=document.getElementById('out');o.style.display='block';o.textContent=t;try{navigator.clipboard.writeText(t)}catch(e){}
if(mail)location.href='mailto:'+mail+'?subject='+encodeURIComponent('Notes: '+document.title)+'&body='+encodeURIComponent(t)}
draw();
</script></body></html>"""

    /** The client's notes → (time in ms, note). Lines look like "00:01:23.45 | note". */
    fun parse(text: String): List<Pair<Long, String>> = text.lines().mapNotNull { l ->
        val m = Regex("^\\s*(\\d{1,2}):(\\d{2}):(\\d{2}(?:\\.\\d+)?)\\s*\\|\\s*(.*)$").find(l) ?: return@mapNotNull null
        val (h, mi, s, note) = m.destructured
        ((h.toLong() * 3600 + mi.toLong() * 60) * 1000 + (s.toDouble() * 1000).toLong()) to note.trim()
    }
}

/** Deliver → CLIENT REVIEW: make the review page from an exported video; bring the notes back as markers. */
@Composable
fun VideoReviewSection(onNotes: (List<Pair<Long, String>>) -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var paste by remember { mutableStateOf<String?>(null) }
    val colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Pro.Fg, unfocusedTextColor = Pro.Fg, focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u == null) return@rememberLauncherForActivityResult
        busy = "Making the review page…"
        scope.launch {
            val out = withContext(Dispatchers.IO) { try { VideoReview.build(c, u, title, email.trim()) { busy = it } } catch (_: Throwable) { null } }
            busy = if (out == null) "Couldn't make the review page" else "Saved in Downloads/AlcorCut Pro — send it to your client"
            if (out != null) try {
                c.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/zip")
                    .putExtra(android.content.Intent.EXTRA_STREAM, out).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
            } catch (_: Throwable) {}
        }
    }
    SectionTitle("CLIENT REVIEW")
    Text("Export the video first, then make a review page from it: your client plays it in any browser and adds notes at exact times. Paste what they send back and the notes become markers on the timeline.", color = Pro.Dim, fontSize = 11.sp)
    OutlinedTextField(title, { title = it }, label = { Text("Title (e.g. Hansen wedding — cut 2)") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(email, { email = it }, label = { Text("Your e-mail (notes are sent to it)") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PillChip("MAKE REVIEW PAGE…", false, accentText = true) { try { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) } catch (_: Throwable) {} }
        PillChip("ADD CLIENT NOTES…", false, accentText = true) { paste = "" }
    }
    busy?.let { Text(it, color = Pro.Amber, fontSize = 11.sp) }
    paste?.let { t0 ->
        var t by remember { mutableStateOf(t0) }
        AlertDialog(onDismissRequest = { paste = null }, containerColor = Ed.Panel,
            title = { Text("Client notes", color = Pro.Fg) },
            text = {
                Column {
                    Text("Paste the list your client sent from the review page.", color = Pro.Dim, fontSize = 12.sp)
                    OutlinedTextField(t, { t = it }, modifier = Modifier.fillMaxWidth().height(180.dp), colors = colors)
                }
            },
            confirmButton = { TextButton(onClick = { val n = VideoReview.parse(t); paste = null; onNotes(n); busy = "${n.size} note(s) added as markers" }) { Text("ADD MARKERS", color = Pro.Amber) } },
            dismissButton = { TextButton(onClick = { paste = null }) { Text("CANCEL", color = Pro.Dim) } },
        )
    }
}
