package android.app

/** Requests the phone asks the user to confirm (renaming, deleting). On Windows they go ahead. */
class IntentSender
class PendingIntent(val intentSender: IntentSender = IntentSender()) {
    companion object {
        const val FLAG_UPDATE_CURRENT = 0x08000000
        const val FLAG_IMMUTABLE = 0x04000000
        const val FLAG_MUTABLE = 0x02000000
        fun getBroadcast(c: android.content.Context, code: Int, i: android.content.Intent, flags: Int) = PendingIntent()
        fun getActivity(c: android.content.Context, code: Int, i: android.content.Intent, flags: Int) = PendingIntent()
    }
}

/** The app window, for code that asks the phone's activity for its window or to close. Own code. */
open class Activity : android.content.Context() {
    val window: android.view.Window = android.view.Window()
    fun finishAndRemoveTask() { kotlin.system.exitProcess(0) }
    fun finish() { kotlin.system.exitProcess(0) }
    companion object {
        const val RESULT_OK = -1
        const val RESULT_CANCELED = 0
    }
}
