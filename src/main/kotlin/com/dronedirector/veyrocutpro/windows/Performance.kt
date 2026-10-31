package com.dronedirector.veyrocutpro.windows

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.editor.PillChip
import com.dronedirector.veyrocutpro.ui.I18n
import com.dronedirector.veyrocutpro.ui.Pro

/**
 * Video editor → settings → Performance (PC only): which graphics card draws the effects and the
 * screen, which encoder makes the exported video, and how hard the app may work the processor.
 * Own code.
 */
object Performance {
    class Gpu(val luidHigh: Long, val luidLow: Long, val vendor: Int, val memoryMb: Long, val software: Boolean, val powerRank: Int, val name: String) {
        val id: String get() = "luid:$luidHigh:$luidLow"
        val maker: String get() = when (vendor) { 0x10DE -> "NVIDIA"; 0x8086 -> "Intel"; 0x1002, 0x1022 -> "AMD"; 0x5143 -> "Qualcomm"; else -> "" }
    }

    private fun prefs(c: Context) = c.getSharedPreferences("procam", Context.MODE_PRIVATE)

    /** "auto", "high", "low" or "luid:<high>:<low>" (one card by name). */
    fun gpu(c: Context): String = prefs(c).getString("perf_gpu", "auto") ?: "auto"
    /** "auto", "hw" (graphics card) or "sw" (processor only). */
    fun encoder(c: Context): String = prefs(c).getString("perf_encoder", "auto") ?: "auto"
    /** "max", "balanced" or "light". */
    fun cpu(c: Context): String = prefs(c).getString("perf_cpu", "max") ?: "max"

    fun setGpu(c: Context, v: String) { prefs(c).edit().putString("perf_gpu", v).apply() }
    fun setEncoder(c: Context, v: String) { prefs(c).edit().putString("perf_encoder", v).apply() }
    fun setCpu(c: Context, v: String) { prefs(c).edit().putString("perf_cpu", v).apply(); applyCpu(c) }

    /** The graphics cards (most powerful first), without Windows' software stand-in. */
    val gpus: List<Gpu> by lazy {
        if (!MfNative.ok) return@lazy emptyList()
        try {
            MfNative.gpuList().mapNotNull { r ->
                val p = r.split('|', limit = 7)
                if (p.size < 7) null else Gpu(p[0].toLong(), p[1].toLong(), p[2].toInt(), p[3].toLong(), p[4] == "1", p[5].toInt(), p[6].trim())
            }.filter { !it.software }.distinctBy { it.id }
        } catch (_: Throwable) { emptyList() }
    }

    /** The card the user's choice points to (null = Windows chooses). */
    fun chosen(c: Context): Gpu? {
        val g = gpus
        if (g.isEmpty()) return null
        return when (val v = gpu(c)) {
            "auto" -> null
            "high" -> g.first()
            "low" -> g.minByOrNull { if (it.powerRank < 0) 99 else it.powerRank }
            else -> g.firstOrNull { it.id == v }
        }
    }

    /** The card the effects ran on at start-up (the choice takes effect when the app starts). */
    @Volatile var effectsCard: Gpu? = null; private set

    /** Once, before the first window: the screen's drawing and the processor follow the choices. */
    fun startup(c: Context) {
        try {
            val card = chosen(c)
            effectsCard = card
            if (card != null) {
                // The app's own drawing (Skia on Direct3D) on the same kind of card.
                val discrete = gpus.size > 1 && card.powerRank != 0
                System.setProperty("skiko.directx.gpu.priority", if (discrete) "discrete" else "integrated")
            }
            applyCpu(c)
        } catch (t: Throwable) {
            com.dronedirector.veyrocutpro.ui.AppLog.w("Performance settings: ${t.message}")
        }
    }

    /** Cores the app may use for a processor choice. */
    fun cores(total: Int, choice: String): Int = when (choice) {
        "light" -> maxOf(1, minOf(total, maxOf(2, total / 4)))
        "balanced" -> maxOf(1, minOf(total - 1, (total * 3 + 3) / 4))
        else -> total
    }

    fun totalCores(): Int = try { if (MfNative.ok) MfNative.cpuCount().takeIf { it > 0 } ?: Runtime.getRuntime().availableProcessors() else Runtime.getRuntime().availableProcessors() } catch (_: Throwable) { Runtime.getRuntime().availableProcessors() }

    private fun applyCpu(c: Context) {
        if (!MfNative.ok) return
        val choice = cpu(c)
        val total = totalCores()
        try { MfNative.cpuLimit(if (choice == "max") 0 else cores(total, choice), choice != "max") } catch (_: Throwable) {}
    }

    /** For the exporter: (mode, luid high, luid low). Mode 0 = Windows chooses, 1 = graphics card, 2 = processor. */
    fun encoderArgs(c: Context): Triple<Int, Long, Long> {
        val mode = when (encoder(c)) { "hw" -> 1; "sw" -> 2; else -> 0 }
        val card = if (mode == 1) chosen(c) ?: gpus.firstOrNull() else null
        return Triple(mode, card?.luidHigh ?: 0L, card?.luidLow ?: 0L)
    }

    /** Which encoder the last export used (shown under the choice). */
    var lastEncoder by mutableStateOf("")

    /** From the exporter: "hardware|<name>" or "software|<name>". */
    fun noteEncoder(info: String) {
        if (info.isBlank()) return
        val hw = info.startsWith("hardware|")
        val name = info.substringAfter('|').ifBlank { if (hw) "H.264 / HEVC" else "Windows' encoder" }
        val s = name + " (" + I18n.tr(if (hw) "graphics card" else "processor") + ")"
        javax.swing.SwingUtilities.invokeLater { lastEncoder = s }
        com.dronedirector.veyrocutpro.ui.AppLog.i("Export encoder: $s")
    }
}

// ------------------------------------------------------------------ settings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerformanceSettings() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var gpu by remember { mutableStateOf(Performance.gpu(ctx)) }
    var enc by remember { mutableStateOf(Performance.encoder(ctx)) }
    var cpu by remember { mutableStateOf(Performance.cpu(ctx)) }
    val startGpu = remember { Performance.gpu(ctx) }
    val cards = Performance.gpus
    val total = remember { mutableIntStateOf(Performance.totalCores()) }.intValue
    @Composable fun label(t: String) = Text(I18n.tr(t), style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp), modifier = Modifier.padding(top = 14.dp))
    @Composable fun note(t: String) = Text(t, color = Pro.Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

    Column {
        label("GRAPHICS CARD")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            PillChip(I18n.tr("Automatic"), gpu == "auto") { gpu = "auto"; Performance.setGpu(ctx, gpu) }
            if (cards.size > 1) {
                PillChip(I18n.tr("High performance"), gpu == "high") { gpu = "high"; Performance.setGpu(ctx, gpu) }
                PillChip(I18n.tr("Power saving"), gpu == "low") { gpu = "low"; Performance.setGpu(ctx, gpu) }
            }
            for (g in cards) PillChip(g.name, gpu == g.id) { gpu = g.id; Performance.setGpu(ctx, gpu) }
        }
        note(
            when {
                cards.isEmpty() -> I18n.tr("Windows chooses the graphics card.")
                cards.size == 1 -> I18n.tr("This PC has one graphics card: {0}.").replace("{0}", cards[0].name)
                else -> I18n.tr("High performance uses the separate graphics card (faster, more power); Power saving the built-in one (cooler, longer battery). The effects and the app's screen use the card you pick.")
            }
        )
        Performance.effectsCard?.let { note(I18n.tr("Effects now run on: {0}").replace("{0}", it.name)) }
        if (gpu != startGpu) Text(I18n.tr("Takes effect the next time AlcorCut Pro starts."), color = Pro.Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

        label("ENCODER FOR EXPORT")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            PillChip(I18n.tr("Automatic"), enc == "auto") { enc = "auto"; Performance.setEncoder(ctx, enc) }
            PillChip(I18n.tr("Graphics card"), enc == "hw") { enc = "hw"; Performance.setEncoder(ctx, enc) }
            PillChip(I18n.tr("Processor only"), enc == "sw") { enc = "sw"; Performance.setEncoder(ctx, enc) }
        }
        note(
            when (enc) {
                "hw" -> I18n.tr("The graphics card's video encoder (NVIDIA, Intel or AMD) — the fastest exports. With several cards, the one picked above is used.")
                "sw" -> I18n.tr("Windows' encoder on the processor — slower, works on every PC, and leaves the graphics card free.")
                else -> I18n.tr("Windows picks the encoder: the graphics card's when there is one, else the processor.")
            }
        )
        if (Performance.lastEncoder.isNotEmpty()) note(I18n.tr("Last export: {0}").replace("{0}", Performance.lastEncoder))

        label("PROCESSOR LOAD")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            PillChip(I18n.tr("Maximum"), cpu == "max") { cpu = "max"; Performance.setCpu(ctx, cpu) }
            PillChip(I18n.tr("Balanced"), cpu == "balanced") { cpu = "balanced"; Performance.setCpu(ctx, cpu) }
            PillChip(I18n.tr("Light"), cpu == "light") { cpu = "light"; Performance.setCpu(ctx, cpu) }
        }
        val n = Performance.cores(total, cpu)
        note(
            when (cpu) {
                "balanced" -> I18n.tr("Uses {0} of {1} processor cores at lower priority, so the PC stays smooth while you export.")
                "light" -> I18n.tr("Uses {0} of {1} processor cores at lower priority — cooler and quieter on a laptop, but slower.")
                else -> I18n.tr("Uses all {1} processor cores — the fastest smooth motion, morph cuts, studio voice and AI tools.")
            }.replace("{0}", n.toString()).replace("{1}", total.toString())
        )
        Text(I18n.tr("Changes to the processor load apply at once."), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
