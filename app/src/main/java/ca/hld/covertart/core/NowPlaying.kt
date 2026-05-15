package ca.hld.covertart.core

enum class PlaybackState { PLAYING, PAUSED, STOPPED, OTHER }

/** The resolved current track. */
data class NowPlaying(
    val artist: String,
    val title: String,
    val album: String,
    val art: SourceImage?,
    val playbackState: PlaybackState,
) {
    /** Stable identity for dedup â independent of playback position/seeks. */
    val identityKey: String = "$artist\u0000$album\u0000$title"
}
