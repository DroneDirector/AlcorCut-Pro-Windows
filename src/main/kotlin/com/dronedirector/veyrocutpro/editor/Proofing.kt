package com.dronedirector.veyrocutpro.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.layout.Column
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
import com.dronedirector.veyrocutpro.camera.GradeIO
import com.dronedirector.veyrocutpro.ui.Pro
import com.dronedirector.veyrocutpro.ui.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Client proofing: a gallery the client opens in any web browser (a .zip with a page and the photos,
 * shared by mail, a link or a USB stick). They mark favourites, give stars and write notes, then send
 * the list back; pasting it into the library marks those photos as picks with the client's stars.
 */
object Proofing {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun watermark(b: Bitmap, text: String): Bitmap {
        if (text.isBlank()) return b
        val out = if (b.isMutable) b else b.copy(Bitmap.Config.ARGB_8888, true).also { b.recycle() }
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF; textSize = out.width / 14f; textAlign = Paint.Align.CENTER; setShadowLayer(4f, 2f, 2f, 0x55000000) }
        c.save(); c.rotate(-28f, out.width / 2f, out.height / 2f)
        var y = -out.height.toFloat()
        while (y < out.height * 2f) { c.drawText(text, out.width / 2f, y, p); y += p.textSize * 3.2f }
        c.restore()
        return out
    }

    /** Builds the gallery into Downloads; returns the .zip, or null. */
    fun build(context: Context, photos: List<LibPhoto>, title: String, email: String, longSide: Int, mark: String, progress: (Int, Int) -> Unit): Uri? {
        val safe = title.ifBlank { "Proofing" }.replace(Regex("[^A-Za-z0-9 _\\-]"), "").trim().replace(' ', '_').ifBlank { "Proofing" }
        val name = "${safe}_proofing.zip"
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/AlcorCut Pro")
        }
        val dst = (if (Build.VERSION.SDK_INT >= 29) try { context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) } catch (_: Throwable) { null } else null)
            ?: Uri.fromFile(java.io.File(context.getExternalFilesDir(null), name))
        val files = ArrayList<Pair<String, String>>()   // zip name, original name
        (context.contentResolver.openOutputStream(dst) ?: return null).use { os ->
            ZipOutputStream(os).use { z ->
                photos.forEachIndexed { i, p ->
                    progress(i, photos.size)
                    val b = try { GradeIO.decode(context, p.uri, longSide) } catch (_: Throwable) { null } ?: return@forEachIndexed
                    val w = watermark(b, mark)
                    val zn = "photos/" + (i + 1).toString().padStart(3, '0') + ".jpg"
                    z.putNextEntry(ZipEntry(zn)); w.compress(Bitmap.CompressFormat.JPEG, 88, z); z.closeEntry(); w.recycle()
                    files += zn to p.name
                }
                z.putNextEntry(ZipEntry("index.html")); z.write(page(title.ifBlank { "Proofing" }, email, files).toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("READ ME.txt"))
                z.write("Unzip this folder and open index.html in a web browser. Tap the heart on the photos you want, give stars or notes, then press \"Send my picks\".\n".toByteArray()); z.closeEntry()
            }
        }
        progress(photos.size, photos.size)
        com.dronedirector.veyrocutpro.ui.AppLog.i("Proofing gallery: ${files.size} photo(s) → $name")
        return dst
    }

    private fun page(title: String, email: String, files: List<Pair<String, String>>): String {
        val items = files.joinToString(",") { (zn, n) -> "{s:\"$zn\",n:\"" + n.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}" }
        return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(title)}</title>
<style>
:root{--bg:#111;--fg:#eee;--dim:#999;--acc:#f0a830}
body{margin:0;background:var(--bg);color:var(--fg);font-family:system-ui,sans-serif}
header{padding:20px 16px;position:sticky;top:0;background:#111e;backdrop-filter:blur(6px);z-index:2;display:flex;flex-wrap:wrap;gap:10px;align-items:center}
h1{font-size:22px;margin:0;flex:1}
button{background:var(--acc);color:#000;border:0;border-radius:20px;padding:9px 16px;font-weight:600;cursor:pointer}
button.o{background:#2a2a2a;color:var(--fg)}
.g{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:10px;padding:0 16px 40px}
.c{background:#1c1c1c;border-radius:10px;overflow:hidden}
.c img{width:100%;aspect-ratio:3/2;object-fit:cover;display:block;cursor:zoom-in}
.r{display:flex;align-items:center;gap:6px;padding:6px 8px;font-size:13px}
.h{font-size:22px;cursor:pointer;color:#555}.h.on{color:#ff4d6d}
.s span{cursor:pointer;color:#555;font-size:17px}.s span.on{color:var(--acc)}
.n{width:calc(100% - 16px);margin:0 8px 8px;background:#262626;color:var(--fg);border:0;border-radius:6px;padding:6px;font:inherit;font-size:12px}
#lb{position:fixed;inset:0;background:#000e;display:none;align-items:center;justify-content:center;z-index:5}#lb img{max-width:96vw;max-height:92vh}
#out{white-space:pre-wrap;background:#1c1c1c;margin:0 16px 16px;padding:12px;border-radius:8px;display:none;font-size:12px}
</style></head><body>
<header><h1>${esc(title)}</h1><span id="cnt" style="color:var(--dim);font-size:13px"></span><button class="o" onclick="only=!only;draw()">Favourites only</button><button onclick="send()">Send my picks</button></header>
<div id="out"></div><div class="g" id="g"></div><div id="lb" onclick="this.style.display='none'"><img id="lbi"></div>
<script>
var P=[$items],K='proof_'+${"\"" + esc(title).replace("\"", "") + "\""},st={},only=false,mail="${esc(email)}";
try{st=JSON.parse(localStorage.getItem(K)||'{}')}catch(e){}
function save(){try{localStorage.setItem(K,JSON.stringify(st))}catch(e){}}
function g(i){return st[i]||(st[i]={f:0,r:0,t:''})}
function draw(){var h='',n=0;P.forEach(function(p,i){var s=g(i);if(s.f)n++;if(only&&!s.f)return;var stars='';for(var k=1;k<=5;k++)stars+='<span class="'+(s.r>=k?'on':'')+'" onclick="star('+i+','+k+')">★</span>';
h+='<div class="c"><img loading="lazy" src="'+p.s+'" onclick="big(\''+p.s+'\')"><div class="r"><span class="h '+(s.f?'on':'')+'" onclick="fav('+i+')">♥</span><span class="s">'+stars+'</span><span style="margin-left:auto;color:#777">'+(i+1)+'</span></div><input class="n" placeholder="Note" value="'+(s.t||'').replace(/"/g,'&quot;')+'" oninput="note('+i+',this.value)"></div>'});
document.getElementById('g').innerHTML=h;document.getElementById('cnt').textContent=n+' of '+P.length+' favourites'}
function fav(i){g(i).f=g(i).f?0:1;save();draw()}function star(i,k){g(i).r=g(i).r==k?0:k;save();draw()}function note(i,v){g(i).t=v;save()}
function big(s){document.getElementById('lbi').src=s;document.getElementById('lb').style.display='flex'}
function send(){var t='AlcorCut proofing: '+document.title+'\n';P.forEach(function(p,i){var s=g(i);if(s.f||s.r||s.t)t+=(s.f?'PICK':'NOTE')+' | '+p.n+' | '+s.r+' | '+(s.t||'').replace(/\n/g,' ')+'\n'});
var o=document.getElementById('out');o.style.display='block';o.textContent=t;try{navigator.clipboard.writeText(t)}catch(e){}
if(mail)location.href='mailto:'+mail+'?subject='+encodeURIComponent('Picks: '+document.title)+'&body='+encodeURIComponent(t)}
draw();
</script></body></html>"""
    }

    /** Reads the client's list: (file name, pick, stars, note). */
    fun parse(text: String): List<Triple<String, Pair<Boolean, Int>, String>> = text.lines().mapNotNull { l ->
        val p = l.split('|').map { it.trim() }
        if (p.size < 3 || (p[0] != "PICK" && p[0] != "NOTE")) null else Triple(p[1], (p[0] == "PICK") to (p[2].toIntOrNull() ?: 0).coerceIn(0, 5), p.getOrElse(3) { "" })
    }
}

/** Make a proofing gallery of [photos]. */
@Composable
fun ProofingDialog(photos: List<LibPhoto>, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mark by remember { mutableStateOf("PROOF") }
    var size by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf<String?>(null) }
    val colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Pro.Fg, unfocusedTextColor = Pro.Fg, focusedBorderColor = Pro.Amber, focusedLabelColor = Pro.Amber, cursorColor = Pro.Amber)
    AlertDialog(onDismissRequest = { if (busy == null) onDone() }, containerColor = Ed.Panel,
        title = { Text("Client proofing gallery", color = Pro.Fg) },
        text = {
            Column {
                Text("${photos.size} photo(s) in a gallery the client opens in any web browser. They mark favourites, stars and notes and send the list back; paste it under Library → Client picks.", color = Pro.Dim, fontSize = 12.sp)
                OutlinedTextField(title, { title = it }, label = { Text("Gallery title (e.g. Hansen wedding)") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("Your e-mail (picks are sent to it)") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(mark, { mark = it }, label = { Text("Watermark (empty = none)") }, singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth())
                Text("Size", color = Pro.Fg, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Chips(listOf("1200 px", "2048 px", "3000 px"), size) { size = it }
                busy?.let { Text(it, color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = busy == null, onClick = {
                busy = "Making the gallery…"
                scope.launch {
                    val u = withContext(Dispatchers.IO) {
                        try { Proofing.build(context, photos, title, email.trim(), listOf(1200, 2048, 3000)[size], mark) { d, t -> busy = "Making the gallery… $d of $t" } } catch (_: Throwable) { null }
                    }
                    busy = null
                    if (u == null) android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Couldn't make the gallery"), android.widget.Toast.LENGTH_LONG).show()
                    else {
                        android.widget.Toast.makeText(context, com.dronedirector.veyrocutpro.ui.I18n.tr("Saved in Downloads/AlcorCut Pro — share it with your client"), android.widget.Toast.LENGTH_LONG).show()
                        try {
                            context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/zip")
                                .putExtra(android.content.Intent.EXTRA_STREAM, u).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                        } catch (_: Throwable) {}
                        onDone()
                    }
                }
            }) { Text(if (busy == null) "MAKE" else "WORKING…", color = Pro.Amber) }
        },
        dismissButton = { TextButton(enabled = busy == null, onClick = onDone) { Text("CANCEL", color = Pro.Dim) } },
    )
}

/** Paste the client's list: their picks become picks with their stars (and notes as keywords). */
@Composable
fun ClientPicksDialog(all: List<LibPhoto>, onDone: (Int?) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { onDone(null) }, containerColor = Ed.Panel,
        title = { Text("Client picks", color = Pro.Fg) },
        text = {
            Column {
                Text("Paste the list the client sent from the proofing gallery.", color = Pro.Dim, fontSize = 12.sp)
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().height(180.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Pro.Fg, unfocusedTextColor = Pro.Fg, focusedBorderColor = Pro.Amber, cursorColor = Pro.Amber))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rows = Proofing.parse(text)
                val byName = all.groupBy { it.name }
                var n = 0
                for ((name, pr, note) in rows) {
                    val keys = byName[name]?.map { it.uri.toString() } ?: continue
                    LibraryStore.update(context, keys) { m ->
                        m.copy(flag = if (pr.first) 1 else m.flag, rating = if (pr.second > 0) pr.second else m.rating,
                            keywords = if (note.isBlank()) m.keywords else (m.keywords + ("client: " + note.take(60).lowercase())).distinct())
                    }
                    n += keys.size
                }
                onDone(n)
            }) { Text("APPLY", color = Pro.Amber) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("CANCEL", color = Pro.Dim) } },
    )
}
