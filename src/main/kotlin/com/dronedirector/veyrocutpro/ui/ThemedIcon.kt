package com.dronedirector.veyrocutpro.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons that follow the light / dark look like [Text] does (white tints become near-black on the light look). Own code. */
@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) =
    androidx.compose.material3.Icon(imageVector, contentDescription, modifier, themed(tint))

@Composable
fun Icon(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) =
    androidx.compose.material3.Icon(painter, contentDescription, modifier, themed(tint))
