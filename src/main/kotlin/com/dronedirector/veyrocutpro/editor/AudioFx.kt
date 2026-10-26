package com.dronedirector.veyrocutpro.editor



enum class AudioChannels(val label: String) { STEREO("Stereo"), MONO("Mono"), LEFT("Left only"), RIGHT("Right only"), SWAP("Swap L/R") }

/** Per-clip (and music) audio processing: EQ, dynamics, channels, fades and ducking. */
data class AudioFx(
    val low: Float = 0f,          // dB, shelf at 120 Hz
    val mid: Float = 0f,          // dB, peak at 1 kHz
    val high: Float = 0f,         // dB, shelf at 8 kHz
    val lowCut: Boolean = false,  // high-pass 80 Hz (wind, rumble, handling noise)
    val voice: Boolean = false,   // speech clarity: 100 Hz high-pass + presence lift
    val compressor: Float = 0f,   // 0..1
    val gate: Float = 0f,         // 0..1 noise gate
    val pan: Float = 0f,          // -1 left .. +1 right
    val channels: AudioChannels = AudioChannels.STEREO,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val duckMusic: Boolean = false,
    val limiter: Boolean = true,
    /** Voice noise reduction on export: 0 off, 1 light, 2 strong, 3 voice isolation (AI), 4 studio voice (AI). */
    val denoise: Int = 0,
    /** Repair on export: remove clicks / crackle, and take room echo down (0..1). */
    val declick: Boolean = false,
    val dereverb: Float = 0f,
    /** Dialogue leveller (0..1): rides the level so quiet and loud speech come out even. */
    val leveler: Float = 0f,
    /** Parametric EQ (on top of the three quick bands); null = off. */
    val bands: List<EqBand>? = null,
    /** De-esser (0..1): tames sharp "s" and "sh" sounds. */
    val deess: Float = 0f,
) {
    val isNeutral get() = this == AudioFx() || this == AudioFx(limiter = false)
}

/** One parametric EQ band. type: 0 bell, 1 low shelf, 2 high shelf, 3 low cut, 4 high cut. */
data class EqBand(val type: Int = 0, val freq: Float = 1000f, val gain: Float = 0f, val q: Float = 1f, val on: Boolean = true) {
    companion object { val TYPES = listOf("Bell", "Low shelf", "High shelf", "Low cut", "High cut") }
}

enum class AudioPreset(val label: String, val fx: AudioFx) {
    FLAT("Flat", AudioFx()),
    VOICE("Voice", AudioFx(low = -2f, mid = 1f, high = 2f, voice = true, compressor = 0.45f, gate = 0.2f)),
    INTERVIEW("Interview", AudioFx(low = -3f, mid = 1.5f, high = 1.5f, voice = true, compressor = 0.6f, gate = 0.35f, channels = AudioChannels.MONO)),
    WIND("Wind cut", AudioFx(low = -6f, lowCut = true, gate = 0.1f)),
    WARM("Warm", AudioFx(low = 3f, mid = 0.5f, high = -2f)),
    BRIGHT("Bright", AudioFx(low = -1f, high = 4f)),
    BASS("Bass boost", AudioFx(low = 7f, mid = -1f)),
    RADIO("Radio", AudioFx(low = -12f, mid = 6f, high = -10f, lowCut = true, compressor = 0.7f, channels = AudioChannels.MONO)),
    LOUD("Loud & punchy", AudioFx(low = 2f, high = 1.5f, compressor = 0.85f)),
}
