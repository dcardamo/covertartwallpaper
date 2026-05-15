package ca.hld.covertart.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeGateTest {

    private fun track(id: String) =
        NowPlaying(artist = id, title = id, album = id, art = null, playbackState = PlaybackState.PLAYING)

    @Test
    fun gatedWhenMasterDisabled() {
        val gate = ChangeGate(debounceMillis = 400)
        assertFalse(gate.shouldApply(track("a"), masterEnabled = false, nowMillis = 10_000))
    }

    @Test
    fun allowsNewIdentityWhenDebounceElapsed() {
        val gate = ChangeGate(debounceMillis = 400)
        assertTrue(gate.shouldApply(track("a"), masterEnabled = true, nowMillis = 10_000))
    }

    @Test
    fun gatedWhenIdentityUnchanged() {
        val gate = ChangeGate(debounceMillis = 400)
        gate.markApplied(track("a"), nowMillis = 10_000)
        assertFalse(gate.shouldApply(track("a"), masterEnabled = true, nowMillis = 11_000))
    }

    @Test
    fun gatedWhenWithinDebounceWindow() {
        val gate = ChangeGate(debounceMillis = 400)
        gate.markApplied(track("a"), nowMillis = 10_000)
        // New identity, but only 200ms since the last apply.
        assertFalse(gate.shouldApply(track("b"), masterEnabled = true, nowMillis = 10_200))
    }

    @Test
    fun allowsNewIdentityAfterDebounceWindow() {
        val gate = ChangeGate(debounceMillis = 400)
        gate.markApplied(track("a"), nowMillis = 10_000)
        assertTrue(gate.shouldApply(track("b"), masterEnabled = true, nowMillis = 10_500))
    }
}
