package ca.hld.covertart.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState as AndroidPlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import ca.hld.covertart.core.ChangeGate
import ca.hld.covertart.core.MetadataView
import ca.hld.covertart.core.PlaybackState
import ca.hld.covertart.core.SessionSnapshot
import ca.hld.covertart.core.SourceImage
import ca.hld.covertart.core.TrackResolver
import ca.hld.covertart.data.AppState
import ca.hld.covertart.device.WallpaperApplier
import ca.hld.covertart.device.screenSize
import ca.hld.covertart.render.AndroidCanvasExecutor
import ca.hld.covertart.render.AndroidSourceImage
import ca.hld.covertart.render.WallpaperRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The only long-lived process. As a NotificationListenerService, Android keeps
 * it bound whenever notification access is granted — no foreground service
 * needed. Enumerates media sessions, follows the active one, and applies the
 * cover art as wallpaper.
 */
class MediaWatcherService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = ChangeGate(debounceMillis = DEBOUNCE_MILLIS)
    private val renderer = WallpaperRenderer(AndroidCanvasExecutor())

    private lateinit var sessionManager: MediaSessionManager
    private lateinit var appState: AppState
    private lateinit var applier: WallpaperApplier
    private lateinit var componentName: ComponentName

    private val controllers = mutableListOf<MediaController>()
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val lastActiveAt = mutableMapOf<MediaController, Long>()
    private var pendingJob: Job? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list ->
            updateControllers(list ?: emptyList())
            onSessionEvent()
        }

    override fun onListenerConnected() {
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        appState = AppState(applicationContext)
        applier = WallpaperApplier(applicationContext)
        componentName = ComponentName(this, MediaWatcherService::class.java)

        sessionManager.addOnActiveSessionsChangedListener(sessionsListener, componentName)
        updateControllers(sessionManager.getActiveSessions(componentName))
        // Evaluate whatever is already playing right now (covers reboot/restart).
        onSessionEvent()
    }

    override fun onListenerDisconnected() {
        sessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        controllers.forEach { c -> controllerCallbacks[c]?.let(c::unregisterCallback) }
        controllers.clear()
        controllerCallbacks.clear()
        lastActiveAt.clear()
        pendingJob?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Diff the active controller list, registering/unregistering callbacks. */
    private fun updateControllers(active: List<MediaController>) {
        controllers.filter { it !in active }.forEach { gone ->
            controllerCallbacks.remove(gone)?.let(gone::unregisterCallback)
            lastActiveAt.remove(gone)
        }
        active.filter { it !in controllers }.forEach { added ->
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    touch(added); onSessionEvent()
                }
                override fun onPlaybackStateChanged(state: AndroidPlaybackState?) {
                    touch(added); onSessionEvent()
                }
                override fun onSessionDestroyed() {
                    onSessionEvent()
                }
            }
            added.registerCallback(cb)
            controllerCallbacks[added] = cb
            lastActiveAt[added] = System.currentTimeMillis()
        }
        controllers.clear()
        controllers.addAll(active)
    }

    private fun touch(controller: MediaController) {
        lastActiveAt[controller] = System.currentTimeMillis()
    }

    /** Trailing debounce: each event cancels the previous pending evaluation. */
    private fun onSessionEvent() {
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(DEBOUNCE_MILLIS)
            evaluate()
        }
    }

    private suspend fun evaluate() {
        val snapshots = controllers.map { c ->
            SessionSnapshot(
                metadata = AndroidMetadataView(c.metadata),
                playbackState = c.playbackState.toCore(),
                lastActiveAtMillis = lastActiveAt[c] ?: 0L,
            )
        }
        val nowPlaying = TrackResolver.resolve(snapshots) ?: return
        // Pause/stop produces no action — keep the last cover art up.
        if (nowPlaying.playbackState != PlaybackState.PLAYING) return

        val masterEnabled = appState.masterEnabled.first()
        val now = System.currentTimeMillis()
        if (!gate.shouldApply(nowPlaying, masterEnabled, now)) return

        val label = "${nowPlaying.artist} – ${nowPlaying.title}"
        val art = nowPlaying.art
        if (art == null) {
            appState.setStatus("Playing $label — no album art, kept previous wallpaper")
            return
        }
        try {
            val (w, h) = screenSize(applicationContext)
            val bitmap = renderer.render(art, w, h)
            applier.apply(bitmap)
            bitmap.recycle()
            gate.markApplied(nowPlaying, now)
            appState.setLastTrack(label)
            appState.setStatus("Listening — last set: $label")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set wallpaper", e)
            appState.setStatus("Error setting wallpaper: ${e.message}")
        }
    }

    private fun AndroidPlaybackState?.toCore(): PlaybackState = when (this?.state) {
        AndroidPlaybackState.STATE_PLAYING -> PlaybackState.PLAYING
        AndroidPlaybackState.STATE_PAUSED -> PlaybackState.PAUSED
        AndroidPlaybackState.STATE_STOPPED, AndroidPlaybackState.STATE_NONE -> PlaybackState.STOPPED
        else -> PlaybackState.OTHER
    }

    /** Wraps a MediaMetadata as the JVM-testable MetadataView. */
    private class AndroidMetadataView(private val metadata: MediaMetadata?) : MetadataView {
        override fun string(key: String): String? = metadata?.getString(key)
        override fun bitmap(key: String): SourceImage? =
            metadata?.getBitmap(key)?.let { AndroidSourceImage(it) }
    }

    companion object {
        private const val TAG = "MediaWatcherService"
        private const val DEBOUNCE_MILLIS = 400L
    }
}
