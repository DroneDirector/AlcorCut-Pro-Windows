package com.dronedirector.veyrocutpro.editor

/**
 * Edit templates (Media → EDIT TEMPLATES): one tap turns your clips into a finished-looking edit — cut on the
 * music's beat (when there is music), transitions on every cut, an opening title, an ending and, for some, an
 * overlay. Everything stays editable afterwards; undo brings the old edit back.
 */
data class EditTemplate(
    val name: String,
    val hint: String,
    /** Cut every N beats (needs a music track; without one the clips are kept as they are). */
    val everyBeats: Int,
    /** Transitions used on the cuts, in turn. */
    val transitions: List<TransitionType>,
    val transitionMs: Long,
    val title: String,
    val titleFont: LayerFont,
    val titleColor: Int,
    val titleAnim: LayerAnim,
    val ending: String?,
    /** 2.39 cinema bars over the whole video. */
    val bars: Boolean = false,
    val outline: Boolean = false,
)

object EditTemplates {
    val ALL = listOf(
        EditTemplate("Travel recap", "Upbeat cuts every 2 beats, slides and light leaks, a title and an ending.",
            2, listOf(TransitionType.NONE, TransitionType.WHIP_LEFT, TransitionType.NONE, TransitionType.LIGHT_LEAK), 400L,
            "TRAVEL DIARY", LayerFont.HEAVY, 0xFFFFFFFF.toInt(), LayerAnim.POP, "Thanks for watching"),
        EditTemplate("Drone reel", "Slow, wide and cinematic: cuts every 4 beats, soft dissolves and cinema bars.",
            4, listOf(TransitionType.CROSSFADE, TransitionType.BLUR_WIPE), 900L,
            "ABOVE IT ALL", LayerFont.LIGHT, 0xFFFFFFFF.toInt(), LayerAnim.FADE, null, bars = true),
        EditTemplate("Action", "Fast cuts on every beat with zoom punches and flashes, a big outlined title.",
            1, listOf(TransitionType.NONE, TransitionType.ZOOM_PUNCH, TransitionType.NONE, TransitionType.FLASH), 250L,
            "LET'S GO", LayerFont.HEAVY, 0xFFFFD60A.toInt(), LayerAnim.ZOOM, null, outline = true),
        EditTemplate("Calm & warm", "Gentle cuts every 8 beats with long dissolves and a serif title — for family days and weddings.",
            8, listOf(TransitionType.CROSSFADE), 1400L,
            "Our day", LayerFont.SERIF, 0xFFFFF4E0.toInt(), LayerAnim.FADE, "The end"),
        EditTemplate("Intro / opener", "Quick cuts on every beat with glitches, ending on a big title.",
            1, listOf(TransitionType.GLITCH, TransitionType.NONE), 300L,
            "CHANNEL NAME", LayerFont.HEAVY, 0xFFFFFFFF.toInt(), LayerAnim.BOUNCE, null, outline = true),
        EditTemplate("Old film", "Cuts every 4 beats with film burns, a typewriter title and an old-film overlay.",
            4, listOf(TransitionType.FILM_BURN, TransitionType.CROSSFADE), 700L,
            "Memories", LayerFont.MONO, 0xFFF2E6C8.toInt(), LayerAnim.TYPEWRITER, "Fin"),
    )

    /** Transitions on every cut of [clips] (none after the last clip). */
    fun withTransitions(clips: List<Clip>, t: EditTemplate): List<Clip> = clips.mapIndexed { i, c ->
        if (i == clips.lastIndex) c.copy(transition = Transition())
        else {
            val type = t.transitions[i % t.transitions.size]
            val ms = minOf(t.transitionMs, c.lengthMs / 2, clips[i + 1].lengthMs / 2).coerceAtLeast(1L)
            c.copy(transition = Transition(type = type, durationMs = ms))
        }
    }

    /** The template's layers for a [total] ms video. */
    fun layers(t: EditTemplate, total: Long): List<Layer> = buildList {
        val intro = t.name == "Intro / opener"
        val tStart = if (intro) (total - 3500L).coerceAtLeast(0L) else 300L
        add(Layer(kind = LayerKind.TEXT, text = t.title, size = if (t.titleFont == LayerFont.HEAVY) 0.13f else 0.1f, x = 0.5f, y = if (intro) 0.5f else 0.42f,
            font = t.titleFont, color = t.titleColor, shadow = !t.outline, stroke = if (t.outline) 0.1f else null, borderColor = if (t.outline) 0xFF000000.toInt() else null,
            startMs = tStart, endMs = (tStart + 3200L).coerceAtMost(total.coerceAtLeast(tStart + 1000L)), animIn = t.titleAnim, animOut = LayerAnim.FADE, animMs = 500L, name = "Title"))
        t.ending?.let { e ->
            val s = (total - 3000L).coerceAtLeast(0L)
            add(Layer(kind = LayerKind.TEXT, text = e, size = 0.07f, x = 0.5f, y = 0.5f, font = t.titleFont, color = t.titleColor, shadow = true,
                startMs = s, endMs = total, animIn = LayerAnim.FADE, animOut = LayerAnim.FADE, animMs = 600L, name = "Ending"))
        }
        if (t.bars) add(Layer(kind = LayerKind.FX, fx = FxLayers.BARS, text = "Cinema bars", fxA = 2.39f, color = 0xFF000000.toInt(), shadow = false, startMs = 0, endMs = total))
        if (t.name == "Old film") add(Layer(kind = LayerKind.FX, fx = FxLayers.RETRO, text = "Retro look", fxA = 3f, opacity = 0.8f, shadow = false, startMs = 0, endMs = total))
    }
}
