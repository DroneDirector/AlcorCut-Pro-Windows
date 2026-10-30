package com.dronedirector.veyrocutpro.editor

import android.net.Uri

/** Round trip: while the video editor has a photo open in the photo editor, its export comes back here. */
object PhotoRoundTrip {
    @Volatile var onExported: ((Uri) -> Unit)? = null
}
