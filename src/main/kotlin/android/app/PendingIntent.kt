package android.app

/** Requests the phone asks the user to confirm (renaming, deleting). On Windows they go ahead. */
class IntentSender
class PendingIntent(val intentSender: IntentSender = IntentSender())

object Activity {
    const val RESULT_OK = -1
    const val RESULT_CANCELED = 0
}
