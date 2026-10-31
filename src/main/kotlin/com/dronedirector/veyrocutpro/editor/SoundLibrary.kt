package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import java.io.File

/** One built-in track (in assets/sound_library/<section>/<id>.m4a). */
data class LibTrack(val id: String, val title: String, val durationMs: Long)

/** A sound library section, e.g. Soundtracks or Nature sounds. */
data class LibSection(val folder: String, val title: String, val subtitle: String, val tracks: List<LibTrack>)

/**
 * Built-in sound libraries for the video editor. The tracks are owned by the app's author and
 * licensed for use in your videos. They ship inside the app (AAC 128 kbps) and are copied to the
 * app's storage the first time you add one, so the timeline, waveforms, preview and export all
 * treat them like any other audio file.
 */
object SoundLibrary {
    val sections: List<LibSection> = listOf(
        LibSection(
            "soundtracks", "Soundtracks", "Cinematic music for your edits",
            listOf(
                LibTrack("advisory", "Advisory", 385000L),
                LibTrack("and_we_run", "And We Run", 296720L),
                LibTrack("beyond_the_horizon", "Beyond the Horizon", 295800L),
                LibTrack("black_eagle", "Black Eagle", 327849L),
                LibTrack("colorful_noise", "Colorful Noise", 328680L),
                LibTrack("ethernal", "Ethernal", 223600L),
                LibTrack("faithful_end_credits", "Faithful (End Credits)", 217920L),
                LibTrack("feather_fall", "Feather Fall", 214960L),
                LibTrack("heaven_encounters", "Heaven Encounters", 115120L),
                LibTrack("higher_again", "Higher Again", 301400L),
                LibTrack("journey", "Journey", 174360L),
                LibTrack("let_there_be", "Let There Be", 256320L),
                LibTrack("lethal_weapons", "Lethal Weapons", 164640L),
                LibTrack("natures_roar", "Nature's Roar", 310200L),
                LibTrack("natures_voice", "Nature's Voice", 158000L),
                LibTrack("radioactive", "Radioactive", 178560L),
                LibTrack("rings_of_saturn", "Rings of Saturn", 169760L),
                LibTrack("rise_like_the_dawn", "Rise Like the Dawn", 203760L),
                LibTrack("row", "Row", 317520L),
                LibTrack("skybound_dreams", "Skybound Dreams", 212160L),
                LibTrack("spring", "Spring", 214160L),
                LibTrack("tales_of_the_brave", "Tales of the Brave", 243880L),
                LibTrack("the_flood", "The Flood", 195600L),
                LibTrack("the_haze", "The Haze", 198080L),
                LibTrack("there_back_again", "There & Back Again", 204000L),
                LibTrack("uprising", "Uprising", 323960L),
            ),
        ),
        // More sections: put the .m4a files in assets/sound_library/<folder>/ and list them here.
        LibSection("nature", "Nature sounds", "Wind, water, birds and ambience", emptyList()),
    )


    /** Where a track is kept (downloaded on first launch). */
    fun target(context: Context, folder: String, id: String) = File(File(context.filesDir, "sound_library/$folder").apply { mkdirs() }, "$id.m4a")

    /** The track's file, or null while it hasn't been downloaded yet. */
    fun file(context: Context, section: LibSection, t: LibTrack): Uri? {
        val f = target(context, section.folder, t.id)
        return if (f.exists() && f.length() > 0L) Uri.fromFile(f) else null
    }

    // ---- preview (listen before adding)

    private var player: MediaPlayer? = null
    var playingId: String? = null; private set

    fun togglePreview(context: Context, section: LibSection, t: LibTrack, onChange: (String?) -> Unit) {
        val wasThis = playingId == t.id
        stopPreview()
        if (wasThis) { onChange(null); return }
        try {
            val mp = MediaPlayer()
            val f = target(context, section.folder, t.id)
            if (!f.exists()) { onChange(null); return }
            mp.setDataSource(f.path)
            mp.setOnCompletionListener { stopPreview(); onChange(null) }
            mp.prepare()
            mp.start()
            player = mp
            playingId = t.id
            onChange(t.id)
        } catch (_: Throwable) {
            stopPreview(); onChange(null)
        }
    }

    fun stopPreview() {
        try { player?.stop() } catch (_: Throwable) {}
        try { player?.release() } catch (_: Throwable) {}
        player = null
        playingId = null
    }
}
