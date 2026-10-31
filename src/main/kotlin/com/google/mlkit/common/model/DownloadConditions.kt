package com.google.mlkit.common.model

class DownloadConditions private constructor() {
    class Builder {
        fun requireWifi() = this
        fun requireCharging() = this
        fun build() = DownloadConditions()
    }
}
