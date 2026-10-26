package com.dronedirector.veyrocutpro.camera



enum class CaptureMode(val label: String, val isVideo: Boolean, val isPro: Boolean) {
    PORTRAIT("PORTRAIT", false, false),
    AI_PORTRAIT("AI PORTRAIT", false, false),
    PANORAMA("PANO", false, false),
    LONG_EXPOSURE("LONG EXP", false, false),
    TIMELAPSE("TIME-LAPSE", true, false),
    HYPERLAPSE("HYPERLAPSE", true, false),
    SLOW_MO("SLOW-MO", true, false),
    SUPER_SLOW_MO("SUPER SLO-MO", true, false),
    BURST("BURST", false, false),
    PHOTO("PHOTO", false, false),
    PRO_PHOTO("PRO", false, true),
    VIDEO("VIDEO", true, false),
    PRO_VIDEO("PRO VIDEO", true, true),
    LIVE("LIVE", false, false);

    /** Normal video modes: CameraX VideoCapture with sound. */
    val isNormalVideo: Boolean get() = this == VIDEO || this == PRO_VIDEO
    /** Frames picked on the GPU and encoded at 30 fps. */
    val isLapse: Boolean get() = this == TIMELAPSE || this == HYPERLAPSE
    val isSlowMo: Boolean get() = this == SLOW_MO || this == SUPER_SLOW_MO
    val isSmart: Boolean get() = this in SMART
    /** Background-blur modes: PORTRAIT (blur after the shot) and AI PORTRAIT (also blurs the viewfinder live). */
    val isPortrait: Boolean get() = this == PORTRAIT || this == AI_PORTRAIT

    companion object {
        /** Mode groups, in the order they appear in the mode switch. */
        val SMART = listOf(PORTRAIT, AI_PORTRAIT, PANORAMA, LONG_EXPOSURE, HYPERLAPSE, TIMELAPSE, SLOW_MO, SUPER_SLOW_MO, BURST)
        val REGULAR = listOf(PHOTO, PRO_PHOTO, VIDEO, PRO_VIDEO, LIVE)
    }
}
