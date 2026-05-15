package ca.hld.covertart.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

private class FakeSourceImage(override val width: Int, override val height: Int) : SourceImage

private class FakeMetadata(
    private val strings: Map<String, String> = emptyMap(),
    private val bitmaps: Map<String, SourceImage> = emptyMap(),
) : MetadataView {
    override fun string(key: String): String? = strings[key]
    override fun bitmap(key: String): SourceImage? = bitmaps[key]
}

private fun snapshot(
    state: PlaybackState,
    lastActive: Long,
    strings: Map<String, String> = emptyMap(),
    bitmaps: Map<String, SourceImage> = emptyMap(),
) = SessionSnapshot(FakeMetadata(strings, bitmaps), state, lastActive)

class TrackResolverTest {

    @Test
    fun returnsNullWhenNoSessions() {
        assertNull(TrackResolver.resolve(emptyList()))
    }

    @Test
    fun prefersPlayingSessionOverPaused() {
        val paused = snapshot(PlaybackState.PAUSED, lastActive = 100, strings = mapOf(MetadataKeys.TITLE to "Paused"))
        val playing = snapshot(PlaybackState.PLAYING, lastActive = 50, strings = mapOf(MetadataKeys.TITLE to "Playing"))
        assertEquals("Playing", TrackResolver.resolve(listOf(paused, playing))!!.title)
    }

    @Test
    fun tieBreaksByMostRecentlyActive() {
        val older = snapshot(PlaybackState.PLAYING, lastActive = 10, strings = mapOf(MetadataKeys.TITLE to "Older"))
        val newer = snapshot(PlaybackState.PLAYING, lastActive = 99, strings = mapOf(MetadataKeys.TITLE to "Newer"))
        assertEquals("Newer", TrackResolver.resolve(listOf(older, newer))!!.title)
    }

    @Test
    fun fallsBackToMostRecentWhenNothingPlaying() {
        val a = snapshot(PlaybackState.PAUSED, lastActive = 10, strings = mapOf(MetadataKeys.TITLE to "A"))
        val b = snapshot(PlaybackState.STOPPED, lastActive = 20, strings = mapOf(MetadataKeys.TITLE to "B"))
        assertEquals("B", TrackResolver.resolve(listOf(a, b))!!.title)
    }

    @Test
    fun artFallbackChainPrefersAlbumArt() {
        val album = FakeSourceImage(1, 1)
        val art = FakeSourceImage(2, 2)
        val icon = FakeSourceImage(3, 3)
        val s = snapshot(
            PlaybackState.PLAYING, lastActive = 1,
            bitmaps = mapOf(
                MetadataKeys.ALBUM_ART to album,
                MetadataKeys.ART to art,
                MetadataKeys.DISPLAY_ICON to icon,
            ),
        )
        assertSame(album, TrackResolver.resolve(listOf(s))!!.art)
    }

    @Test
    fun artFallbackChainUsesDisplayIconLast() {
        val icon = FakeSourceImage(3, 3)
        val s = snapshot(
            PlaybackState.PLAYING, lastActive = 1,
            bitmaps = mapOf(MetadataKeys.DISPLAY_ICON to icon),
        )
        assertSame(icon, TrackResolver.resolve(listOf(s))!!.art)
    }

    @Test
    fun nullArtWhenNoArtKeyPresent() {
        val s = snapshot(PlaybackState.PLAYING, lastActive = 1, strings = mapOf(MetadataKeys.TITLE to "T"))
        assertNull(TrackResolver.resolve(listOf(s))!!.art)
    }

    @Test
    fun identityKeyIgnoresPlaybackPosition() {
        val a = NowPlaying("Artist", "Title", "Album", null, PlaybackState.PLAYING)
        val b = NowPlaying("Artist", "Title", "Album", null, PlaybackState.PAUSED)
        assertEquals(a.identityKey, b.identityKey)
    }

    @Test
    fun artistFallsBackToAlbumArtist() {
        val s = snapshot(
            PlaybackState.PLAYING, lastActive = 1,
            strings = mapOf(MetadataKeys.ALBUM_ARTIST to "The Band"),
        )
        assertEquals("The Band", TrackResolver.resolve(listOf(s))!!.artist)
    }
}
