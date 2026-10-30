package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * Network storage (Synology and any other NAS or PC sharing folders over SMB): finding them on
 * the local network, opening their shared folders (as a guest first, signed in only when the
 * NAS asks for it) and copying files into the app. Own code; SMB through smbj and smbj-rpc.
 */
data class NasServer(val name: String, val host: String)

/** One entry in a shared folder. */
data class NasEntry(val name: String, val folder: Boolean, val size: Long)

/** What a browser shows besides folders. */
enum class NasKinds(val ext: Set<String>) {
    VIDEO_EDITOR(Nas.VIDEO + Nas.PHOTO + Nas.AUDIO),
    /** The video editor's start page: clips (photos become clips). */
    VIDEO_START(Nas.VIDEO + Nas.PHOTO),
    /** Choosing a folder (team projects): folders only. */
    FOLDERS(emptySet()),
    PHOTO_EDITOR(Nas.PHOTO + Nas.RAW),
}

object Nas {
    val VIDEO = setOf("mp4", "mov", "m4v", "mkv", "webm", "3gp", "avi", "mts", "m2ts", "ts", "mxf")
    val PHOTO = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "jxl", "gif", "bmp", "tif", "tiff", "dng")
    val AUDIO = setOf("mp3", "wav", "aac", "m4a", "flac", "ogg", "opus", "aif", "aiff", "wma")
    val RAW = setOf("dng", "cr2", "cr3", "crw", "nef", "nrw", "arw", "srf", "sr2", "raf", "orf", "rw2", "pef", "srw", "3fr", "iiq", "erf", "kdc", "mrw", "x3f", "rwl")

    fun isAudio(name: String) = name.substringAfterLast('.', "").lowercase() in AUDIO

    /** Result of opening a NAS. */
    sealed class Opened {
        class Ok(val link: NasLink) : Opened()
        /** The NAS wants a user name and password (wrong ones, or none saved). */
        class NeedsSignIn(val wrong: Boolean) : Opened()
        class Failed(val why: String) : Opened()
    }

    private fun client() = SMBClient(
        SmbConfig.builder().withTimeout(20, TimeUnit.SECONDS).withSoTimeout(30, TimeUnit.SECONDS).withMultiProtocolNegotiate(true).build()
    )

    /**
     * Opens [host]: with the given sign-in, else a saved one, else as a guest. Blocking — call off
     * the main thread.
     */
    fun open(context: Context, host: String, user: String? = null, password: CharArray? = null, domain: String? = null, useSaved: Boolean = true): Opened {
        val c = client()
        val conn = try { c.connect(host) } catch (t: Throwable) {
            c.close()
            return Opened.Failed("Couldn't reach $host — is it on the same network?")
        }
        fun tryAuth(ctx: AuthenticationContext): NasLink? = try {
            val s = conn.authenticate(ctx)
            val link = NasLink(host, c, conn, s)
            // A session alone isn't enough: the folder list must open too.
            link.shares(); link
        } catch (_: SMBApiException) { null } catch (_: Throwable) { null }
        if (user != null && password != null) {
            val l = tryAuth(AuthenticationContext(user, password, domain?.ifBlank { null } ?: ""))
            return if (l != null) Opened.Ok(l) else { conn.close(); c.close(); Opened.NeedsSignIn(wrong = true) }
        }
        if (useSaved) NasSignIns.use(context, host) { u, p, d -> tryAuth(AuthenticationContext(u, p, d)) }?.let { return Opened.Ok(it) }
        (tryAuth(AuthenticationContext.guest()) ?: tryAuth(AuthenticationContext.anonymous()))?.let { return Opened.Ok(it) }
        try { conn.close() } catch (_: Throwable) {}
        c.close()
        return Opened.NeedsSignIn(wrong = NasSignIns.has(context, host))
    }

    /** Where copied files are kept (inside the app, so projects keep working). */
    fun folder(context: Context) = File(context.filesDir, "nas").apply { mkdirs() }
}

/** An open NAS: its shared folders, their contents, and copying files out. */
class NasLink(val host: String, private val client: SMBClient, private val conn: Connection, private val session: Session) : Closeable {
    private val open = HashMap<String, DiskShare>()

    private fun share(name: String): DiskShare = open.getOrPut(name) { session.connectShare(name) as DiskShare }

    /** The shared folders (hidden admin shares left out). */
    fun shares(): List<String> = ServerService(SMBTransportFactories.SRVSVC.getTransport(session)).shares1
        .filter { (it.type and 0x0FFFFFFF) == 0 && !it.netName.endsWith("$") }
        .map { it.netName }
        .sortedBy { it.lowercase() }

    /** A folder's contents: folders first, then the files [kinds] can use. */
    fun list(shareName: String, path: String, kinds: NasKinds): List<NasEntry> =
        share(shareName).list(path).mapNotNull {
            val n = it.fileName
            if (n == "." || n == ".." || n.startsWith(".") || n == "@eaDir" || n == "#recycle") return@mapNotNull null
            val dir = (it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
            if (!dir && n.substringAfterLast('.', "").lowercase() !in kinds.ext) null
            else NasEntry(n, dir, it.endOfFile)
        }.sortedWith(compareBy({ !it.folder }, { it.name.lowercase() }))

    /** Copies one file into [dest]; [onBytes] gets the bytes copied so far. False = stopped. */
    fun copy(shareName: String, path: String, dest: File, keepGoing: () -> Boolean, onBytes: (Long) -> Unit): Boolean {
        val f = share(shareName).openFile(
            path, EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null,
        )
        val part = File(dest.parentFile, dest.name + ".part")
        try {
            f.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    var total = 0L
                    while (true) {
                        if (!keepGoing()) return false
                        val r = input.read(buf)
                        if (r < 0) break
                        out.write(buf, 0, r)
                        total += r
                        onBytes(total)
                    }
                }
            }
            if (dest.exists()) dest.delete()
            return part.renameTo(dest)
        } finally {
            try { f.close() } catch (_: Throwable) {}
            if (part.exists()) part.delete()
        }
    }

    // ---- Writing (team projects in a shared folder on the NAS).

    /** Size of a file, or -1 when it isn't there. */
    fun size(shareName: String, path: String): Long = try {
        val sh = share(shareName)
        if (!sh.fileExists(path)) -1L else sh.getFileInformation(path).standardInformation.endOfFile
    } catch (_: Throwable) { -1L }

    /** Every file and folder in a folder (no filter). */
    fun listAll(shareName: String, path: String): List<NasEntry> =
        share(shareName).list(path).mapNotNull {
            val n = it.fileName
            if (n == "." || n == "..") null
            else NasEntry(n, (it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L, it.endOfFile)
        }

    fun readBytes(shareName: String, path: String): ByteArray? = try {
        val sh = share(shareName)
        if (!sh.fileExists(path)) null
        else sh.openFile(path, EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
            .use { f -> f.inputStream.use { it.readBytes() } }
    } catch (_: Throwable) { null }

    /** Writes [input] to [path] (replacing it); false when stopped. */
    fun write(shareName: String, path: String, input: java.io.InputStream, keepGoing: () -> Boolean = { true }, onBytes: (Long) -> Unit = {}): Boolean {
        val sh = share(shareName)
        mkdirs(shareName, path.substringBeforeLast('\\', ""))
        val f = sh.openFile(path, EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OVERWRITE_IF, null)
        f.use {
            it.outputStream.use { out ->
                val buf = ByteArray(1 shl 20)
                var total = 0L
                while (true) {
                    if (!keepGoing()) return false
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r); total += r; onBytes(total)
                }
            }
        }
        return true
    }

    fun writeBytes(shareName: String, path: String, data: ByteArray) { write(shareName, path, data.inputStream()) }

    fun delete(shareName: String, path: String) { try { share(shareName).rm(path) } catch (_: Throwable) {} }

    fun mkdirs(shareName: String, path: String) {
        if (path.isEmpty()) return
        val sh = share(shareName)
        var cur = ""
        for (part in path.split('\\')) {
            if (part.isEmpty()) continue
            cur = if (cur.isEmpty()) part else "$cur\\$part"
            try { if (!sh.folderExists(cur)) sh.mkdir(cur) } catch (_: Throwable) {}
        }
    }

    override fun close() {
        for (s in open.values) try { s.close() } catch (_: Throwable) {}
        open.clear()
        try { session.close() } catch (_: Throwable) {}
        try { conn.close() } catch (_: Throwable) {}
        try { client.close() } catch (_: Throwable) {}
    }

    companion object {
        /** Was the error a refused sign-in or share (so asking to sign in makes sense)? */
        fun refused(t: Throwable): Boolean = t is SMBApiException &&
            (t.status == NtStatus.STATUS_ACCESS_DENIED || t.status == NtStatus.STATUS_LOGON_FAILURE)
    }
}

/**
 * Finds file servers on the local network: those announcing themselves (Bonjour / mDNS, as a
 * Synology does) and anything answering on the file-sharing port in the phone's own subnet.
 */
class NasFinder(private val context: Context) {
    val found = mutableStateListOf<NasServer>()
    var searching by mutableStateOf(false)
        private set
    private var job: Job? = null
    private var nsd: NsdManager? = null
    private var listener: NsdManager.DiscoveryListener? = null

    private fun add(s: NasServer) {
        val i = found.indexOfFirst { it.host == s.host }
        if (i < 0) found.add(s)
        // A proper name (from the announcement) beats a bare address.
        else if (found[i].name == found[i].host && s.name != s.host) found[i] = s
    }

    fun start(scope: CoroutineScope) {
        if (searching) return
        searching = true
        startNsd(scope)
        job = scope.launch(Dispatchers.IO) {
            try { scan() } catch (_: Throwable) {}
            withContext(Dispatchers.Main) { searching = false }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        try { listener?.let { nsd?.stopServiceDiscovery(it) } } catch (_: Throwable) {}
        listener = null
        searching = false
    }

    @Suppress("DEPRECATION")
    private fun startNsd(scope: CoroutineScope) {
        val m = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsd = m
        val queue = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        fun next() {
            if (resolving) return
            val s = queue.removeFirstOrNull() ?: return
            resolving = true
            try {
                m.resolveService(s, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, code: Int) { scope.launch(Dispatchers.Main) { resolving = false; next() } }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val h = info.host?.hostAddress
                        scope.launch(Dispatchers.Main) {
                            if (h != null && info.host is Inet4Address) add(NasServer(info.serviceName ?: h, h))
                            resolving = false; next()
                        }
                    }
                })
            } catch (_: Throwable) { resolving = false }
        }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) { scope.launch(Dispatchers.Main) { queue.addLast(info); next() } }
        }
        listener = l
        try { m.discoverServices("_smb._tcp.", NsdManager.PROTOCOL_DNS_SD, l) } catch (_: Throwable) { listener = null }
    }

    /** Knocks on the file-sharing port of every address in the phone's subnet (a /24 at most). */
    private suspend fun scan() = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return@withContext
        val me = lp.linkAddresses.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress } ?: return@withContext
        val b = me.address.address
        val gate = Semaphore(48)
        (1..254).filter { it != (b[3].toInt() and 0xFF) }.map { last ->
            async {
                gate.withPermit {
                    if (!isActive) return@withPermit
                    val ip = "${b[0].toInt() and 0xFF}.${b[1].toInt() and 0xFF}.${b[2].toInt() and 0xFF}.$last"
                    val open = try { Socket().use { it.connect(InetSocketAddress(ip, 445), 350) }; true } catch (_: Throwable) { false }
                    if (open) {
                        val name = try { InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip }?.substringBefore('.') } catch (_: Throwable) { null }
                        withContext(Dispatchers.Main) { add(NasServer(name ?: ip, ip)) }
                    }
                }
            }
        }.awaitAll()
    }
}

/**
 * Saved NAS sign-ins, encrypted on the phone (Android Keystore) and left out of every backup.
 * The app can use or remove them but never shows a password — only the server and user name.
 */
object NasSignIns {
    private const val FILE = "acluxpro_nas_secure"

    /** What may be shown: server and user name. */
    data class Shown(val host: String, val user: String)

    val saved = mutableStateListOf<Shown>()

    private fun secure(c: Context) = try {
        val alias = androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC)
        androidx.security.crypto.EncryptedSharedPreferences.create(
            FILE, alias, c,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Throwable) { null }

    fun load(c: Context) {
        val p = secure(c)
        saved.clear()
        if (p == null) return
        p.all.keys.filter { it.startsWith("u:") }.sorted().forEach { k ->
            val host = k.removePrefix("u:")
            saved.add(Shown(host, p.getString(k, "") ?: ""))
        }
    }

    fun has(c: Context, host: String) = secure(c)?.contains("u:$host") == true

    /** Runs [block] with the saved sign-in for [host] (if any); the password never leaves here as text. */
    internal fun <T> use(c: Context, host: String, block: (String, CharArray, String) -> T?): T? {
        val p = secure(c) ?: return null
        val u = p.getString("u:$host", null) ?: return null
        val pw = (p.getString("p:$host", null) ?: return null).toCharArray()
        return try { block(u, pw, p.getString("d:$host", "") ?: "") } finally { pw.fill('\u0000') }
    }

    fun save(c: Context, host: String, user: String, password: CharArray, domain: String) {
        secure(c)?.edit()?.putString("u:$host", user)?.putString("p:$host", String(password))?.putString("d:$host", domain)?.apply()
        load(c)
    }

    fun remove(c: Context, host: String) {
        secure(c)?.edit()?.remove("u:$host")?.remove("p:$host")?.remove("d:$host")?.apply()
        load(c)
    }
}

/** A file picked on the NAS, to copy in. */
data class NasPick(val share: String, val path: String, val name: String, val size: Long)

/** Copies the picked files into the app, one after another; [onDone] gets the copies (as file Uris). */
fun nasCopy(
    context: Context, link: NasLink, picks: List<NasPick>, scope: CoroutineScope,
    keepGoing: () -> Boolean, onProgress: (file: Int, fraction: Float) -> Unit, onDone: (List<Uri>, String?) -> Unit,
) = scope.launch(Dispatchers.IO) {
    val out = ArrayList<Uri>()
    var err: String? = null
    val dir = File(Nas.folder(context), link.host.replace(Regex("[^A-Za-z0-9._-]"), "_")).apply { mkdirs() }
    for ((i, p) in picks.withIndex()) {
        if (!keepGoing()) break
        val sub = File(dir, p.share.replace(Regex("[^A-Za-z0-9._ -]"), "_")).apply { mkdirs() }
        var dest = File(sub, p.name)
        // Same name, different file: keep both.
        if (dest.exists() && dest.length() != p.size) {
            var n = 2
            val base = p.name.substringBeforeLast('.'); val ext = p.name.substringAfterLast('.', "")
            while (dest.exists()) { dest = File(sub, "$base ($n)" + if (ext.isNotEmpty()) ".$ext" else ""); n++ }
        }
        if (dest.exists() && dest.length() == p.size) { out.add(Uri.fromFile(dest)); continue }
        try {
            val ok = link.copy(p.share, p.path, dest, keepGoing) { b -> onProgress(i, if (p.size > 0) b.toFloat() / p.size else 0f) }
            if (ok) out.add(Uri.fromFile(dest))
        } catch (t: Throwable) {
            err = "Couldn't copy ${p.name}" + (t.message?.let { " — $it" } ?: "")
        }
    }
    withContext(Dispatchers.Main) { onDone(out, err) }
}
