package ca.hld.covertart.core

/**
 * Pure gate deciding whether a wallpaper apply should happen. Holds mutable
 * last-applied state; not thread-safe — call from a single coroutine.
 *
 * The debounce check here is a hard floor on apply frequency; the service
 * additionally applies a trailing debounce so rapid bursts collapse to the
 * latest event rather than being dropped.
 */
class ChangeGate(private val debounceMillis: Long = 400L) {

    private var lastAppliedIdentity: String? = null
    private var lastAppliedAtMillis: Long? = null

    /** @return true if the caller should render + apply for [nowPlaying]. */
    fun shouldApply(nowPlaying: NowPlaying, masterEnabled: Boolean, nowMillis: Long): Boolean {
        if (!masterEnabled) return false
        if (nowPlaying.identityKey == lastAppliedIdentity) return false
        val sinceLastApply = lastAppliedAtMillis?.let { nowMillis - it }
        if (sinceLastApply != null && sinceLastApply < debounceMillis) return false
        return true
    }

    /** Record a successful apply so duplicates and replays are absorbed. */
    fun markApplied(nowPlaying: NowPlaying, nowMillis: Long) {
        lastAppliedIdentity = nowPlaying.identityKey
        lastAppliedAtMillis = nowMillis
    }
}
