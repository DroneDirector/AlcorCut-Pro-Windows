package com.dronedirector.veyrocutpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * A drop-down menu that always stays on the screen: it opens just under its button and never moves up
 * or off the screen, is never taller than the space below the button, and scrolls (with hint arrows)
 * when its content doesn't fit. Opening and closing groups inside it never pushes anything out of reach.
 */
@Composable
fun ScrollMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current
    // Space under the button (px), measured where the menu is placed.
    var room by remember { mutableIntStateOf(with(density) { (cfg.screenHeightDp * 0.8f).dp.roundToPx() }) }
    val margin = with(density) { 8.dp.roundToPx() }
    val provider = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val top = anchorBounds.bottom.coerceAtMost(windowSize.height - margin * 6)
                val r = (windowSize.height - top - margin).coerceAtLeast(margin * 10)
                if (r != room) room = r
                val x = anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
                return IntOffset(x, top)
            }
        }
    }
    val maxW = (cfg.screenWidthDp - 16).dp
    val w = width?.let { if (it > maxW) maxW else it }
    val scroll = rememberScrollState()
    Popup(popupPositionProvider = provider, onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        Column(
            modifier
                .then(if (w != null) Modifier.width(w) else Modifier.widthIn(min = 200.dp, max = maxW))
                .heightIn(max = with(density) { room.toDp() })
                .shadow(8.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(panelHere())
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                .hintedScroll(horizontal = false, scroll = scroll)
                .padding(vertical = 6.dp),
            content = content,
        )
    }
}
