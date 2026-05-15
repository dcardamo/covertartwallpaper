# Covert Art Wallpaper — Design

**Date:** 2026-05-14
**Status:** Approved, ready for implementation planning

## Summary

An Android app that watches whatever music app is playing (Spotify, Tidal, or
anything that publishes a media session) and sets the device wallpaper — home
and lock — to the current track's cover art, rendered to fit a tall portrait
screen attractively. Built for a FiiO M33 DAP, installed and auto-updated via
Obtainium, built in GitHub Actions, developed on NixOS.

## Goals

- Real-time wallpaper changes as the playing track changes.
- Generic across media apps — no per-app integration.
- Cover art presented full-screen in a way that suits the Niagara launcher,
  whose clock/app text is pinned to the left edge.
- A development loop where the wallpaper rendering can be visually iterated on
  the host machine, with no device round-trip.
- Hands-off distribution: push to GitHub → CI builds a signed APK → Obtainium
  offers the update.

## Non-goals

- No idle/default-wallpaper state. When playback pauses or stops, the last
  cover art stays up until the next track plays.
- No in-app configuration of the visual style — the chosen treatment is baked
  in. The app UI is a minimal status screen only.
- No live wallpaper / animated transitions. Wallpaper is applied as a static
  bitmap; transitions are whatever Android does by default.
- No cross-platform framework. Native Kotlin only.

## Platform & stack

- **Language:** Kotlin. **Build:** Gradle (project wrapper). **UI:** Jetpack
  Compose for the single screen.
- **applicationId:** `ca.hld.covertart`
- **minSdk:** 29 · **targetSdk:** 34 · **compileSdk:** 34
  (FiiO M33 runs Android 13 / API 33.)
- **Dev environment:** NixOS, via `flake.nix` + `.envrc` (direnv `use flake`).

## Behaviour decisions

These were settled during brainstorming:

| Decision | Choice |
|------------------------------|--------------------------------------------------------------|
| Pause / stop behaviour | Keep last cover art until the next track plays. No revert. |
| Wallpaper surfaces | Both home screen and lock screen. |
| Application mechanism | Static bitmap via `WallpaperManager.setBitmap`. Not a live wallpaper. |
| App UI scope | Minimal status screen only — no style configuration. |
| Visual treatment | "G": full-height crop, anchored right (see Section 3). |
| Preview rendering | Thin executor abstraction; AWT implementation for host-side previews. |

## 1. Architecture & components

| Component | Responsibility |
|-----------------------|-------------------------------------------------------------------------------------------|
| `MediaWatcherService` | Extends `NotificationListenerService`. Notification access is the permission gate. Uses `MediaSessionManager.getActiveSessions()` to enumerate `MediaController`s, registers an `OnActiveSessionsChangedListener` plus a `MediaController.Callback` per controller for real-time metadata/playback changes. Generic across media apps. Stays alive while notification access is granted. |
| `TrackResolver` | Pure logic. Given the active `MediaController`s, picks the session to use (prefer `PLAYING`; tie-break most-recent). Extracts artist/title/album and the album-art bitmap with a fallback chain: `METADATA_KEY_ALBUM_ART` → `METADATA_KEY_ART` → `METADATA_KEY_DISPLAY_ICON`. Produces a `NowPlaying`. |
| `ChangeGate` | Pure logic. Decides whether an apply should happen: master toggle on, track identity differs from last applied, debounce window (~400 ms) elapsed. Absorbs duplicate callbacks, seeks, and replays. |
| `WallpaperRenderer` | Splits into a pure `RenderPlan` producer and a `WallpaperExecutor`. See Section 3. |
| `WallpaperApplier` | Wraps `WallpaperManager.setBitmap(bitmap, null, true, FLAG_SYSTEM \| FLAG_LOCK)` — sets home + lock in one call. |
| `AppState` | DataStore-backed: master on/off toggle, last-applied track string, current status string. |
| `MainActivity` | Minimal Jetpack Compose status screen: a button that deep-links to the notification-access settings, a master on/off toggle, and a status line (e.g. `Listening — last set: Artist – Track`). Observes `AppState`. |

`MediaWatcherService` is the only long-lived process. As a bound
`NotificationListenerService`, Android keeps it alive whenever notification
access is granted — no separate foreground service is needed, which matters
for battery on a DAP.

## 2. Data flow

1. A media app changes track → `MediaController.Callback.onMetadataChanged` /
   `onPlaybackStateChanged` fires inside `MediaWatcherService`.
2. `TrackResolver` picks the active controller and builds a `NowPlaying`
   (artist, title, album, album-art bitmap, playback state, identity key).
3. `ChangeGate` checks: master toggle on? identity differs from last applied?
   debounce window elapsed? If any check fails, stop here.
4. `WallpaperRenderer` produces a `RenderPlan` and executes it off the main
   thread (`Dispatchers.Default`) to produce the final bitmap.
5. `WallpaperApplier` sets it for home + lock.
6. `AppState` is updated with the last-applied track and timestamp;
   `MainActivity`'s status line reflects it.

Pause / stop produces no action — the last cover art stays. On
`onListenerConnected` (service start, including after a device reboot) the
service evaluates current sessions once, so the wallpaper is correct if
something is already playing.

## 3. Rendering pipeline — the "G" treatment

The cover art is square; the screen is portrait (1080×1920 on the M33).
Treatment "G": **full-height crop, anchored right.**

The renderer is split so that the geometry is pure and testable and the pixel
drawing is a thin, swappable executor:

- **`RenderPlan`** — pure data, produced by pure functions:
  - `targetSize` — the real display size from `WindowManager` / `DisplayMetrics`.
  - `srcRect` — the region of the source art to draw from.
  - `dstRect` — where it lands on the target canvas.
  - `scrimRect` + `scrimColors` — the left-side legibility scrim.

  Geometry: scale the square art so its **height fills the screen**; its
  scaled width then overflows the screen width. Anchor the art's **right edge
  to the screen's right edge** (`dstRect.left = targetWidth - scaledWidth`,
  negative — the left slice runs off-screen behind where Niagara's text sits).
  No vertical crop.

- **`WallpaperExecutor`** — interface that takes a `RenderPlan` plus the source
  image and produces pixels. Two implementations, each ~20 lines:
  - `AndroidCanvasExecutor` — device path. `android.graphics.Canvas`,
    `Bitmap`, `LinearGradient`.
  - `AwtExecutor` — host/preview path. `BufferedImage`, `Graphics2D`,
    `GradientPaint`.

- **Left scrim:** a horizontal gradient drawn over the art — black at ~75%
  opacity at x=0, fading to transparent by ~35% of the width. Fixed (not
  sampled from the art); guarantees white Niagara text stays legible against
  any cover. Tunable via the preview harness.

- **No album art available:** skip the apply, keep the previous wallpaper,
  and note it in the status string.

## 4. Build & distribution

- **Nix dev env:** `flake.nix` provides JDK 17 and the Android SDK
  (`cmdline-tools`, `platform-tools`, `platforms;android-34`,
  `build-tools;34.0.0`) via `androidenv.composeAndroidPackages`, exporting
  `ANDROID_HOME` / `ANDROID_SDK_ROOT`. `.envrc` is `use flake`. Gradle comes
  from the project's Gradle wrapper.
- **Gradle app:** single module. `applicationId` `ca.hld.covertart`,
  minSdk 29, targetSdk 34, compileSdk 34.
- **Versioning for Obtainium:** `versionCode` = the GitHub Actions run number
  (`github.run_number`), injected as a Gradle property; `versionName` =
  `1.0.<run_number>`. Monotonic, so Obtainium detects every build as an update.
- **Signing:** a release keystore is generated once (locally, with `keytool`)
  and stored — base64-encoded — as GitHub Actions secrets along with the key
  alias and passwords (`SIGNING_KEYSTORE`, `SIGNING_KEY_ALIAS`,
  `SIGNING_KEY_PASSWORD`, `SIGNING_STORE_PASSWORD`). CI decodes it and signs
  `assembleRelease`. The keystore is never committed. A consistent signing key
  is what lets Obtainium treat new builds as updates rather than conflicting
  installs.
- **GitHub Actions** — on push to `main` (plus manual dispatch):
  1. Checkout.
  2. Set up JDK 17.
  3. Set up the Android SDK.
  4. `./gradlew test` — unit tests.
  5. Run the preview harness; upload `build/previews/` as a workflow artifact.
  6. `./gradlew assembleRelease` — signed.
  7. Create a GitHub Release tagged `v1.0.<run_number>` with the APK attached.
- **Obtainium:** the repo URL is added once; Obtainium watches releases and
  offers each higher-`versionCode` build as an update.

## 5. Testing

Automated JVM unit tests — `./gradlew test`, run in CI before every build, no
emulator required:

- **`TrackResolver`** — session selection (prefer `PLAYING`, tie-break
  most-recent), the album-art fallback chain, and the no-session / no-art
  cases. Driven by fake controller/metadata fixtures.
- **`RenderPlan` geometry** — pure functions: for given source-art and screen
  dimensions, assert the exact scale factor, the right-anchored `dstRect`
  offset, and the scrim rectangle bounds.
- **`ChangeGate`** — identity dedup, debounce window, and master-toggle gating.

The sample cover images used by the preview harness double as test fixtures.
The `AndroidCanvasExecutor`, the `NotificationListenerService` binding, and the
actual `setBitmap` call are device-dependent; they are kept deliberately thin
and are covered by the push → Obtainium → M33 loop.

## 6. Error handling & edge cases

- **No notification access** — `MainActivity` surfaces the grant button
  prominently; the service does nothing until access is granted.
- **Master toggle off** — the service stays bound but ignores all events.
- **No active media session** — idle; nothing changes.
- **Active session but no album art** — skip the apply, keep the previous
  wallpaper, note the missing art in the status string.
- **Multiple players** (Spotify and Tidal both holding sessions) —
  `TrackResolver` prefers the `PLAYING` one; ties broken by most-recent.
- **Rapid or duplicate metadata callbacks, seeks, replays** — `ChangeGate`'s
  debounce plus identity dedup absorb them; no redundant wallpaper sets.
- **`setBitmap` throws** — caught and logged; the status string shows the
  error; the next event retries.
- **Service killed / device rebooted** — the `NotificationListenerService`
  rebinds; `onListenerConnected` re-evaluates current sessions and applies
  once, so the wallpaper is correct if music is already playing.
- **Bitmap memory** — intermediate bitmaps are recycled; rendering runs on
  `Dispatchers.Default`; source-art scaling is bounded.

## 7. Preview harness

The device round-trip (push → CI build → Obtainium → look at the M33) is far
too slow for tuning the visual treatment. The harness makes the rendering
iterable on the host:

- A Gradle task / JVM entry point runs `WallpaperRenderer` through
  `AwtExecutor` against a set of bundled **sample covers**, chosen to stress
  text legibility: dark, bright, busy, pale, and high-contrast-edge.
- Each rendered wallpaper has a **faux-Niagara overlay** composited on top —
  clock plus a few app names, left-pinned — so the result is seen in context:
  does the scrim keep the text readable, does the crop look right.
- Output PNGs are written to `build/previews/`, one per sample cover.
- During development these PNGs are read directly to iterate on scrim opacity,
  gradient width, and crop anchor — no device needed.
- CI runs the harness and uploads `build/previews/` as a workflow artifact, so
  every build carries a visual record.

## Open items for implementation planning

- Exact scrim opacity / gradient-width starting values (tuned via the harness).
- `flake.nix` specifics for `androidenv` and the unfree Android SDK licence.
- Sourcing the bundled sample cover images (license-clean placeholders).
- GitHub Release strategy details (rolling tag vs. per-build tag) — current
  plan is a per-build `v1.0.<run_number>` tag.
