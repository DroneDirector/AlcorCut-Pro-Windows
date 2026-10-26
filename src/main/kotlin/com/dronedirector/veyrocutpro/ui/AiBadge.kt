package com.dronedirector.veyrocutpro.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Animated AI progress badge (progress ring, shimmer, big percentage) with an optional stop button. */
@Composable
fun AiBadgeHost(visible: Boolean, title: String, count: Int, progress: Int, onStop: (() -> Unit)?, modifier: Modifier = Modifier, note: String? = null) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = androidx.compose.animation.slideInVertically { -it * 2 } + androidx.compose.animation.fadeIn() +
            androidx.compose.animation.scaleIn(initialScale = 0.7f),
        exit = androidx.compose.animation.slideOutVertically { -it * 2 } + androidx.compose.animation.fadeOut(),
    ) { AiBadge(title, count, progress, onStop, note = note) }
}

@Composable
fun AiBadge(title: String, count: Int, progress: Int, onStop: (() -> Unit)? = null, modifier: Modifier = Modifier, note: String? = null) {
    val accent = Pro.Amber
    val shown by androidx.compose.animation.core.animateIntAsState(progress, androidx.compose.animation.core.tween(500), label = "pct")
    val sweep by androidx.compose.animation.core.animateFloatAsState(progress / 100f, androidx.compose.animation.core.tween(500), label = "sweep")
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "ai")
    val spin by inf.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    val shimmer by inf.animateFloat(-1f, 2f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.LinearEasing)), label = "shimmer")
    val pulse by inf.animateFloat(0.75f, 1.15f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse")
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
    androidx.compose.foundation.layout.Row(
        modifier
            .padding(top = 62.dp)
            .clip(shape)
            .background(androidx.compose.ui.graphics.Color(0xE6101114))
            .border(1.5.dp, androidx.compose.ui.graphics.Brush.sweepGradient(listOf(accent, accent.copy(alpha = 0.15f), accent)), shape)
            .drawWithContent {
                drawContent()
                // A light sweep gliding across the badge.
                val x = size.width * shimmer
                drawRect(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f), androidx.compose.ui.graphics.Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(x - size.height * 2, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                    )
                )
            }
            .padding(start = 6.dp, end = if (onStop != null) 6.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Progress ring with a spinning glow and a pulsing sparkle in the middle.
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                val inset = stroke / 2 + 1f
                val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f), 0f, 360f, false, tl, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawArc(
                    androidx.compose.ui.graphics.Brush.sweepGradient(listOf(accent.copy(alpha = 0.5f), accent, androidx.compose.ui.graphics.Color.White)),
                    -90f, 360f * sweep.coerceAtLeast(0.02f), false, tl, arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
                rotate(spin) {
                    drawArc(accent.copy(alpha = 0.35f), 0f, 40f, false, tl, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * 0.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
            }
            Icon(
                Icons.Filled.AutoAwesome, contentDescription = null, tint = accent,
                modifier = Modifier.size(18.dp).graphicsLayer { scaleX = pulse; scaleY = pulse },
            )
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                title + if (count > 1) " · $count" else "",
                style = Pro.Label.copy(color = accent, fontSize = 10.sp, letterSpacing = 1.5.sp),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$shown",
                    color = androidx.compose.ui.graphics.Color.White, fontSize = 22.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
                Text(
                    "%", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                )
            }
            // Why this run happens (e.g. a second, full-size pass).
            if (note != null) Text(
                note, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f), fontSize = 10.5.sp,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 230.dp),
            )
        }
        if (onStop != null) {
            Box(
                Modifier.padding(start = 12.dp).size(34.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f))
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
