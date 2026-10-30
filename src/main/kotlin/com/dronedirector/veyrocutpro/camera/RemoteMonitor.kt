package com.dronedirector.veyrocutpro.camera

import android.content.Context
import android.graphics.Bitmap
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Remote monitor: a small web page served from this phone on the local Wi-Fi. Open the address
 * on a second phone, tablet or computer (any browser, no app needed) to watch the live picture
 * and control the camera. A 4-digit PIN in the address keeps other people on the network out.
 */
class RemoteMonitor(
    private val context: Context,
    /** Latest viewfinder frame as JPEG (called on a background thread; may return null). */
    private val frame: () -> ByteArray?,
    /** Status line for the page, as JSON. */
    private val status: () -> String,
    /** A button pressed on the page. */
    private val command: (String) -> Unit,
) {
    @Volatile private var server: ServerSocket? = null
    @Volatile var pin: String = ""; private set
    val port = 8765
    private val viewers = AtomicInteger(0)
    val viewerCount: Int get() = viewers.get()
    val running: Boolean get() = server != null

    fun start(): Boolean {
        if (server != null) return true
        pin = (1000 + java.security.SecureRandom().nextInt(9000)).toString()
        val ss = try { ServerSocket(port) } catch (_: Throwable) { return false }
        server = ss
        thread(name = "RemoteMonitor", isDaemon = true) {
            while (!ss.isClosed) {
                val sock = try { ss.accept() } catch (_: Throwable) { break }
                thread(isDaemon = true) { handle(sock) }
            }
        }
        return true
    }

    fun stop() {
        try { server?.close() } catch (_: Throwable) {}
        server = null
    }

    /** The address to type on the other device, or null when not on Wi-Fi / hotspot. */
    fun address(): String? {
        val ip = localIp() ?: return null
        return "http://$ip:$port/$pin"
    }

    private fun localIp(): String? = localIpAddress()

    private fun handle(sock: Socket) {
        try {
            sock.soTimeout = 15_000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val first = reader.readLine() ?: return
            // Skip headers.
            while (true) { val l = reader.readLine() ?: break; if (l.isEmpty()) break }
            val path = first.split(" ").getOrNull(1) ?: "/"
            val out = sock.getOutputStream()
            val parts = path.trimStart('/').split('/', '?')
            if (parts.firstOrNull() != pin) { send(out, 403, "text/plain", "Wrong or missing PIN.".toByteArray()); return }
            when (parts.getOrNull(1) ?: "") {
                "" -> send(out, 200, "text/html; charset=utf-8", page().toByteArray())
                "status" -> send(out, 200, "application/json", status().toByteArray())
                "frame.jpg" -> {
                    val jpg = frame()
                    if (jpg == null) send(out, 503, "text/plain", ByteArray(0)) else send(out, 200, "image/jpeg", jpg)
                }
                "stream" -> stream(out)
                "cmd" -> {
                    val c = parts.getOrNull(2) ?: ""
                    if (c.matches(Regex("[a-z0-9_]{1,24}"))) command(c)
                    send(out, 200, "application/json", "{\"ok\":true}".toByteArray())
                }
                else -> send(out, 404, "text/plain", ByteArray(0))
            }
        } catch (_: Throwable) {
        } finally {
            try { sock.close() } catch (_: Throwable) {}
        }
    }

    /** Live picture as MJPEG (about 8 frames a second). */
    private fun stream(out: OutputStream) {
        viewers.incrementAndGet()
        try {
            out.write(("HTTP/1.1 200 OK\r\nCache-Control: no-cache\r\nConnection: close\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n\r\n").toByteArray())
            while (server != null) {
                val jpg = frame()
                if (jpg != null) {
                    out.write("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpg.size}\r\n\r\n".toByteArray())
                    out.write(jpg)
                    out.write("\r\n".toByteArray())
                    out.flush()
                }
                Thread.sleep(120)
            }
        } catch (_: SocketException) {
        } catch (_: Throwable) {
        } finally {
            viewers.decrementAndGet()
        }
    }

    private fun send(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val reason = when (code) { 200 -> "OK"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "Unavailable" }
        out.write("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nCache-Control: no-cache\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(body)
        out.flush()
    }

    private fun page(): String = """
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>AlcorCut Pro · Remote</title>
<style>
:root{--a:#FFB300}
*{box-sizing:border-box}body{margin:0;background:#0b0c0e;color:#fff;font:14px system-ui,sans-serif}
#v{display:block;width:100%;max-height:72vh;object-fit:contain;background:#000}
#bar{display:flex;gap:10px;align-items:center;justify-content:space-between;padding:10px 12px;font:13px ui-monospace,monospace}
#rec{color:#ff3b30;font-weight:700}
.row{display:flex;flex-wrap:wrap;gap:8px;padding:0 12px 14px;justify-content:center}
button{background:#1a1d22;color:#fff;border:1px solid #333;border-radius:10px;padding:12px 16px;font:600 14px system-ui;min-width:64px}
button:active{border-color:var(--a)}
#sh{background:var(--a);color:#000;border:0;border-radius:50%;width:76px;height:76px;font-size:13px}
#sh.on{background:#ff3b30;color:#fff}
small{color:#999;display:block;text-align:center;padding-bottom:16px}
</style></head><body>
<img id="v" src="stream" alt="Live view">
<div id="bar"><span id="mode">—</span><span id="rec"></span><span id="exp"></span></div>
<div class="row"><button onclick="c('zoom_out')">− ZOOM</button><button id="sh" onclick="c('shutter')">SHUTTER</button><button onclick="c('zoom_in')">ZOOM +</button></div>
<div class="row"><button onclick="c('mode_photo')">PHOTO</button><button onclick="c('mode_video')">VIDEO</button><button onclick="c('mode_pro_video')">PRO VIDEO</button><button onclick="c('af')">AUTOFOCUS</button><button onclick="c('ae_lock')">AE LOCK</button></div>
<small>AlcorCut Pro remote monitor · keep both devices on the same Wi-Fi</small>
<script>
function c(x){fetch('cmd/'+x).then(s)}
function s(){fetch('status').then(r=>r.json()).then(j=>{
 document.getElementById('mode').textContent=j.mode+(j.zoom?' · '+j.zoom:'');
 document.getElementById('rec').textContent=j.recording?('● REC '+j.time):'';
 document.getElementById('exp').textContent=j.exposure||'';
 document.getElementById('sh').className=j.recording?'on':'';
 document.getElementById('sh').textContent=j.recording?'STOP':(j.video?'RECORD':'SHUTTER');
}).catch(()=>{})}
setInterval(s,1000);s();
document.getElementById('v').onerror=function(){var i=this;setTimeout(function(){i.src='stream?'+Date.now()},1500)};
</script></body></html>
""".trimIndent()

    companion object {
        /** This phone's address on the local Wi-Fi / hotspot, or null. */
        fun localIpAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("swlan")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        } catch (_: Throwable) { null }

        fun jpeg(b: Bitmap, quality: Int = 60): ByteArray =
            ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
    }
}
