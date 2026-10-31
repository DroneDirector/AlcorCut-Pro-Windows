package com.dronedirector.veyrocutpro.editor

/**
 * Timeline exchange with desktop editors (Deliver → SEND TO DESKTOP EDITOR):
 *  - CMX 3600 EDL: cuts, dissolves and speed changes; opens in most desktop editors.
 *  - FCPXML 1.9: cuts and markers; opens in desktop editors that read FCPXML.
 *  - OpenTimelineIO (.otio, JSON): cuts, dissolves, speed and markers; written from the public
 *    OTIO file format (no OTIO code is used), readable by any OTIO-aware tool.
 *  - Subtitles: .srt and .vtt out, and .srt / .vtt in (as caption layers).
 * Only the edit travels: grades, effects and layers stay in AlcorCut Pro. Media is matched by file name,
 * so on the computer you point the editor at the folder with the original clips.
 */
object Interchange {
    private fun frames(ms: Long, fps: Int) = Math.round(ms * fps / 1000.0)

    private fun tc(frames: Long, fps: Int): String {
        val f = frames.coerceAtLeast(0)
        val ff = f % fps; val s = f / fps
        return "%02d:%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60, ff)
    }

    private fun reel(name: String) = name.substringBeforeLast('.').uppercase().replace(Regex("[^A-Z0-9]"), "").take(8).ifEmpty { "AX" }

    fun edl(title: String, clips: List<Clip>, fps: Int): String {
        val sb = StringBuilder()
        sb.append("TITLE: ").append(title.take(60)).append("\nFCM: NON-DROP FRAME\n\n")
        val starts = clipStarts(clips)
        var event = 1
        clips.forEachIndexed { i, c ->
            val recIn = frames(starts[i], fps)
            val recOut = recIn + frames(c.lengthMs, fps)
            val srcIn = frames(c.startMs, fps)
            val srcOut = srcIn + (recOut - recIn)
            val r = reel(c.name)
            val inOv = if (i > 0) overlapMs(clips, i - 1) else 0L
            if (inOv > 0) {
                // Dissolve from the previous clip into this one.
                val d = frames(inOv, fps)
                val prev = clips[i - 1]
                val pSrcOut = frames(prev.endMs - (inOv * prev.speed).toLong(), fps)
                sb.append("%03d  %-8s V     C        %s %s %s %s\n".format(event, reel(prev.name), tc(pSrcOut, fps), tc(pSrcOut, fps), tc(recIn, fps), tc(recIn, fps)))
                sb.append("%03d  %-8s V     D    %03d %s %s %s %s\n".format(event, r, d, tc(srcIn, fps), tc(srcOut, fps), tc(recIn, fps), tc(recOut, fps)))
            } else {
                sb.append("%03d  %-8s AA/V  C        %s %s %s %s\n".format(event, r, tc(srcIn, fps), tc(srcOut, fps), tc(recIn, fps), tc(recOut, fps)))
            }
            if (c.speed != 1f) sb.append("M2   %-8s %05.1f                %s\n".format(r, c.speed * fps, tc(srcIn, fps)))
            sb.append("* FROM CLIP NAME: ").append(c.name).append('\n')
            if (c.ramp != null) sb.append("* SPEED RAMP IN ALCORCUT PRO (NOT TRANSFERRED)\n")
            sb.append('\n')
            event++
        }
        return sb.toString()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun t(frames: Long, fps: Int) = "${frames * 100}/${fps * 100}s"

    fun fcpxml(title: String, clips: List<Clip>, fps: Int, width: Int, height: Int, markers: List<Long>, notes: Map<Long, String> = emptyMap()): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE fcpxml>\n<fcpxml version=\"1.9\">\n  <resources>\n")
        sb.append("    <format id=\"r1\" frameDuration=\"100/${fps * 100}s\" width=\"$width\" height=\"$height\"/>\n")
        val assets = LinkedHashMap<String, Pair<String, Clip>>()
        for (c in clips) if (c.name !in assets) assets[c.name] = "a${assets.size + 1}" to c
        for ((name, v) in assets) {
            val (id, c) = v
            sb.append("    <asset id=\"$id\" name=\"${esc(name)}\" start=\"0s\" duration=\"${t(frames(c.durationMs, fps), fps)}\" hasVideo=\"1\" hasAudio=\"1\" format=\"r1\">\n")
            sb.append("      <media-rep kind=\"original-media\" src=\"file:///AlcorCutPro/${esc(name)}\"/>\n    </asset>\n")
        }
        val total = frames(clips.sumOf { it.lengthMs }, fps)
        sb.append("  </resources>\n  <library>\n    <event name=\"AlcorCut Pro\">\n      <project name=\"${esc(title)}\">\n")
        sb.append("        <sequence format=\"r1\" duration=\"${t(total, fps)}\" tcStart=\"0s\" tcFormat=\"NDF\">\n          <spine>\n")
        var off = 0L
        var ms = 0L
        for (c in clips) {
            val len = frames(c.lengthMs, fps)
            val id = assets[c.name]!!.first
            sb.append("            <asset-clip ref=\"$id\" offset=\"${t(off, fps)}\" name=\"${esc(c.name)}\" start=\"${t(frames(c.startMs, fps), fps)}\" duration=\"${t(len, fps)}\" format=\"r1\">\n")
            if (c.speed != 1f && c.ramp == null) {
                sb.append("              <timeMap><timept time=\"0s\" value=\"0s\" interp=\"linear\"/><timept time=\"${t(len, fps)}\" value=\"${t(Math.round(len * c.speed.toDouble()), fps)}\" interp=\"linear\"/></timeMap>\n")
            }
            for (m in markers) if (m >= ms && m < ms + c.lengthMs) {
                val at = frames(c.startMs, fps) + frames(m - ms, fps)
                sb.append("              <marker start=\"${t(at, fps)}\" duration=\"100/${fps * 100}s\" value=\"${esc(notes[m] ?: "Marker")}\"/>\n")
            }
            sb.append("            </asset-clip>\n")
            off += len; ms += c.lengthMs
        }
        sb.append("          </spine>\n        </sequence>\n      </project>\n    </event>\n  </library>\n</fcpxml>\n")
        return sb.toString()
    }

    /** Subtitles (.srt) from the caption layers, in timeline order. */
    fun srt(layers: List<Layer>): String {
        fun ts(ms: Long): String { val m = ms.coerceAtLeast(0); return "%02d:%02d:%02d,%03d".format(m / 3_600_000, (m / 60_000) % 60, (m / 1000) % 60, m % 1000) }
        val caps = layers.filter { it.kind == LayerKind.TEXT && it.name == "Caption" && !it.wholeVideo && it.text.isNotBlank() }.sortedBy { it.startMs }
        val sb = StringBuilder()
        caps.forEachIndexed { i, l -> sb.append(i + 1).append('\n').append(ts(l.startMs)).append(" --> ").append(ts(l.endMs)).append('\n').append(l.text.trim()).append("\n\n") }
        return sb.toString()
    }

    /** Subtitles (.vtt, WebVTT) from the caption layers, in timeline order. */
    fun vtt(layers: List<Layer>): String {
        fun ts(ms: Long): String { val m = ms.coerceAtLeast(0); return "%02d:%02d:%02d.%03d".format(m / 3_600_000, (m / 60_000) % 60, (m / 1000) % 60, m % 1000) }
        val caps = layers.filter { it.kind == LayerKind.TEXT && it.name == "Caption" && !it.wholeVideo && it.text.isNotBlank() }.sortedBy { it.startMs }
        val sb = StringBuilder("WEBVTT\n\n")
        caps.forEach { l -> sb.append(ts(l.startMs)).append(" --> ").append(ts(l.endMs)).append('\n').append(l.text.trim()).append("\n\n") }
        return sb.toString()
    }

    /** Reads an .srt or .vtt file: (start ms, end ms, text) per cue. Styling tags are dropped. */
    fun parseSubs(text: String): List<Triple<Long, Long, String>> {
        val time = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")
        fun ms(m: MatchResult): Long {
            val (h, mi, se, f) = m.destructured
            return (h.ifEmpty { "0" }.toLong() * 3600 + mi.toLong() * 60 + se.toLong()) * 1000 + f.padEnd(3, '0').toLong()
        }
        val out = ArrayList<Triple<Long, Long, String>>()
        val blocks = text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n"))
        for (b in blocks) {
            val lines = b.trim().lines()
            val at = lines.indexOfFirst { "-->" in it }
            if (at < 0) continue
            val parts = lines[at].split("-->")
            val a = time.find(parts[0]) ?: continue
            val z = time.find(parts.getOrElse(1) { "" }) ?: continue
            val body = lines.drop(at + 1).joinToString("\n") { it.replace(Regex("<[^>]*>"), "").replace(Regex("\\{\\\\[^}]*\\}"), "").trim() }.trim()
            val s0 = ms(a); val s1 = ms(z)
            if (body.isNotEmpty() && s1 > s0) out.add(Triple(s0, s1, body))
        }
        return out.sortedBy { it.first }
    }

    /** OpenTimelineIO (.otio): one video track with clips, dissolves, speed and markers. */
    fun otio(title: String, clips: List<Clip>, fps: Int, markers: List<Long>, notes: Map<Long, String> = emptyMap()): String {
        fun rt(frames: Long) = org.json.JSONObject().put("OTIO_SCHEMA", "RationalTime.1").put("rate", fps.toDouble()).put("value", frames.toDouble())
        fun range(start: Long, dur: Long) = org.json.JSONObject().put("OTIO_SCHEMA", "TimeRange.1").put("start_time", rt(start)).put("duration", rt(dur))
        val items = org.json.JSONArray()
        var ms = 0L
        clips.forEachIndexed { i, c ->
            if (i > 0) {
                val ov = frames(overlapMs(clips, i - 1), fps)
                if (ov > 0) items.put(org.json.JSONObject().put("OTIO_SCHEMA", "Transition.1").put("name", "Dissolve")
                    .put("transition_type", "SMPTE_Dissolve").put("in_offset", rt(ov / 2)).put("out_offset", rt(ov - ov / 2)).put("metadata", org.json.JSONObject()))
            }
            val len = frames(c.lengthMs, fps)
            val src = frames(c.startMs, fps)
            val marks = org.json.JSONArray()
            for (m in markers) if (m >= ms && m < ms + c.lengthMs) {
                marks.put(org.json.JSONObject().put("OTIO_SCHEMA", "Marker.2").put("name", notes[m] ?: "Marker").put("color", "RED")
                    .put("marked_range", range(src + frames(m - ms, fps), 0)).put("metadata", org.json.JSONObject()))
            }
            val fx = org.json.JSONArray()
            if (c.speed != 1f && c.ramp == null) fx.put(org.json.JSONObject().put("OTIO_SCHEMA", "LinearTimeWarp.1").put("name", "Speed")
                .put("effect_name", "LinearTimeWarp").put("time_scalar", c.speed.toDouble()).put("metadata", org.json.JSONObject()))
            val ref = org.json.JSONObject().put("OTIO_SCHEMA", "ExternalReference.1").put("name", c.name)
                .put("target_url", "file:///AlcorCutPro/" + c.name).put("available_range", range(0, frames(c.durationMs, fps))).put("metadata", org.json.JSONObject())
            items.put(org.json.JSONObject().put("OTIO_SCHEMA", "Clip.2").put("name", c.name)
                .put("source_range", range(src, len))
                .put("media_references", org.json.JSONObject().put("DEFAULT_MEDIA", ref)).put("active_media_reference_key", "DEFAULT_MEDIA")
                .put("effects", fx).put("markers", marks).put("enabled", true)
                .put("metadata", org.json.JSONObject().put("veyrocut", org.json.JSONObject().put("speed_ramp", c.ramp != null))))
            ms += c.lengthMs
        }
        fun track(kind: String, children: org.json.JSONArray) = org.json.JSONObject().put("OTIO_SCHEMA", "Track.1").put("name", if (kind == "Video") "V1" else "A1")
            .put("kind", kind).put("children", children).put("effects", org.json.JSONArray()).put("markers", org.json.JSONArray()).put("enabled", true).put("metadata", org.json.JSONObject())
        val audio = org.json.JSONArray(items.toString())
        for (k in 0 until audio.length()) audio.getJSONObject(k).put("markers", org.json.JSONArray())
        val stack = org.json.JSONObject().put("OTIO_SCHEMA", "Stack.1").put("name", "tracks")
            .put("children", org.json.JSONArray().put(track("Video", items)).put(track("Audio", audio)))
            .put("effects", org.json.JSONArray()).put("markers", org.json.JSONArray()).put("enabled", true).put("metadata", org.json.JSONObject())
        return org.json.JSONObject().put("OTIO_SCHEMA", "Timeline.1").put("name", title)
            .put("global_start_time", rt(0)).put("tracks", stack).put("metadata", org.json.JSONObject()).toString(2).replace("\\/", "/") + "\n"
    }
}
