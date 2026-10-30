package android.app

/** The phone asks before deleting another app's file; on a PC files are deleted directly. */
class RecoverableSecurityException(message: String?) : SecurityException(message) {
    class Action(val actionIntent: android.app.PendingIntent)
    val userAction: Action get() = Action(android.app.PendingIntent())
}
