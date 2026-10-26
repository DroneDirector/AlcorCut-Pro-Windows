package com.dronedirector.veyrocutpro.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.camera.Storage
import com.dronedirector.veyrocutpro.camera.StorageArea

/** A PC always works like the phone on an external monitor (mouse pointer, wide layouts). */
@Composable
fun onExternalMonitor(): Boolean = true

/** The camera inside the editors: comes with Capture (the phone as the PC's camera). */
object CaptureForEditor {
    fun open(editor: String, video: Boolean, onMedia: (uri: Uri, isVideo: Boolean) -> Unit) {
        Toast.makeText(null, I18n.tr("The camera comes to Windows with Capture (your phone as the PC's camera)"), Toast.LENGTH_LONG).show()
    }
    fun close() {}
}

/** Where this part of the app saves: the default folder or one you choose (any drive). */
@Composable
fun StorageChooser(area: StorageArea, onChange: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    var chosen by remember { mutableStateOf(Storage.chosen(ctx, area)) }
    val current = Storage.folder(ctx, area, if (area == StorageArea.PHOTO) Storage.Kind.IMAGE else Storage.Kind.VIDEO).path
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(current, color = Color.White, fontSize = 13.sp)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose folder…", color = Color.Black, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Pro.Amber).clickable {
                    androidx.activity.result.contract.FileDialogs.folder { u ->
                        val f = u?.toFile() ?: return@folder
                        Storage.choose(ctx, area, f); chosen = f.path; onChange(f.path)
                    }
                }.padding(horizontal = 12.dp, vertical = 7.dp))
            if (chosen != null) Text("Use default", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).clickable {
                    Storage.choose(ctx, area, null); chosen = null; onChange("")
                }.padding(horizontal = 12.dp, vertical = 7.dp))
        }
    }
}
