package androidx.activity.result

class IntentSenderRequest private constructor(val intentSender: android.app.IntentSender) {
    class Builder(private val s: android.app.IntentSender) { fun build() = IntentSenderRequest(s) }
}

class ActivityResult(val resultCode: Int, val data: android.content.Intent?)
