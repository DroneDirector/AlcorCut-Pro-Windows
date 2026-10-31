package com.dronedirector.veyrocutpro.editor

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Audio-only export formats. Every one is free to use in a paid app: WAV, AIFF and FLAC are open
 * formats written by the app itself; Opus is royalty-free (Android's built-in encoder); AAC uses
 * the phone's built-in encoder (the licence is covered by the device maker).
 * MP3 is not offered: Android has no MP3 encoder and the common one (LAME) is LGPL.
 */
enum class AudioExportFormat(val label: String, val ext: String, val mime: String, val minSdk: Int = 0, val lossless: Boolean = false, val kbps: Int = 0) {
    M4A("AAC .m4a", "m4a", "audio/mp4", kbps = 128),
    WAV16("WAV 16-bit", "wav", "audio/x-wav", lossless = true),
    WAV24("WAV 24-bit", "wav", "audio/x-wav", lossless = true),
    WAV32F("WAV 32-bit float", "wav", "audio/x-wav", lossless = true),
    AIFF("AIFF 24-bit", "aiff", "audio/x-aiff", lossless = true),
    FLAC("FLAC (lossless)", "flac", "audio/flac", lossless = true),
    OPUS("Opus .ogg", "ogg", "audio/ogg", minSdk = 29, kbps = 160);

    val available: Boolean get() = android.os.Build.VERSION.SDK_INT >= minSdk

    /** Rough bytes per second for the size estimate (stereo, 48 kHz). */
    fun bytesPerSecond(): Double = when (this) {
        WAV16 -> 192_000.0; WAV24, AIFF -> 288_000.0; WAV32F -> 384_000.0
        FLAC -> 170_000.0
        else -> kbps * 125.0
    }
}

/**
 * Passes the finished mix through unchanged and copies it to [file] as raw PCM, so lossless
 * formats are made from the mix itself rather than from a compressed copy.
 */
@UnstableApi
class PcmTap(private val file: File) : BaseAudioProcessor() {
    @Volatile var rate = 0; private set
    @Volatile var channels = 0; private set
    @Volatile var isFloat = false; private set
    @Volatile var bytes = 0L; private set
    private var os: OutputStream? = null
    private var chunk = ByteArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT)
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        rate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining()
        if (n == 0) return
        if (chunk.size < n) chunk = ByteArray(n)
        inputBuffer.get(chunk, 0, n)
        synchronized(this) {
            val o = os ?: BufferedOutputStream(FileOutputStream(file), 1 shl 16).also { os = it }
            o.write(chunk, 0, n)
            bytes += n
        }
        replaceOutputBuffer(n).put(chunk, 0, n).flip()
    }

    fun close() = synchronized(this) { try { os?.flush(); os?.close() } catch (_: Throwable) {}; os = null }

    override fun onReset() { close() }
}

object AudioFiles {
    /** Writes the captured [raw] mix as [fmt] to [out]. Returns null on success, else why not. */
    fun write(raw: File, tap: PcmTap, fmt: AudioExportFormat, out: File): String? {
        if (tap.bytes <= 0 || tap.rate <= 0 || tap.channels <= 0) return "No sound was captured"
        return try {
            when (fmt) {
                AudioExportFormat.OPUS -> return opus(raw, tap, out)
                else -> BufferedOutputStream(FileOutputStream(out), 1 shl 16).use { os ->
                    when (fmt) {
                        AudioExportFormat.WAV16 -> PcmFiles.writeWav(raw, tap.isFloat, tap.rate, tap.channels, 16, os)
                        AudioExportFormat.WAV24 -> PcmFiles.writeWav(raw, tap.isFloat, tap.rate, tap.channels, 24, os)
                        AudioExportFormat.WAV32F -> PcmFiles.writeWav(raw, tap.isFloat, tap.rate, tap.channels, 32, os)
                        AudioExportFormat.AIFF -> PcmFiles.writeAiff(raw, tap.isFloat, tap.rate, tap.channels, 24, os)
                        AudioExportFormat.FLAC -> PcmFiles.writeFlac(raw, tap.isFloat, tap.rate, tap.channels, if (tap.isFloat) 24 else 16, os)
                        else -> {}
                    }
                }
            }
            null
        } catch (t: Throwable) { "Couldn't write the ${fmt.label} file: ${t.message}" }
    }

    /** Opus in an Ogg file with Android's encoder (Android 10+). The mix arrives at 48 kHz. */
    private fun opus(raw: File, tap: PcmTap, out: File): String? {
        if (android.os.Build.VERSION.SDK_INT < 29) return "Opus needs Android 10 or newer"
        val ch = tap.channels.coerceAtMost(2)
        val pcm = File(raw.parentFile, raw.name + ".s16")
        BufferedOutputStream(FileOutputStream(pcm), 1 shl 16).use { PcmFiles.toPcm16(raw, tap.isFloat, tap.channels, it) }
        val rate = tap.rate
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, rate, ch).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 160_000)
        }
        val codec = try { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS) } catch (t: Throwable) { pcm.delete(); return "This phone has no Opus encoder" }
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
        try {
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var track = -1
            var inputDone = false
            var outputDone = false
            var framesIn = 0L
            val frameBytes = 2 * tap.channels
            val src = pcm.inputStream().buffered(1 shl 16)
            val tmp = ByteArray(1 shl 14)
            while (!outputDone) {
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)!!
                        buf.clear()
                        // Down-mix anything above stereo to the first two channels.
                        val want = (buf.capacity() / (2 * ch)) * frameBytes
                        val n = src.read(tmp, 0, minOf(tmp.size / frameBytes * frameBytes, want))
                        if (n <= 0) {
                            codec.queueInputBuffer(ii, 0, 0, framesIn * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val frames = n / frameBytes
                            for (f in 0 until frames) for (c in 0 until ch) {
                                val b = f * frameBytes + c * 2
                                buf.put(tmp[b]); buf.put(tmp[b + 1])
                            }
                            codec.queueInputBuffer(ii, 0, frames * ch * 2, framesIn * 1_000_000L / rate, 0)
                            framesIn += frames
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { track = muxer.addTrack(codec.outputFormat); muxer.start() }
                    oi >= 0 -> {
                        val ob = codec.getOutputBuffer(oi)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && track >= 0) {
                            ob.position(info.offset); ob.limit(info.offset + info.size)
                            muxer.writeSampleData(track, ob, info)
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            src.close()
            return null
        } catch (t: Throwable) {
            return "Opus export failed: ${t.message}"
        } finally {
            try { codec.stop() } catch (_: Throwable) {}
            codec.release()
            try { muxer.stop() } catch (_: Throwable) {}
            try { muxer.release() } catch (_: Throwable) {}
            pcm.delete()
        }
    }
}
