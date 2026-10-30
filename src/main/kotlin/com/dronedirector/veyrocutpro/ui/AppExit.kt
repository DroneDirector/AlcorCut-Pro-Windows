package com.dronedirector.veyrocutpro.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/** File → Exit (any part of the app): the screen fades and shrinks away like the home screen's EXIT, then the app closes. Own code. */
object AppExit {
    var exiting by mutableStateOf(false)
}

@Composable
fun AppExitLayer(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val leave by animateFloatAsState(
        if (AppExit.exiting) 1f else 0f, tween(520),
        finishedListener = { if (it >= 1f) { AppExit.exiting = false; (context as? android.app.Activity)?.finishAndRemoveTask() } },
        label = "exit",
    )
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - leave; scaleX = 1f - leave * 0.08f; scaleY = 1f - leave * 0.08f }) { content() }
}
