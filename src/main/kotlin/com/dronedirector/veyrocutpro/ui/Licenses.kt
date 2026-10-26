package com.dronedirector.veyrocutpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One open-source part of the app and its licence. */
private class Notice(val name: String, val by: String, val license: String, val url: String)

private val NOTICES = listOf(
    Notice("AndroidX: CameraX, Media3 (ExoPlayer, Transformer, Effect), Jetpack Compose, Material 3, Material Icons, Activity, Lifecycle, Core, Security Crypto, ExifInterface, HeifWriter, MediaRouter", "The Android Open Source Project / Google LLC", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    Notice("LiteRT (TensorFlow Lite runtime)", "Google LLC", "Apache License 2.0", "https://ai.google.dev/edge/litert"),
    Notice("Kotlin standard library, kotlinx.coroutines", "JetBrains s.r.o. and contributors", "Apache License 2.0", "https://kotlinlang.org"),
    Notice("Gson", "Google LLC", "Apache License 2.0", "https://github.com/google/gson"),
    Notice("RootEncoder (live streaming)", "pedroSG94", "Apache License 2.0", "https://github.com/pedroSG94/RootEncoder"),
    Notice("ESRGAN model (AI upscale)", "TensorFlow authors (esrgan-tf2)", "Apache License 2.0", "https://www.tensorflow.org/lite/examples/super_resolution/overview"),
    Notice("DeepLab v3 model (AI subject selection)", "The TensorFlow Authors", "Apache License 2.0", "https://github.com/tensorflow/models/tree/master/research/deeplab"),
    Notice("Google ML Kit — face detection, selfie and subject segmentation, image labelling (library search)", "Google LLC", "ML Kit Terms of Service", "https://developers.google.com/ml-kit/terms"),
    Notice("NAFNet model, SIDD width-32 weights (AI noise reduction), LiteRT conversion by litert-community", "megvii-research", "MIT License", "https://github.com/megvii-research/NAFNet"),
    Notice("SIDD dataset (used to train the noise-reduction model)", "Abdelhamed, Lin, Brown", "MIT License", "https://www.eecs.yorku.ca/~kamel/sidd/"),
    Notice("RNNoise (AI voice isolation)", "Xiph.Org Foundation, Mozilla, Jean-Marc Valin", "BSD 3-Clause License", "https://github.com/xiph/rnnoise"),
    Notice("whisper.cpp and ggml (on-device speech to text)", "Georgi Gerganov and the ggml authors", "MIT License", "https://github.com/ggml-org/whisper.cpp"),
    Notice("Whisper speech model weights (downloaded on first use)", "OpenAI", "MIT License", "https://github.com/openai/whisper"),
    Notice("MobileSAM (Magic Mask object selection)", "Chaoning Zhang et al.", "Apache License 2.0", "https://github.com/ChaoningZhang/MobileSAM"),
    Notice("Segment Anything (mask decoder in MobileSAM)", "Meta Platforms, Inc.", "Apache License 2.0", "https://github.com/facebookresearch/segment-anything"),
    Notice("ONNX Runtime (runs the Magic Mask model)", "Microsoft Corporation", "MIT License", "https://github.com/microsoft/onnxruntime"),
    Notice("Lottie for Android (animation layers)", "Airbnb, Inc.", "Apache License 2.0", "https://github.com/airbnb/lottie-android"),
    Notice("DeepFilterNet3 (studio voice clean-up)", "Hendrik Schröter", "MIT License / Apache License 2.0", "https://github.com/Rikorose/DeepFilterNet"),
    Notice("tract (runs DeepFilterNet)", "Sonos, Inc.", "MIT License / Apache License 2.0", "https://github.com/sonos/tract"),
    Notice("Depth Anything V2 Small (AI depth, downloaded on first use)", "Lihe Yang et al. (HKU, TikTok)", "Apache License 2.0", "https://github.com/DepthAnything/Depth-Anything-V2"),
    Notice("LaMa — Resolution-robust Large Mask Inpainting (object removal, downloaded on first use); ONNX port by Carve", "Samsung AI Center Moscow (advimman)", "Apache License 2.0", "https://github.com/advimman/lama"),
    Notice("Kokoro 82M text-to-speech (AI voiceover, downloaded on first use)", "hexgrad", "Apache License 2.0", "https://huggingface.co/hexgrad/Kokoro-82M"),
    Notice("misaki pronunciation dictionaries (AI voiceover)", "hexgrad", "Apache License 2.0", "https://github.com/hexgrad/misaki"),
    Notice("jxl-coder — JPEG XL export", "Radzivon Bartoshyk", "Apache License 2.0 / BSD-3-Clause", "https://github.com/awxkee/jxl-coder"),
    Notice("libjxl — JPEG XL reference library (inside jxl-coder)", "The JPEG XL Project Authors", "BSD-3-Clause", "https://github.com/libjxl/libjxl"),
    Notice("Brotli compression (inside jxl-coder)", "Google LLC", "MIT License", "https://github.com/google/brotli"),
    Notice("LibRaw 0.22.2 — reading Sony, Canon, Nikon, Fujifilm and other cameras' RAW files (unmodified, its own replaceable libraw.so; source code: github.com/LibRaw/LibRaw, tag 0.22.2)", "LibRaw LLC; includes code from dcraw (Dave Coffin), DCB / FBDD (Jacek Gozdz, BSD), X3F tools (Roland Karlsson, BSD) and Adobe DNG SDK pieces (MIT)", "GNU LGPL 2.1 (full text bundled with the app)", "https://github.com/LibRaw/LibRaw/tree/0.22.2"),
    Notice("Natural Earth 1:110m land outlines (Library map view)", "Natural Earth (naturalearthdata.com)", "Public domain", "https://www.naturalearthdata.com/about/terms-of-use/"),
    Notice("Google ML Kit on-device translation and language identification (caption translation)", "Google LLC", "ML Kit Terms of Service", "https://developers.google.com/ml-kit/terms"),
    Notice("Google Play services Wearable — the watch remote's connection to the phone", "Google LLC", "Android Software Development Kit License", "https://developer.android.com/studio/terms"),
    Notice("Google Cast SDK (Google Play services) — casting the live picture to a Chromecast / Google TV", "Google LLC", "Google APIs Terms of Service / Android Software Development Kit License", "https://developers.google.com/cast/docs/terms"),
    Notice("Jetpack Compose for Wear OS — the watch remote's screen", "The Android Open Source Project", "Apache License 2.0", "https://developer.android.com/jetpack/androidx/releases/wear-compose"),
)

/** Reads a bundled licence text (fetched at build time), or null if it isn't in this build. */
private fun asset(context: android.content.Context, name: String): String? = try {
    context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
} catch (_: Throwable) { null }

@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var show by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pro.PanelSolid,
        title = { Text(show?.substringBefore('\u0000') ?: "Open-source licences", color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                val s = show
                if (s == null) {
                    Text("AlcorCut Pro is built with these open-source parts. Thank you to their authors.", color = Pro.Dim, fontSize = 12.sp)
                    NOTICES.forEach { n ->
                        Column(Modifier.padding(top = 10.dp)) {
                            Text(n.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("${n.by} · ${n.license}", color = Pro.Dim, fontSize = 11.sp)
                            Text(n.url, color = Pro.Amber, fontSize = 11.sp)
                        }
                    }
                    for ((label, file) in listOf("Apache License 2.0" to "apache-2.0.txt", "MIT License (NAFNet)" to "nafnet-mit.txt", "BSD 3-Clause (RNNoise)" to "rnnoise-bsd.txt", "MIT License (whisper.cpp)" to "whispercpp-mit.txt", "GNU LGPL 2.1 (LibRaw)" to "libraw-lgpl-2.1.txt", "LibRaw copyright notice" to "libraw-copyright.txt")) {
                        Text(
                            "READ THE $label".uppercase(), style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                            modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f))
                                .clickable { show = label + "\u0000" + file }.padding(10.dp),
                        )
                    }
                    Text("Brand names mentioned in the app (e.g. DJI D-Log, Sony S-Log3, Panasonic V-Log, ARRI LogC3, Topaz Labs, fal.ai) belong to their owners and are used only to describe compatibility. AlcorCut Pro is not affiliated with them.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
                } else {
                    val file = s.substringAfter('\u0000')
                    val text = asset(context, file)
                        ?: if (file.startsWith("libraw")) "GNU Lesser General Public License 2.1 — https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html (LibRaw source: https://github.com/LibRaw/LibRaw/tree/0.22.2)"
                        else if (file.startsWith("apache")) "Apache License, Version 2.0 — https://www.apache.org/licenses/LICENSE-2.0"
                        else "MIT License — https://github.com/megvii-research/NAFNet/blob/main/LICENSE"
                    Text(text, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (show != null) show = null else onDismiss() }) { Text(if (show != null) "Back" else "Close", color = Pro.Amber) }
        },
    )
}

/** Settings button that opens the licences. */
@Composable
fun LicensesButton() {
    var open by remember { mutableStateOf(false) }
    if (open) LicensesDialog { open = false }
    Text(
        "©  OPEN-SOURCE LICENCES", style = Pro.Label.copy(color = Pro.Dim, fontSize = 11.sp),
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.04f)).clickable { open = true }.padding(12.dp),
    )
}

const val PRIVACY_URL = "https://github.com/DroneDirector/Android-Projects/blob/main/ProCamera/PRIVACY.md"

/** Settings button that opens the privacy policy in the browser. */
@Composable
fun PrivacyButton() {
    val context = LocalContext.current
    Text(
        "🔒  PRIVACY POLICY", style = Pro.Label.copy(color = Pro.Dim, fontSize = 11.sp),
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.04f)).clickable {
                try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_URL))) } catch (_: Throwable) {}
            }.padding(12.dp),
    )
    Text("Your media stays on your phone unless you use Cloud AI (sent to fal.ai with your own key) or live streaming.", color = Pro.Dim, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
}
