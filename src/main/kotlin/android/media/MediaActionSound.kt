package android.media

/** Camera sounds: on Windows they are made by the app's own shutter sounds (ShutterSounds). Own code. */
class MediaActionSound {
    fun load(sound: Int) {}
    fun play(sound: Int) {
        if (sound == SHUTTER_CLICK) com.dronedirector.veyrocutpro.camera.ShutterSounds.play(1)
    }
    fun release() {}
    companion object {
        const val SHUTTER_CLICK = 0
        const val FOCUS_COMPLETE = 1
        const val START_VIDEO_RECORDING = 2
        const val STOP_VIDEO_RECORDING = 3
        @JvmStatic fun mustPlayShutterSound(): Boolean = false
    }
}
