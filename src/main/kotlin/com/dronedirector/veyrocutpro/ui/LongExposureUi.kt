package com.dronedirector.veyrocutpro.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.CameraUiState
import com.dronedirector.veyrocutpro.camera.CaptureExtras
import com.dronedirector.veyrocutpro.camera.LongKind
import com.dronedirector.veyrocutpro.camera.ProCameraController

/** While a long exposure runs: the picture building up over the viewfinder, and a progress bar. */
@Composable
fun LongExposureOverlay(s: CameraUiState, c: ProCameraController) {
    if (s.longProgress < 0f) return
    val preview by c.longPreview.collectAsState()
    Box(Modifier.fillMaxSize()) {
        preview?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
        val kind = LongKind.entries[CaptureExtras.longKind.coerceIn(0, LongKind.entries.size - 1)]
        val left = ((1f - s.longProgress) * CaptureExtras.longSec).coerceAtLeast(0f)
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (s.longProgress >= 1f) "Saving…" else "${kind.label.uppercase()} · %.1f s".format(left),
                color = Pro.Amber, fontSize = 13.sp,
            )
            Box(Modifier.padding(top = 6.dp).width(180.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(s.longProgress.coerceIn(0f, 1f)).height(4.dp).background(Pro.Amber))
            }
            Text("Keep still · tap the shutter to finish early", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}
