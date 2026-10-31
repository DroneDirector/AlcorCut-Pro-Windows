package com.dronedirector.veyrocutpro.editor

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dronedirector.veyrocutpro.ui.Pro

/** NAS sign-ins are kept encrypted for your Windows account; no extra unlock is asked. */
object PhoneLock {
    var nasLock by mutableStateOf(false)
        private set
    fun load(c: Context) {}
    fun setNasLock(c: Context, on: Boolean) {}
    fun isSet(c: Context): Boolean = false
    const val NOTE = "On Windows, saved sign-ins are encrypted for your Windows account and are never shown in the app."
}

@Composable
fun rememberPhoneUnlock(): (title: String, onDone: (Boolean) -> Unit) -> Unit = { _, done -> done(true) }

/** Content credentials (C2PA): not on Windows yet. */
object ContentCredentials {
    fun on(c: Context, video: Boolean) = false
    fun setOn(c: Context, video: Boolean, v: Boolean) {}
    fun creator(c: Context) = ""
    fun setCreator(c: Context, v: String) {}
    fun sign(c: Context, out: Uri, video: Boolean): String? = null
    fun selfTest(c: Context): String = "not on Windows yet"
}

@Composable
fun ContentCredentialsSection(video: Boolean) {
    com.dronedirector.veyrocutpro.editor.SectionTitle("CONTENT CREDENTIALS")
    Text("Signed content credentials (C2PA) come to the Windows version later; photos exported here don't carry them yet.", color = Pro.Dim, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
}

/** A bundled font as a desktop font family (for the font pickers). */
fun android.graphics.Typeface.composeFamily(): androidx.compose.ui.text.font.FontFamily? =
    file?.let { androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.platform.Font(it)) }

/** A phone Typeface as a desktop font family (the system sans-serif when it isn't a bundled font). */
fun composeFamilyOf(t: android.graphics.Typeface): androidx.compose.ui.text.font.FontFamily = t.composeFamily() ?: androidx.compose.ui.text.font.FontFamily.SansSerif
