package com.dronedirector.veyrocutpro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import android.content.Context
import com.dronedirector.veyrocutpro.ui.AccentStore
import com.dronedirector.veyrocutpro.ui.I18n
import com.dronedirector.veyrocutpro.ui.ProCamTheme
import com.dronedirector.veyrocutpro.ui.Text

fun main() {
    AccentStore.load(Context.app)
    I18n.load(Context.app)
    application {
        val state = rememberWindowState(placement = WindowPlacement.Maximized, position = WindowPosition(Alignment.Center), size = DpSize(1440.dp, 900.dp))
        Window(onCloseRequest = ::exitApplication, title = "AlcorCut Pro", icon = painterResource("icon.png"), state = state) {
            ProCamTheme {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Text("AlcorCut Pro", color = Color.White)
                }
            }
        }
    }
}
