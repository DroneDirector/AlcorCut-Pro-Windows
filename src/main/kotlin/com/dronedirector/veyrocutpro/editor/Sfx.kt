package com.dronedirector.veyrocutpro.editor



/**
 * Sound effects that can be put on a cut (Transitions → Audio transitions → SOUND EFFECTS).
 * [hitMs] is the moment in the sound that lands on the cut: 0 for impacts (they start on the cut),
 * the full length for risers (they build up to the cut).
 */
enum class SfxGroup(val label: String) { HITS("Hits & impacts"), WHOOSH("Whooshes & air"), BUILD("Builds & risers"), OTHER("Stylized & sci-fi") }

enum class TransitionSfx(val label: String, val detail: String, val lengthMs: Int, val hitMs: Int, val group: SfxGroup) {
    IMPACT("Cinematic impact", "Deep thump with a sharp crack and a rumbling tail", 2500, 0, SfxGroup.HITS),
    BOOM("Cinematic boom", "Huge low boom that rolls out", 3500, 0, SfxGroup.HITS),
    TRAILER_HIT("Trailer hit", "Boom with a metallic ring, like a movie trailer", 3000, 0, SfxGroup.HITS),
    BRAAM("Braam", "Huge brassy horn blast, blockbuster-trailer style", 3000, 0, SfxGroup.HITS),
    DRUM_HIT("Drum hit", "Big cinematic drum with room sound", 1800, 0, SfxGroup.HITS),
    PUNCH("Punch", "Quick swish that ends in a hard hit", 650, 400, SfxGroup.HITS),
    THUNDER("Thunder", "Crack of thunder rolling away", 4000, 0, SfxGroup.HITS),
    HEARTBEAT("Heartbeat", "Two low thumps — lub-dub", 1200, 0, SfxGroup.HITS),
    SUB_DROP("Sub drop", "Low tone that falls away under the cut", 1800, 0, SfxGroup.HITS),

    WHOOSH("Whoosh", "Air rushing past, left to right", 1000, 500, SfxGroup.WHOOSH),
    SWOOSH("Fast swoosh", "Short, quick swish", 450, 220, SfxGroup.WHOOSH),
    DEEP_WHOOSH("Deep whoosh", "Heavy, low pass-by", 1400, 700, SfxGroup.WHOOSH),
    SWIRL("Swirl", "Spinning, circling air that lands on the cut", 1500, 1000, SfxGroup.WHOOSH),
    WIND_GUST("Wind gust", "A gust of wind that swells and fades", 2200, 1100, SfxGroup.WHOOSH),

    RISER("Riser", "Tension that builds up into the cut", 2500, 2500, SfxGroup.BUILD),
    REVERSE("Reverse cymbal", "Swelling shimmer that sucks into the cut", 2000, 2000, SfxGroup.BUILD),
    TICK_RISER("Clock tick build", "Ticking that speeds up into the cut", 3000, 3000, SfxGroup.BUILD),
    TAPE_STOP("Tape stop", "Music slowing to a halt into the cut", 1500, 1500, SfxGroup.BUILD),

    GLITCH("Glitch", "Digital stutters and crackles", 700, 80, SfxGroup.OTHER),
    SCRATCH("Record scratch", "Quick DJ scratch back and forth", 600, 300, SfxGroup.OTHER),
    LASER("Laser zap", "Sci-fi zap", 500, 0, SfxGroup.OTHER),
    SHIMMER("Magic shimmer", "Sparkling bell tones", 2000, 150, SfxGroup.OTHER),
    SHUTTER("Camera shutter", "Mechanical shutter click", 400, 0, SfxGroup.OTHER),
    STATIC("TV static", "Burst of analogue noise", 900, 450, SfxGroup.OTHER),
}
