package androidx.media3.common

/** The phone's player interface (Media3). */
interface Player {
    interface Listener {
        fun onPlaybackStateChanged(playbackState: Int) {}
        fun onIsPlayingChanged(isPlaying: Boolean) {}
        fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {}
        fun onPlayerError(error: PlaybackException) {}
        fun onVideoSizeChanged(videoSize: VideoSize) {}
        fun onRenderedFirstFrame() {}
        fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {}
        fun onPositionDiscontinuity(oldPosition: PositionInfo, newPosition: PositionInfo, reason: Int) {}
        fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {}
        fun onVolumeChanged(volume: Float) {}
    }
    class PositionInfo(val positionMs: Long)
    companion object {
        const val STATE_IDLE = 1; const val STATE_BUFFERING = 2; const val STATE_READY = 3; const val STATE_ENDED = 4
        const val REPEAT_MODE_OFF = 0; const val REPEAT_MODE_ONE = 1; const val REPEAT_MODE_ALL = 2
        const val DISCONTINUITY_REASON_SEEK = 1; const val DISCONTINUITY_REASON_AUTO_TRANSITION = 0
        const val MEDIA_ITEM_TRANSITION_REASON_AUTO = 1; const val MEDIA_ITEM_TRANSITION_REASON_SEEK = 2
        const val PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST = 1
    }
    fun setMediaItem(item: MediaItem)
    fun setMediaItem(item: MediaItem, startPositionMs: Long)
    fun setMediaItems(items: List<MediaItem>)
    fun addMediaItem(item: MediaItem)
    fun clearMediaItems()
    fun prepare()
    fun play()
    fun pause()
    fun stop()
    fun release()
    fun seekTo(positionMs: Long)
    fun seekTo(mediaItemIndex: Int, positionMs: Long)
    fun addListener(l: Listener)
    fun removeListener(l: Listener)
    var playWhenReady: Boolean
    val playbackState: Int
    val isPlaying: Boolean
    val currentPosition: Long
    val duration: Long
    val contentPosition: Long get() = currentPosition
    val bufferedPosition: Long get() = duration
    var volume: Float
    var repeatMode: Int
    var playbackParameters: PlaybackParameters
    fun setPlaybackSpeed(speed: Float) { playbackParameters = PlaybackParameters(speed) }
    val currentMediaItem: MediaItem?
    val currentMediaItemIndex: Int
    val mediaItemCount: Int
    val videoSize: VideoSize
    val playerError: PlaybackException?
    fun isCommandAvailable(c: Int) = true
}
