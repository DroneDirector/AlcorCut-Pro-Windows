package androidx.activity.result

import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

class PickVisualMediaRequest(mediaType: ActivityResultContracts.PickVisualMedia.VisualMediaType = ActivityResultContracts.PickVisualMedia.ImageAndVideo) {
    val type = mediaType
}

class ActivityResultLauncher<I, O> internal constructor(private val contract: ActivityResultContract<I, O>, private val result: () -> (O) -> Unit) {
    fun launch(input: I) = contract.run(input) { o -> javax.swing.SwingUtilities.invokeLater { result()(o) } }
    fun unregister() {}
}

internal fun <I, O> launcherOf(contract: ActivityResultContract<I, O>, result: () -> (O) -> Unit) = ActivityResultLauncher(contract, result)
