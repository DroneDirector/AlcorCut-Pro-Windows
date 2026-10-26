package com.dronedirector.veyrocutpro.editor



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
