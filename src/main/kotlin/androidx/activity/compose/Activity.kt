package androidx.activity.compose

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
fun <I, O> rememberLauncherForActivityResult(contract: ActivityResultContract<I, O>, onResult: (O) -> Unit): ActivityResultLauncher<I, O> {
    val cb = rememberUpdatedState(onResult)
    return remember(contract::class) { androidx.activity.result.launcherOf(contract) { cb.value } }
}

/** "Back" on Windows is the Esc key (and the mouse's back button): the newest handler wins. */
object BackDispatcher {
    internal val stack = mutableStateListOf<() -> Boolean>()

    /** Runs the newest enabled back handler; false if none. */
    fun back(): Boolean {
        for (h in stack.reversed()) if (h()) return true
        return false
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val en = rememberUpdatedState(enabled)
    val cb = rememberUpdatedState(onBack)
    DisposableEffect(Unit) {
        val h: () -> Boolean = { if (en.value) { cb.value(); true } else false }
        BackDispatcher.stack += h
        onDispose { BackDispatcher.stack -= h }
    }
}
