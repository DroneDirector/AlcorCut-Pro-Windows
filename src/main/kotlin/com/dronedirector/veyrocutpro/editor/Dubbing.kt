package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/**
 * AI dubbing: the (translated) captions spoken in another voice and laid out at the captions' times
 * as one audio track. English is spoken on the phone (Kokoro); other languages through Cloud AI
 * (ElevenLabs Multilingual v2 via fal.ai). Own code for the decoding, timing and mixing.
 */
object Dubbing {
    const val RATE = Voiceover.RATE   // 24 kHz mono, like the on-phone voices

    /** Languages offered: (label, ISO code). English uses the on-phone voices. */
    val LANGS = listOf(
        "English (on the phone)" to "en", "Danish" to "da", "Norwegian" to "no", "Swedish" to "sv", "German" to "de", "Spanish" to "es",
        "French" to "fr", "Italian" to "it", "Portuguese" to "pt", "Dutch" to "nl", "Polish" to "pl", "Finnish" to "fi",
    )

    /** Decodes compressed audio (MP3 / AAC) to 24 kHz mono floats. */
    fun decode(file: File): FloatArray? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(file.path)
            val t = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(t)
            val fmt = ex.getTrackFormat(t)
            val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0); codec.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val pcm = ArrayList<Float>()
            val info = MediaCodec.BufferInfo()
            var inDone = false; var outDone = false
            while (!outDone) {
                if (!inDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { val f = codec.outputFormat; rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                else if (o >= 0) {
                    val b = codec.getOutputBuffer(o)!!.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
                    val frames = b.remaining() / ch.coerceAtLeast(1)
                    for (k in 0 until frames) { var s = 0f; for (c in 0 until ch) s += b.get() / 32768f; pcm += s / ch }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
            }
            codec.stop(); codec.release()
            // Resample to 24 kHz (linear).
            if (pcm.isEmpty()) return null
            val outN = (pcm.size.toLong() * RATE / rate).toInt()
            return FloatArray(outN) { i -> val x = i.toDouble() * rate / RATE; val i0 = x.toInt().coerceAtMost(pcm.size - 1); val i1 = (i0 + 1).coerceAtMost(pcm.size - 1); val a = (x - i0).toFloat(); pcm[i0] * (1 - a) + pcm[i1] * a }
        } catch (_: Throwable) { return null } finally { ex.release() }
    }

    /**
     * Speaks each caption (start ms, end ms, text) and mixes them into one 24 kHz track. A line that
     * runs longer than its slot is spoken again a little faster. Returns the samples (from 0 ms).
     */
    fun build(
        context: Context, lines: List<Triple<Long, Long, String>>, lang: String, man: Boolean,
        onProgress: (Int) -> Unit, stop: () -> Boolean,
    ): FloatArray? {
        if (lines.isEmpty()) return null
        val onPhone = lang == "en"
        val kv = if (onPhone) Voiceover.voices.first { it.id == if (man) "am_michael" else "af_heart" } else null
        val cloudVoice = if (man) "George" else "Sarah"
        val total = ((lines.maxOf { it.second } + 4000) * RATE / 1000).toInt()
        val mix = FloatArray(total)
        val tmp = File(context.cacheDir, "dub_line.mp3")
        for ((i, l) in lines.withIndex()) {
            if (stop()) return null
            onProgress(i * 100 / lines.size)
            val (a, b, text) = l
            val next = lines.getOrNull(i + 1)?.first ?: (b + 1500)
            val slot = (maxOf(b, next) - a).coerceAtLeast(500)
            fun say(speed: Float): FloatArray? = if (kv != null) Voiceover.speak(context, text, kv, speed, {}, stop)
                else { tmp.writeBytes(com.dronedirector.veyrocutpro.camera.CloudAi.speak(context, text, cloudVoice, lang, speed)); decode(tmp) }
            var audio = say(1f) ?: continue
            val len = audio.size * 1000L / RATE
            if (len > slot) {
                val sp = (len.toFloat() / slot).coerceAtMost(if (onPhone) 1.35f else 1.2f)
                if (sp > 1.03f) say(sp)?.let { audio = it }
            }
            val off = (a * RATE / 1000).toInt()
            for (k in audio.indices) { val j = off + k; if (j < mix.size) mix[j] += audio[k] }
        }
        tmp.delete()
        onProgress(100)
        for (k in mix.indices) mix[k] = mix[k].coerceIn(-1f, 1f)
        return mix
    }
}
