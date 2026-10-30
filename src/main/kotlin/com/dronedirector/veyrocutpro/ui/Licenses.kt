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
    Notice("Compose Multiplatform (Jetpack Compose for desktop), Material 3, Material Icons", "JetBrains s.r.o., The Android Open Source Project / Google LLC", "Apache License 2.0", "https://github.com/JetBrains/compose-multiplatform"),
    Notice("Skia and Skiko (drawing)", "Google LLC, JetBrains s.r.o.", "BSD 3-Clause License / Apache License 2.0", "https://github.com/JetBrains/skiko"),
    Notice("Kotlin standard library, kotlinx.coroutines", "JetBrains s.r.o. and contributors", "Apache License 2.0", "https://kotlinlang.org"),
    Notice("Gson", "Google LLC", "Apache License 2.0", "https://github.com/google/gson"),
    Notice("ONNX Runtime (runs the AI models)", "Microsoft Corporation", "MIT License", "https://github.com/microsoft/onnxruntime"),
    Notice("OpenJDK runtime (bundled with the app)", "Oracle and/or its affiliates, Eclipse Adoptium", "GNU GPL 2 with the Classpath Exception", "https://openjdk.org/legal/gplv2+ce.html"),
    Notice("MobileSAM (Magic Mask object selection)", "Chaoning Zhang et al.", "Apache License 2.0", "https://github.com/ChaoningZhang/MobileSAM"),
    Notice("Segment Anything (mask decoder in MobileSAM)", "Meta Platforms, Inc.", "Apache License 2.0", "https://github.com/facebookresearch/segment-anything"),
    Notice("LaMa — Resolution-robust Large Mask Inpainting (object removal, downloaded on first use); ONNX port by Carve", "Samsung AI Center Moscow (advimman)", "Apache License 2.0", "https://github.com/advimman/lama"),
    Notice("NAFNet (AI noise reduction), SIDD width-32 weights, exported to ONNX", "megvii-research", "MIT License", "https://github.com/megvii-research/NAFNet"),
    Notice("DeepLab v3 (subject and people selection)", "Google LLC / TensorFlow authors", "Apache License 2.0", "https://github.com/tensorflow/models/tree/master/research/deeplab"),
    Notice("ESRGAN (AI upscaling)", "Xintao Wang et al. / TensorFlow Hub authors", "Apache License 2.0", "https://github.com/xinntao/ESRGAN"),
    Notice("YuNet face detection (OpenCV Zoo)", "Shiqi Yu, Wei Wu and OpenCV contributors", "MIT License", "https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet"),
    Notice("SFace face recognition (OpenCV Zoo, People in the library)", "Yaoyao Zhong, Weihong Deng and OpenCV contributors", "Apache License 2.0", "https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface"),
    Notice("tf2onnx (model conversion for Windows)", "ONNX Project contributors", "Apache License 2.0", "https://github.com/onnx/tensorflow-onnx"),
    Notice("Natural Earth 1:110m land outlines (Library map view)", "Natural Earth (naturalearthdata.com)", "Public domain", "https://www.naturalearthdata.com/about/terms-of-use/"),
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
        containerColor = panelHere(),
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
                    for ((label, file) in listOf("Apache License 2.0" to "apache-2.0.txt", "MIT License (NAFNet)" to "nafnet-mit.txt", "BSD 3-Clause (RNNoise)" to "rnnoise-bsd.txt", "MIT License (whisper.cpp)" to "whispercpp-mit.txt", "GNU LGPL 2.1 (LibRaw)" to "libraw-lgpl-2.1.txt", "LibRaw copyright notice" to "libraw-copyright.txt", "Font licences" to "../fonts/FONT_LICENSES.txt")) {
                        Text(
                            "READ THE $label".uppercase(), style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                            modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(fgHere().copy(alpha = 0.06f))
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
            .background(fgHere().copy(alpha = 0.04f)).clickable { open = true }.padding(12.dp),
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
            .background(fgHere().copy(alpha = 0.04f)).clickable {
                try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_URL))) } catch (_: Throwable) {}
            }.padding(12.dp),
    )
    Text("Your media stays on your phone unless you use Cloud AI (sent to fal.ai with your own key) or live streaming.", color = Pro.Dim, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
}
