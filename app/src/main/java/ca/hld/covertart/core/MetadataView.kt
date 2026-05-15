package ca.hld.covertart.core

/**
 * Read-only view over one media session's metadata, abstracting
 * android.media.MediaMetadata so TrackResolver stays JVM-testable.
 */
interface MetadataView {
    fun string(key: String): String?
    fun bitmap(key: String): SourceImage?
}

/** A point-in-time snapshot of one media session. */
data class SessionSnapshot(
    val metadata: MetadataView,
    val playbackState: PlaybackState,
    /** When this session most recently reported activity (ms); used for tie-breaking. */
    val lastActiveAtMillis: Long,
)

/** MediaMetadata key strings (mirrors android.media.MediaMetadata.METADATA_KEY_*). */
object MetadataKeys {
    const val ARTIST = "android.media.metadata.ARTIST"
    const val ALBUM_ARTIST = "android.media.metadata.ALBUM_ARTIST"
    const val TITLE = "android.media.metadata.TITLE"
    const val ALBUM = "android.media.metadata.ALBUM"
    const val ALBUM_ART = "android.media.metadata.ALBUM_ART"
    const val ART = "android.media.metadata.ART"
    const val DISPLAY_ICON = "android.media.metadata.DISPLAY_ICON"
}
