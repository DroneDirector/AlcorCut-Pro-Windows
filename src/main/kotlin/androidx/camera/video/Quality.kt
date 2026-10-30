package androidx.camera.video

/** Video sizes by name (4K, 1080p, 720p, 480p). */
class Quality private constructor(val name: String, val width: Int, val height: Int) {
    companion object {
        val UHD = Quality("UHD", 3840, 2160); val FHD = Quality("FHD", 1920, 1080)
        val HD = Quality("HD", 1280, 720); val SD = Quality("SD", 720, 480)
        val HIGHEST = UHD; val LOWEST = SD
    }
}
