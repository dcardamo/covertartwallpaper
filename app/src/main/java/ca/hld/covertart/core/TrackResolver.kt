package ca.hld.covertart.core

/** Pure logic: choose the session to follow and build a NowPlaying from it. */
object TrackResolver {

    /** @return the resolved track, or null when there are no sessions. */
    fun resolve(sessions: List<SessionSnapshot>): NowPlaying? {
        val chosen = pickSession(sessions) ?: return null
        val md = chosen.metadata
        val artist = md.string(MetadataKeys.ARTIST)
            ?: md.string(MetadataKeys.ALBUM_ARTIST)
            ?: ""
        val title = md.string(MetadataKeys.TITLE) ?: ""
        val album = md.string(MetadataKeys.ALBUM) ?: ""
        val art = md.bitmap(MetadataKeys.ALBUM_ART)
            ?: md.bitmap(MetadataKeys.ART)
            ?: md.bitmap(MetadataKeys.DISPLAY_ICON)
        return NowPlaying(artist, title, album, art, chosen.playbackState)
    }

    /** Prefer a PLAYING session; tie-break (and fall back) to most recently active. */
    internal fun pickSession(sessions: List<SessionSnapshot>): SessionSnapshot? {
        if (sessions.isEmpty()) return null
        val playing = sessions.filter { it.playbackState == PlaybackState.PLAYING }
        val pool = playing.ifEmpty { sessions }
        return pool.maxByOrNull { it.lastActiveAtMillis }
    }
}
