# Covert Art Wallpaper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-extended-cc:subagent-driven-development (recommended) or superpowers-extended-cc:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android app (`ca.hld.covertart`) that watches any media session and sets the home + lock wallpaper to the playing track's cover art, rendered with the right-anchored "G" treatment.

**Architecture:** Single-module Kotlin/Gradle Android app. All decision logic (`TrackResolver`, `ChangeGate`, `RenderPlan` geometry) is pure and JVM-unit-tested. The renderer splits into a pure planner plus a swappable `WallpaperExecutor` — `AndroidCanvasExecutor` on device, `AwtExecutor` in the host-side preview harness. The only long-lived process is a `NotificationListenerService`.

**Tech Stack:** Kotlin 2.1.0, Gradle 8.11.1, AGP 8.7.3, Jetpack Compose (BOM 2024.12.01), DataStore, kotlinx-coroutines. JDK 17 + Android SDK via a Nix flake. CI on GitHub Actions, distribution via Obtainium.

---

## File Structure

```
flake.nix                          # JDK 17 + Android SDK dev shell (Task 0)
.envrc                             # direnv: use flake (Task 0)
settings.gradle.kts                # Gradle settings, repos, :app module (Task 1)
build.gradle.kts                   # root build script (Task 1)
gradle.properties                  # Gradle/AndroidX flags (Task 1)
gradle/libs.versions.toml          # version catalog (Task 1)
gradlew / gradlew.bat / gradle/wrapper/*   # Gradle wrapper (Task 1)
.github/workflows/build.yml        # CI: test, previews, signed release (Task 10)
README.md                          # one-time setup notes (Task 10)
app/build.gradle.kts               # app module build (Tasks 1, 6, 10)
app/proguard-rules.pro             # empty placeholder (Task 1)
app/src/main/AndroidManifest.xml   # activity (Task 1), service + permission (Task 8)
app/src/main/java/ca/hld/covertart/
  MainActivity.kt                  # Compose status screen (Tasks 1, 9)
  core/SourceImage.kt              # platform-agnostic image handle (Task 2)
  core/NowPlaying.kt               # NowPlaying + PlaybackState (Task 2)
  core/MetadataView.kt             # MetadataView, SessionSnapshot, MetadataKeys (Task 2)
  core/TrackResolver.kt            # pure session selection + extraction (Task 2)
  core/ChangeGate.kt               # pure apply-or-not gate (Task 3)
  render/RenderPlan.kt             # IntRect, RenderConfig, RenderPlan, planRender (Task 4)
  render/WallpaperExecutor.kt      # executor interface (Task 5)
  render/AndroidCanvasExecutor.kt  # AndroidSourceImage + device executor (Task 5)
  render/WallpaperRenderer.kt      # planner + executor glue (Task 5)
  data/AppState.kt                 # DataStore-backed state (Task 7)
  device/WallpaperApplier.kt       # WallpaperManager.setBitmap wrapper (Task 8)
  device/ScreenSize.kt             # real display size helper (Task 8)
  service/MediaWatcherService.kt   # NotificationListenerService + wiring (Task 8)
app/src/test/java/ca/hld/covertart/
  core/TrackResolverTest.kt        # (Task 2)
  core/ChangeGateTest.kt           # (Task 3)
  render/RenderPlanTest.kt         # (Task 4)
  render/AwtExecutor.kt            # AwtSourceImage + host executor (Task 6)
  preview/SampleCovers.kt          # procedural license-clean covers (Task 6)
  preview/FauxNiagaraOverlay.kt    # left-pinned clock/app overlay (Task 6)
  preview/PreviewHarness.kt        # JVM entry point (Task 6)
  preview/PreviewHarnessTest.kt    # drives the harness, asserts output (Task 6)
```

---

### Task 0: Nix dev environment

**Goal:** A `nix develop` shell that provides JDK 17, Gradle, and the Android SDK with `ANDROID_HOME` set.

**Files:**
- Create: `flake.nix`
- Create: `.envrc`

**Acceptance Criteria:**
- [ ] `nix develop` produces a shell with `java -version` reporting 17.
- [ ] `$ANDROID_HOME` / `$ANDROID_SDK_ROOT` point at an SDK with `platforms/android-34` and `build-tools/34.0.0`.
- [ ] `gradle --version` works inside the shell.

**Verify:** `nix develop --command bash -c 'java -version 2>&1 | head -1 && ls "$ANDROID_HOME/platforms" && gradle --version | head -3'` → shows `17`, `android-34`, and a Gradle version.

**Steps:**

- [ ] **Step 1: Write `flake.nix`**

```nix
{
  description = "Covert Art Wallpaper — Android dev environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  inputs.flake-utils.url = "github:numtide/flake-utils";

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          cmdLineToolsVersion = "11.0";
          platformToolsVersion = "34.0.5";
          buildToolsVersions = [ "34.0.0" ];
          platformVersions = [ "34" ];
          includeEmulator = false;
          includeSystemImages = false;
          includeSources = false;
        };
        androidSdk = androidComposition.androidsdk;
        sdkRoot = "${androidSdk}/libexec/android-sdk";
      in {
        devShells.default = pkgs.mkShell {
          buildInputs = [ pkgs.jdk17 pkgs.gradle androidSdk ];
          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;
          JAVA_HOME = "${pkgs.jdk17}";
          # NixOS ships its own aapt2; point Gradle at the SDK's prebuilt one.
          shellHook = ''
            export GRADLE_OPTS="-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdkRoot}/build-tools/34.0.0/aapt2"
          '';
        };
      });
}
```

- [ ] **Step 2: Write `.envrc`**

```bash
use flake
```

- [ ] **Step 3: Verify the shell**

Run: `nix develop --command bash -c 'java -version 2>&1 | head -1 && ls "$ANDROID_HOME/platforms" && gradle --version | head -3'`
Expected: a line containing `17`, a line `android-34`, and Gradle version output.

- [ ] **Step 4: Commit**

```bash
git add flake.nix .envrc flake.lock
git commit -m "build: add Nix dev environment with JDK 17 and Android SDK"
```

---

### Task 1: Gradle project scaffold

**Goal:** A buildable Android app skeleton — `./gradlew assembleDebug` produces an APK and `./gradlew test` runs (zero tests, passes).

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (generated)
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/ca/hld/covertart/MainActivity.kt`

**Acceptance Criteria:**
- [ ] `./gradlew assembleDebug` builds `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] `./gradlew test` succeeds.
- [ ] `applicationId` is `ca.hld.covertart`, minSdk 29, targetSdk/compileSdk 34.

**Verify:** `nix develop --command ./gradlew assembleDebug test` → `BUILD SUCCESSFUL`.

**Steps:**

- [ ] **Step 1: Write `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
datastore = "1.1.1"
kotlinxCoroutines = "1.9.0"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 2: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "covertartwallpaper"
include(":app")
```

- [ ] **Step 3: Write root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

- [ ] **Step 4: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 5: Write `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// versionCode is supplied by CI as the GitHub Actions run number; default 1 locally.
val runNumber: Int = (project.findProperty("versionCode") as String?)?.toInt() ?: 1

android {
    namespace = "ca.hld.covertart"
    compileSdk = 34

    defaultConfig {
        applicationId = "ca.hld.covertart"
        minSdk = 29
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
}
```

- [ ] **Step 6: Write `app/proguard-rules.pro`**

```proguard
# R8 is disabled for release (isMinifyEnabled = false); no rules needed yet.
```

- [ ] **Step 7: Write `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:label="Covert Art Wallpaper"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
```

- [ ] **Step 8: Write a placeholder `app/src/main/java/ca/hld/covertart/MainActivity.kt`**

```kotlin
package ca.hld.covertart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

// Placeholder — replaced by the full status screen in Task 9.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Covert Art Wallpaper") }
    }
}
```

- [ ] **Step 9: Generate the Gradle wrapper**

Run: `nix develop --command gradle wrapper --gradle-version 8.11.1`
Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.

- [ ] **Step 10: Build and verify**

Run: `nix develop --command ./gradlew assembleDebug test`
Expected: `BUILD SUCCESSFUL`; `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 11: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/ gradlew gradlew.bat app/
git commit -m "build: scaffold Gradle Android project (ca.hld.covertart)"
```

---

### Task 2: TrackResolver and core media types

**Goal:** Pure, JVM-tested logic that picks the session to follow from a list of snapshots and builds a `NowPlaying` with the album-art fallback chain.

**Files:**
- Create: `app/src/main/java/ca/hld/covertart/core/SourceImage.kt`
- Create: `app/src/main/java/ca/hld/covertart/core/NowPlaying.kt`
- Create: `app/src/main/java/ca/hld/covertart/core/MetadataView.kt`
- Create: `app/src/main/java/ca/hld/covertart/core/TrackResolver.kt`
- Test: `app/src/test/java/ca/hld/covertart/core/TrackResolverTest.kt`

**Acceptance Criteria:**
- [ ] `pickSession` prefers a `PLAYING` session and tie-breaks (and falls back) to the most recently active.
- [ ] `resolve` extracts art via `ALBUM_ART → ART → DISPLAY_ICON`, returning the first present.
- [ ] `resolve` returns `null` for an empty session list and a `NowPlaying` with `art = null` when no art key is present.
- [ ] `NowPlaying.identityKey` ignores playback position.

**Verify:** `nix develop --command ./gradlew testDebugUnitTest --tests "*TrackResolverTest"` → `BUILD SUCCESSFUL`.

**Steps:**

- [ ] **Step 1: Write the failing test `app/src/test/java/ca/hld/covertart/core/TrackResolverTest.kt`**

```kotlin
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
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `nix develop --command ./gradlew testDebugUnitTest --tests "*TrackResolverTest"`
Expected: FAIL — unresolved references (`SourceImage`, `MetadataView`, etc.).

- [ ] **Step 3: Write `app/src/main/java/ca/hld/covertart/core/SourceImage.kt`**

```kotlin
package ca.hld.covertart.core

/**
 * Platform-agnostic handle to a source cover image. Pure logic only ever needs
 * its dimensions; executors downcast to their concrete subtype to read pixels.
 */
interface SourceImage {
    val width: Int
    val height: Int
}
```

- [ ] **Step 4: Write `app/src/main/java/ca/hld/covertart/core/NowPlaying.kt`**

```kotlin
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
    /** Stable identity for dedup — independent of playback position/seeks. */
    val identityKey: String = "$artist $album $title"
}
```

- [ ] **Step 5: Write `app/src/main/java/ca/hld/covertart/core/MetadataView.kt`**

```kotlin
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
```

- [ ] **Step 6: Write `app/src/main/java/ca/hld/covertart/core/TrackResolver.kt`**

```kotlin
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
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `nix develop --command ./gradlew testDebugUnitTest --tests "*TrackResolverTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/ca/hld/covertart/core/ app/src/test/java/ca/hld/covertart/core/TrackResolverTest.kt
git commit -m "feat: add TrackResolver with session selection and art fallback"
```

---

### Task 3: ChangeGate

**Goal:** Pure gate that decides whether a wallpaper apply should happen — master toggle, identity dedup, debounce floor.

**Files:**
- Create: `app/src/main/java/ca/hld/covertart/core/ChangeGate.kt`
- Test: `app/src/test/java/ca/hld/covertart/core/ChangeGateTest.kt`

**Acceptance Criteria:**
- [ ] Returns `false` when master is disabled.
- [ ] Returns `false` when the track identity equals the last applied identity.
- [ ] Returns `false` when less than the debounce window has elapsed since the last apply.
- [ ] Returns `true` for a new identity once the debounce window has elapsed.
- [ ] After `markApplied`, the same identity is gated out.

**Verify:** `nix develop --command ./gradlew testDebugUnitTest --tests "*ChangeGateTest"` → `BUILD SUCCESSFUL`.

**Steps:**

- [ ] **Step 1: Write the failing test `app/src/test/java/ca/hld/covertart/core/ChangeGateTest.kt`**

```kotlin
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `nix develop --command ./gradlew testDebugUnitTest --tests "*ChangeGateTest"`
Expected: FAIL — unresolved reference `ChangeGate`.

- [ ] **Step 3: Write `app/src/main/java/ca/hld/covertart/core/ChangeGate.kt`**

```kotlin
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
    private var lastAppliedAtMillis: Long = Long.MIN_VALUE

    /** @return true if the caller should render + apply for [nowPlaying]. */
    fun shouldApply(nowPlaying: NowPlaying, masterEnabled: Boolean, nowMillis: Long): Boolean {
        if (!masterEnabled) return false
        if (nowPlaying.identityKey == lastAppliedIdentity) return false
        if (nowMillis - lastAppliedAtMillis < debounceMillis) return false
        return true
    }

    /** Record a successful apply so duplicates and replays are absorbed. */
    fun markApplied(nowPlaying: NowPlaying, nowMillis: Long) {
        lastAppliedIdentity = nowPlaying.identityKey
        lastAppliedAtMillis = nowMillis
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `nix develop --command ./gradlew testDebugUnitTest --tests "*ChangeGateTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ca/hld/covertart/core/ChangeGate.kt app/src/test/java/ca/hld/covertart/core/ChangeGateTest.kt
git commit -m "feat: add ChangeGate apply-or-skip logic"
```

---

### Task 4: RenderPlan geometry

**Goal:** Pure `planRender` function producing the "G" treatment geometry — full-height crop, right-anchored — plus the left scrim rectangle.

**Files:**
- Create: `app/src/main/java/ca/hld/covertart/render/RenderPlan.kt`
- Test: `app/src/test/java/ca/hld/covertart/render/RenderPlanTest.kt`

**Acceptance Criteria:**
- [ ] Source is scaled so its height exactly fills the target height.
- [ ] `dstRect` right edge equals `targetWidth`; `dstRect.left` is `targetWidth - scaledWidth` (negative when the art overflows).
- [ ] `srcRect` covers the whole source (no vertical crop).
- [ ] `scrimRect` spans `0..round(targetWidth * scrimEndFraction)` wide, full target height.
- [ ] `planRender` rejects non-positive dimensions.

**Verify:** `nix develop --command ./gradlew testDebugUnitTest --tests "*RenderPlanTest"` → `BUILD SUCCESSFUL`.

**Steps:**

- [ ] **Step 1: Write the failing test `app/src/test/java/ca/hld/covertart/render/RenderPlanTest.kt`**

```kotlin
package ca.hld.covertart.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RenderPlanTest {

    @Test
    fun squareArtOnPortraitScreenIsRightAnchored() {
        val plan = planRender(srcWidth = 1000, srcHeight = 1000, targetWidth = 1080, targetHeight = 1920)
        // height fills: scale = 1920/1000 = 1.92 -> scaledWidth = 1920
        assertEquals(IntRect(-840, 0, 1080, 1920), plan.dstRect)
        assertEquals(IntRect(0, 0, 1000, 1000), plan.srcRect)
        assertEquals(IntRect(0, 0, 1920, 1920), plan.targetSize)
    }

    @Test
    fun nonSquareArtScalesByHeight() {
        val plan = planRender(srcWidth = 600, srcHeight = 800, targetWidth = 1080, targetHeight = 1920)
        // scale = 1920/800 = 2.4 -> scaledWidth = 1440 -> left = 1080 - 1440 = -360
        assertEquals(IntRect(-360, 0, 1080, 1920), plan.dstRect)
    }

    @Test
    fun scrimSpansConfiguredFractionOfWidth() {
        val plan = planRender(1000, 1000, 1080, 1920, RenderConfig(scrimEndFraction = 0.35f))
        // round(1080 * 0.35) = 378
        assertEquals(IntRect(0, 0, 378, 1920), plan.scrimRect)
        assertEquals(0.75f, plan.scrimStartAlpha, 0.0001f)
        assertEquals(0.35f, plan.scrimEndFraction, 0.0001f)
    }

    @Test
    fun rejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException::class.java) { planRender(0, 100, 100, 100) }
        assertThrows(IllegalArgumentException::class.java) { planRender(100, 100, 100, 0) }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `nix develop --command ./gradlew testDebugUnitTest --tests "*RenderPlanTest"`
Expected: FAIL — unresolved references (`planRender`, `IntRect`, `RenderConfig`).

- [ ] **Step 3: Write `app/src/main/java/ca/hld/covertart/render/RenderPlan.kt`**

```kotlin
package ca.hld.covertart.render

/** Inclusive-left, exclusive-right integer rectangle. No platform dependency. */
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Tunable visual parameters for the "G" treatment. The defaults are the
 * preview-harness starting point — iterate on these via build/previews/.
 */
data class RenderConfig(
    /** Opacity of the black scrim at x = 0. */
    val scrimStartAlpha: Float = 0.75f,
    /** Scrim fades to transparent by this fraction of the target width. */
    val scrimEndFraction: Float = 0.35f,
)

/** Pure description of how to paint one wallpaper frame. No platform types. */
data class RenderPlan(
    val targetSize: IntRect,
    val srcRect: IntRect,
    val dstRect: IntRect,
    val scrimRect: IntRect,
    val scrimStartAlpha: Float,
    val scrimEndFraction: Float,
)

/**
 * "G" treatment: scale the source so its HEIGHT fills the target, anchor its
 * RIGHT edge to the target's right edge (the left slice runs off-screen behind
 * where Niagara's text sits). No vertical crop.
 */
fun planRender(
    srcWidth: Int,
    srcHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    config: RenderConfig = RenderConfig(),
): RenderPlan {
    require(srcWidth > 0 && srcHeight > 0) { "source dimensions must be positive" }
    require(targetWidth > 0 && targetHeight > 0) { "target dimensions must be positive" }

    val scale = targetHeight.toFloat() / srcHeight.toFloat()
    val scaledWidth = Math.round(srcWidth * scale)
    val dstLeft = targetWidth - scaledWidth // negative when the art overflows

    val scrimWidth = Math.round(targetWidth * config.scrimEndFraction)

    return RenderPlan(
        targetSize = IntRect(0, 0, targetWidth, targetHeight),
        srcRect = IntRect(0, 0, srcWidth, srcHeight),
        dstRect = IntRect(dstLeft, 0, targetWidth, targetHeight),
        scrimRect = IntRect(0, 0, scrimWidth, targetHeight),
        scrimStartAlpha = config.scrimStartAlpha,
        scrimEndFraction = config.scrimEndFraction,
    )
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `nix develop --command ./gradlew testDebugUnitTest --tests "*RenderPlanTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ca/hld/covertart/render/RenderPlan.kt app/src/test/java/ca/hld/covertart/render/RenderPlanTest.kt
git commit -m "feat: add RenderPlan geometry for the G treatment"
```

---

### Task 5: WallpaperExecutor interface, Android executor, and renderer

**Goal:** The executor interface plus the device-path `AndroidCanvasExecutor` and the `WallpaperRenderer` that glues the pure planner to an executor.

**Files:**
- Create: `app/src/main/java/ca/hld/covertart/render/WallpaperExecutor.kt`
- Create: `app/src/main/java/ca/hld/covertart/render/AndroidCanvasExecutor.kt`
- Create: `app/src/main/java/ca/hld/covertart/render/WallpaperRenderer.kt`

**Acceptance Criteria:**
- [ ] `WallpaperExecutor<T>` exposes `execute(plan, source): T`.
- [ ] `AndroidCanvasExecutor` produces an `android.graphics.Bitmap` sized to `plan.targetSize`, draws the art per `dstRect`, and overlays the left gradient scrim.
- [ ] `AndroidSourceImage` wraps an `android.graphics.Bitmap` as a `SourceImage`.
- [ ] `WallpaperRenderer<T>` runs `planRender` then the executor.
- [ ] The module still compiles: `./gradlew assembleDebug` succeeds.

**Verify:** `nix develop --command ./gradlew assembleDebug` → `BUILD SUCCESSFUL`.

**Steps:**

- [ ] **Step 1: Write `app/src/main/java/ca/hld/covertart/render/WallpaperExecutor.kt`**

```kotlin
package ca.hld.covertart.render

import ca.hld.covertart.core.SourceImage

/**
 * Paints a [RenderPlan] into a platform bitmap of type [T]. Implementations are
 * deliberately thin and downcast [SourceImage] to their matching concrete type.
 */
interface WallpaperExecutor<T> {
    fun execute(plan: RenderPlan, source: SourceImage): T
}
```

- [ ] **Step 2: Write `app/src/main/java/ca/hld/covertart/render/AndroidCanvasExecutor.kt`**

```kotlin
package ca.hld.covertart.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import ca.hld.covertart.core.SourceImage

/** A SourceImage backed by an android.graphics.Bitmap (device path). */
class AndroidSourceImage(val bitmap: Bitmap) : SourceImage {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
}

/** Device-path executor: android.graphics.Canvas + LinearGradient. */
class AndroidCanvasExecutor : WallpaperExecutor<Bitmap> {

    override fun execute(plan: RenderPlan, source: SourceImage): Bitmap {
        val src = (source as AndroidSourceImage).bitmap
        val out = Bitmap.createBitmap(
            plan.targetSize.width,
            plan.targetSize.height,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(out)

        val srcRect = Rect(plan.srcRect.left, plan.srcRect.top, plan.srcRect.right, plan.srcRect.bottom)
        val dstRect = Rect(plan.dstRect.left, plan.dstRect.top, plan.dstRect.right, plan.dstRect.bottom)
        canvas.drawBitmap(src, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))

        val scrimEndX = plan.scrimRect.right.toFloat()
        val startColor = Color.argb((plan.scrimStartAlpha * 255f).toInt(), 0, 0, 0)
        val endColor = Color.argb(0, 0, 0, 0)
        val scrimPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, scrimEndX, 0f, startColor, endColor, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, scrimEndX, plan.scrimRect.bottom.toFloat(), scrimPaint)
        return out
    }
}
```

- [ ] **Step 3: Write `app/src/main/java/ca/hld/covertart/render/WallpaperRenderer.kt`**

```kotlin
package ca.hld.covertart.render

import ca.hld.covertart.core.SourceImage

/** Glues the pure planner to a platform executor. */
class WallpaperRenderer<T>(
    private val executor: WallpaperExecutor<T>,
    private val config: RenderConfig = RenderConfig(),
) {
    fun render(source: SourceImage, targetWidth: Int, targetHeight: Int): T {
        val plan = planRender(source.width, source.height, targetWidth, targetHeight, config)
        return executor.execute(plan, source)
    }
}
```

- [ ] **Step 4: Build and verify**

Run: `nix develop --command ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ca/hld/covertart/render/
git commit -m "feat: add WallpaperExecutor interface and Android canvas executor"
```

---

### Task 6: AWT executor and preview harness

**Goal:** A host-side render path — `AwtExecutor` plus procedural sample covers, a faux-Niagara overlay, a `PreviewHarness` JVM entry point, and a `runPreviews` Gradle task that writes PNGs to `app/build/previews/`.

**Files:**
- Create: `app/src/test/java/ca/hld/covertart/render/AwtExecutor.kt`
- Create: `app/src/test/java/ca/hld/covertart/preview/SampleCovers.kt`
- Create: `app/src/test/java/ca/hld/covertart/preview/FauxNiagaraOverlay.kt`
- Create: `app/src/test/java/ca/hld/covertart/preview/PreviewHarness.kt`
- Create: `app/src/test/java/ca/hld/covertart/preview/PreviewHarnessTest.kt`
- Modify: `app/build.gradle.kts` (append the `runPreviews` task)

**Acceptance Criteria:**
- [ ] `AwtExecutor` produces a `BufferedImage` matching `plan.targetSize`, drawing art and scrim equivalently to the Android executor.
- [ ] `SampleCovers.all` provides five covers: `dark`, `bright`, `pale`, `busy`, `contrast-edge`.
- [ ] `PreviewHarness.run` writes one PNG per sample cover with the faux-Niagara overlay composited on top.
- [ ] `./gradlew runPreviews` writes PNGs to `app/build/previews/`.

**Verify:** `nix develop --command ./gradlew runPreviews && ls app/build/previews/` → lists `dark.png bright.png pale.png busy.png contrast-edge.png`.

**Steps:**

- [ ] **Step 1: Write `app/src/test/java/ca/hld/covertart/render/AwtExecutor.kt`**

```kotlin
package ca.hld.covertart.render

import ca.hld.covertart.core.SourceImage
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/** A SourceImage backed by a java.awt BufferedImage (host/preview path). */
class AwtSourceImage(val image: BufferedImage) : SourceImage {
    override val width: Int get() = image.width
    override val height: Int get() = image.height
}

/** Host-path executor: java.awt Graphics2D + GradientPaint. */
class AwtExecutor : WallpaperExecutor<BufferedImage> {

    override fun execute(plan: RenderPlan, source: SourceImage): BufferedImage {
        val src = (source as AwtSourceImage).image
        val out = BufferedImage(plan.targetSize.width, plan.targetSize.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

        // drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer)
        g.drawImage(
            src,
            plan.dstRect.left, plan.dstRect.top, plan.dstRect.right, plan.dstRect.bottom,
            plan.srcRect.left, plan.srcRect.top, plan.srcRect.right, plan.srcRect.bottom,
            null,
        )

        val scrimEndX = plan.scrimRect.right.toFloat()
        val start = Color(0f, 0f, 0f, plan.scrimStartAlpha)
        val end = Color(0f, 0f, 0f, 0f)
        g.paint = GradientPaint(0f, 0f, start, scrimEndX, 0f, end)
        g.fillRect(0, 0, plan.scrimRect.right, plan.scrimRect.bottom)
        g.dispose()
        return out
    }
}
```

- [ ] **Step 2: Write `app/src/test/java/ca/hld/covertart/preview/SampleCovers.kt`**

```kotlin
package ca.hld.covertart.preview

import java.awt.Color
import java.awt.Graphics2D
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.util.Random

/**
 * Procedurally generated, license-clean sample covers chosen to stress
 * left-edge text legibility: dark, bright, pale, busy, high-contrast-edge.
 * Deterministic — these double as render-pipeline test fixtures.
 */
object SampleCovers {

    private const val SIZE = 1000

    val all: Map<String, BufferedImage> by lazy {
        mapOf(
            "dark" to gradient(Color(18, 18, 24), Color(40, 30, 60)),
            "bright" to gradient(Color(255, 220, 80), Color(255, 140, 0)),
            "pale" to gradient(Color(245, 245, 240), Color(220, 225, 235)),
            "busy" to busy(),
            "contrast-edge" to contrastEdge(),
        )
    }

    private fun newCanvas(): Pair<BufferedImage, Graphics2D> {
        val img = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        return img to img.createGraphics()
    }

    private fun gradient(a: Color, b: Color): BufferedImage {
        val (img, g) = newCanvas()
        g.paint = GradientPaint(0f, 0f, a, SIZE.toFloat(), SIZE.toFloat(), b)
        g.fillRect(0, 0, SIZE, SIZE)
        g.dispose()
        return img
    }

    private fun busy(): BufferedImage {
        val (img, g) = newCanvas()
        val rnd = Random(42)
        repeat(400) {
            g.color = Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            g.fillRect(rnd.nextInt(SIZE), rnd.nextInt(SIZE), 30 + rnd.nextInt(120), 30 + rnd.nextInt(120))
        }
        g.dispose()
        return img
    }

    private fun contrastEdge(): BufferedImage {
        val (img, g) = newCanvas()
        g.color = Color.WHITE
        g.fillRect(0, 0, SIZE, SIZE)
        g.color = Color.BLACK
        // Hard dark band exactly where Niagara's text sits.
        g.fillRect(0, 0, SIZE / 3, SIZE)
        g.dispose()
        return img
    }
}
```

- [ ] **Step 3: Write `app/src/test/java/ca/hld/covertart/preview/FauxNiagaraOverlay.kt`**

```kotlin
package ca.hld.covertart.preview

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Composites a left-pinned clock + app names onto a rendered wallpaper,
 * approximating the Niagara launcher so previews show text in context.
 * Mutates and returns the given image.
 */
object FauxNiagaraOverlay {

    fun compositeOnto(wallpaper: BufferedImage): BufferedImage {
        val g = wallpaper.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.WHITE
        val pad = 48

        g.font = Font(Font.SANS_SERIF, Font.BOLD, 120)
        g.drawString("9:41", pad, 220)

        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 44)
        val apps = listOf("Phone", "Messages", "Tidal", "Spotify", "Settings", "Camera")
        apps.forEachIndexed { i, name -> g.drawString(name, pad, 360 + i * 70) }

        g.dispose()
        return wallpaper
    }
}
```

- [ ] **Step 4: Write `app/src/test/java/ca/hld/covertart/preview/PreviewHarness.kt`**

```kotlin
package ca.hld.covertart.preview

import ca.hld.covertart.render.AwtExecutor
import ca.hld.covertart.render.AwtSourceImage
import ca.hld.covertart.render.RenderConfig
import ca.hld.covertart.render.WallpaperRenderer
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders every sample cover through the real pipeline + AWT executor,
 * composites the faux-Niagara overlay, and writes one PNG per cover.
 */
object PreviewHarness {

    const val TARGET_WIDTH = 1080
    const val TARGET_HEIGHT = 1920

    fun run(outputDir: File, config: RenderConfig = RenderConfig()): List<File> {
        outputDir.mkdirs()
        val renderer = WallpaperRenderer(AwtExecutor(), config)
        return SampleCovers.all.map { (name, cover) ->
            val wallpaper = renderer.render(AwtSourceImage(cover), TARGET_WIDTH, TARGET_HEIGHT)
            FauxNiagaraOverlay.compositeOnto(wallpaper)
            val out = File(outputDir, "$name.png")
            ImageIO.write(wallpaper, "png", out)
            out
        }
    }
}
```

- [ ] **Step 5: Write `app/src/test/java/ca/hld/covertart/preview/PreviewHarnessTest.kt`**

```kotlin
package ca.hld.covertart.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PreviewHarnessTest {

    @Test
    fun rendersOnePreviewPerSampleCover() {
        val outputDir = File(System.getProperty("previews.outputDir") ?: "build/previews")
        val written = PreviewHarness.run(outputDir)
        assertEquals(SampleCovers.all.size, written.size)
        written.forEach { assertTrue("preview not written: $it", it.isFile && it.length() > 0) }
    }
}
```

- [ ] **Step 6: Append the `runPreviews` task to `app/build.gradle.kts`**

Add at the end of the file, after the `dependencies { }` block:

```kotlin
// Renders the preview harness independently of the full unit-test suite.
// Reuses the debug unit-test classpath so AWT + test-source classes are available.
tasks.register<Test>("runPreviews") {
    description = "Renders sample wallpapers to build/previews/ via the AWT executor."
    group = "verification"
    val debugUnitTest = tasks.named<Test>("testDebugUnitTest").get()
    testClassesDirs = debugUnitTest.testClassesDirs
    classpath = debugUnitTest.classpath
    useJUnit { includeTestsMatching("*PreviewHarnessTest") }
    systemProperty(
        "previews.outputDir",
        layout.buildDirectory.dir("previews").get().asFile.absolutePath,
    )
}
```

- [ ] **Step 7: Verify**

Run: `nix develop --command ./gradlew runPreviews`
Expected: `BUILD SUCCESSFUL`.
Run: `ls app/build/previews/`
Expected: `bright.png  busy.png  contrast-edge.png  dark.png  pale.png`.

- [ ] **Step 8: Commit**

```bash
git add app/src/test/java/ca/hld/covertart/render/AwtExecutor.kt app/src/test/java/ca/hld/covertart/preview/ app/build.gradle.kts
git commit -m "feat: add AWT executor and host-side preview harness"
```

---

### Task 7: AppState (DataStore)

**Goal:** DataStore-backed persistent state — master on/off toggle, last-applied track string, current status string — exposed as Flows with suspend setters.

**Files:**
- Create: `app/src/main/java/ca/hld/covertart/data/AppState.kt`

**Acceptance Criteria:**
- [ ] `AppState` exposes `masterEnabled: Flow<Boolean>` (default `true`), `lastTrack: Flow<String>` (default `""`), `status: Flow<String>` (default `"Not started"`).
- [ ] Suspend setters `setMasterEnabled`, `setLastTrack`, `setStatus` persist to a single preferences DataStore named `covertart`.
- [ ] The module compiles: `./gradlew assembleDebug` succeeds.

**Verify:** `nix develop --command ./gradlew assembleDebug` → `BUILD SUCCESSFUL`.

**Steps:**

- [ ] **Step 1: Write `app/src/main/java/ca/hld/covertart/data/AppState.kt`**

```kotlin
package ca.hld.covertart.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "covertart")

/**
 * DataStore-backed persistent app state: master toggle, last-applied track,
 * and the current status string shown on the MainActivity screen.
 */
class AppState(private val context: Context) {

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val LAST_TRACK = stringPreferencesKey("last_track")
        val STATUS = stringPreferencesKey("status")
    }

    val masterEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.MASTER_ENABLED] ?: true }

    val lastTrack: Flow<String> =
        context.dataStore.data.map { it[Keys.LAST_TRACK] ?: "" }

    val status: Flow<String> =
        context.dataStore.data.map { it[Keys.STATUS] ?: "Not started" }

    suspend fun setMasterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MASTER_ENABLED] = enabled }
    }

    suspend fun setLastTrack(track: String) {
        context.dataStore.edit { it[Keys.LAST_TRACK] = track }
    }

    suspend fun setStatus(status: String) {
        context.dataStore.edit { it[Keys.STATUS] = status }
    }
}
```

- [ ] **Step 2: Build and verify**

Run: `nix develop --command ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ca/hld/covertart/data/AppState.kt
git commit -m "feat: add DataStore-backed AppState"
```

---

### Task 8: MediaWatcherService, WallpaperApplier, and wiring

**Goal:** The long-lived `NotificationListenerService` that watches media sessions, runs the full pipeline (resolve → gate → render → apply → persist), and the `WallpaperApplier` + `screenSize` helper it depends on.

**Files:**
- Create: `app/src/main/java/ca/hld/covertart/device/WallpaperApplier.kt`
- Create: `app/src/main/java/ca/hld/covertart/device/ScreenSize.kt`
- Create: `app/src/main/java/ca/hld/covertart/service/MediaWatcherService.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add `SET_WALLPAPER` permission and the `<service>`)

**Acceptance Criteria:**
- [ ] `WallpaperApplier.apply` calls `WallpaperManager.setBitmap` with `FLAG_SYSTEM or FLAG_LOCK` in one call.
- [ ] `MediaWatcherService` registers an `OnActiveSessionsChangedListener` and a per-controller `MediaController.Callback`, and re-evaluates on `onListenerConnected`.
- [ ] Each event goes through a trailing-debounce coroutine, then `TrackResolver` → PLAYING check → `ChangeGate` → `WallpaperRenderer` → `WallpaperApplier` → `AppState`.
- [ ] Pause/stop produces no apply; missing art skips the apply and updates the status string; `setBitmap` exceptions are caught and surfaced in the status string.
- [ ] The manifest declares the service with `BIND_NOTIFICATION_LISTENER_SERVICE` and the `NotificationListenerService` intent-filter.
- [ ] `./gradlew assembleDebug` succeeds.

**Verify:** `nix develop --command ./gradlew assembleDebug` → `BUILD SUCCESSFUL`; `grep BIND_NOTIFICATION_LISTENER_SERVICE app/src/main/AndroidManifest.xml` matches.

**Steps:**

- [ ] **Step 1: Write `app/src/main/java/ca/hld/covertart/device/WallpaperApplier.kt`**

```kotlin
package ca.hld.covertart.device

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap

/** Sets a bitmap as both the home and lock screen wallpaper in one call. */
class WallpaperApplier(context: Context) {

    private val wallpaperManager = WallpaperManager.getInstance(context)

    /** @throws java.io.IOException if the system rejects the bitmap. */
    fun apply(bitmap: Bitmap) {
        wallpaperManager.setBitmap(
            bitmap,
            /* visibleCropHint = */ null,
            /* allowBackup = */ true,
            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
        )
    }
}
```

- [ ] **Step 2: Write `app/src/main/java/ca/hld/covertart/device/ScreenSize.kt`**

```kotlin
package ca.hld.covertart.device

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/** Real display size in pixels as (width, height) — the wallpaper target size. */
fun screenSize(context: Context): Pair<Int, Int> {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = wm.currentWindowMetrics.bounds
        bounds.width() to bounds.height()
    } else {
        @Suppress("DEPRECATION")
        val metrics = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
        metrics.widthPixels to metrics.heightPixels
    }
}
```

- [ ] **Step 3: Write `app/src/main/java/ca/hld/covertart/service/MediaWatcherService.kt`**

```kotlin
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
```

- [ ] **Step 4: Modify `app/src/main/AndroidManifest.xml`**

Add the permission as the first child of `<manifest>` (before `<application>`):

```xml
    <uses-permission android:name="android.permission.SET_WALLPAPER" />
```

Add the service inside `<application>`, after the `<activity>` block:

```xml
        <service
            android:name=".service.MediaWatcherService"
            android:exported="true"
            android:label="Covert Art Wallpaper"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>
```

- [ ] **Step 5: Build and verify**

Run: `nix develop --command ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.
Run: `grep -c BIND_NOTIFICATION_LISTENER_SERVICE app/src/main/AndroidManifest.xml`
Expected: `1`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ca/hld/covertart/device/ app/src/main/java/ca/hld/covertart/service/ app/src/main/AndroidManifest.xml
git commit -m "feat: add MediaWatcherService wiring the full pipeline"
```

---

### Task 9: MainActivity status screen

**Goal:** Replace the placeholder `MainActivity` with the real Compose status screen — grant-access button, master toggle, status line — observing `AppState`.

**Files:**
- Modify: `app/src/main/java/ca/hld/covertart/MainActivity.kt` (full replacement)

**Acceptance Criteria:**
- [ ] A button opens the system notification-access settings via `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
- [ ] A `Switch` reflects and updates `AppState.masterEnabled`.
- [ ] A status line shows `AppState.status`, observed with `collectAsStateWithLifecycle`.
- [ ] `./gradlew assembleDebug` succeeds.

**Verify:** `nix develop --command ./gradlew assembleDebug` → `BUILD SUCCESSFUL`.

**Steps:**

- [ ] **Step 1: Replace `app/src/main/java/ca/hld/covertart/MainActivity.kt` entirely**

```kotlin
package ca.hld.covertart

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import ca.hld.covertart.data.AppState
import kotlinx.coroutines.launch

/** Minimal status screen — no style configuration, just grant + toggle + status. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appState = AppState(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StatusScreen(
                        appState = appState,
                        onGrantAccess = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onToggleMaster = { enabled ->
                            lifecycleScope.launch { appState.setMasterEnabled(enabled) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusScreen(
    appState: AppState,
    onGrantAccess: () -> Unit,
    onToggleMaster: (Boolean) -> Unit,
) {
    val masterEnabled by appState.masterEnabled.collectAsStateWithLifecycle(initialValue = true)
    val status by appState.status.collectAsStateWithLifecycle(initialValue = "Not started")

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Covert Art Wallpaper", style = MaterialTheme.typography.headlineMedium)

        Button(onClick = onGrantAccess, modifier = Modifier.fillMaxWidth()) {
            Text("Grant notification access")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = masterEnabled, onCheckedChange = onToggleMaster)
            Spacer(Modifier.width(12.dp))
            Text(if (masterEnabled) "Wallpaper updates on" else "Wallpaper updates off")
        }

        HorizontalDivider()
        Text(status, style = MaterialTheme.typography.bodyLarge)
    }
}
```

- [ ] **Step 2: Build and verify**

Run: `nix develop --command ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/ca/hld/covertart/MainActivity.kt
git commit -m "feat: add Compose status screen"
```

---

### Task 10: Release signing and GitHub Actions CI

**Goal:** Wire conditional release signing from Gradle properties, add the CI workflow (test → previews → signed release → GitHub Release), and document the one-time keystore + Obtainium setup.

**Files:**
- Modify: `app/build.gradle.kts` (add `signingConfigs` and wire `release`)
- Create: `.github/workflows/build.yml`
- Modify: `README.md`

**Acceptance Criteria:**
- [ ] When the signing Gradle properties are present, `assembleRelease` produces a signed APK; when absent (e.g. local dev), the build still configures without error.
- [ ] CI runs on push to `main` and `workflow_dispatch`: checkout, JDK 17, Android SDK, `./gradlew test`, `./gradlew runPreviews` + artifact upload, signed `assembleRelease`, GitHub Release tagged `v1.0.<run_number>`.
- [ ] `versionCode` is the run number; `versionName` is `1.0.<run_number>`.
- [ ] README documents keystore generation, the required secrets, and the Obtainium setup.

**Verify:** Generate a throwaway keystore and run `nix develop --command ./gradlew assembleRelease -PversionCode=7 -PsigningKeystore=$PWD/ci-test.keystore -PsigningKeyAlias=covertart -PsigningKeyPassword=testpass -PsigningStorePassword=testpass` → `BUILD SUCCESSFUL`, signed APK at `app/build/outputs/apk/release/app-release.apk`.

**Steps:**

- [ ] **Step 1: Add signing to `app/build.gradle.kts`**

Inside the `android { }` block, add a `signingConfigs { }` block immediately before `buildTypes { }`:

```kotlin
    // Release signing is supplied by CI via -P properties; absent locally.
    val signingKeystore = project.findProperty("signingKeystore") as String?
    signingConfigs {
        if (signingKeystore != null) {
            create("release") {
                storeFile = file(signingKeystore)
                storePassword = project.findProperty("signingStorePassword") as String?
                keyAlias = project.findProperty("signingKeyAlias") as String?
                keyPassword = project.findProperty("signingKeyPassword") as String?
            }
        }
    }
```

Then change the `release { }` block inside `buildTypes { }` to wire the signing config when present:

```kotlin
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (signingKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
```

- [ ] **Step 2: Create a throwaway keystore and verify the signed release build**

Run:
```bash
nix develop --command keytool -genkeypair -v \
  -keystore ci-test.keystore -alias covertart \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass testpass -keypass testpass \
  -dname "CN=Covert Art Wallpaper CI Test"
nix develop --command ./gradlew assembleRelease \
  -PversionCode=7 \
  -PsigningKeystore=$PWD/ci-test.keystore \
  -PsigningKeyAlias=covertart \
  -PsigningKeyPassword=testpass \
  -PsigningStorePassword=testpass
```
Expected: `BUILD SUCCESSFUL`; `app/build/outputs/apk/release/app-release.apk` exists.
Then remove the throwaway keystore: `rm ci-test.keystore` (it must never be committed).

- [ ] **Step 3: Create `.github/workflows/build.yml`**

```yaml
name: Build

on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Unit tests
        run: ./gradlew test

      - name: Render preview harness
        run: ./gradlew runPreviews

      - name: Upload previews
        uses: actions/upload-artifact@v4
        with:
          name: previews
          path: app/build/previews/

      - name: Decode signing keystore
        env:
          SIGNING_KEYSTORE: ${{ secrets.SIGNING_KEYSTORE }}
        run: echo "$SIGNING_KEYSTORE" | base64 -d > "$RUNNER_TEMP/release.keystore"

      - name: Assemble signed release
        env:
          SIGNING_KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
          SIGNING_KEY_PASSWORD: ${{ secrets.SIGNING_KEY_PASSWORD }}
          SIGNING_STORE_PASSWORD: ${{ secrets.SIGNING_STORE_PASSWORD }}
        run: |
          ./gradlew assembleRelease \
            -PversionCode=${{ github.run_number }} \
            -PsigningKeystore="$RUNNER_TEMP/release.keystore" \
            -PsigningKeyAlias="$SIGNING_KEY_ALIAS" \
            -PsigningKeyPassword="$SIGNING_KEY_PASSWORD" \
            -PsigningStorePassword="$SIGNING_STORE_PASSWORD"

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: v1.0.${{ github.run_number }}
          files: app/build/outputs/apk/release/app-release.apk
```

- [ ] **Step 4: Replace `README.md`**

```markdown
# Covert Art Wallpaper

An Android app that watches whatever music app is playing and sets the device
wallpaper (home + lock) to the current track's cover art, rendered for a tall
portrait screen. Built for a FiiO M33, distributed via Obtainium.

See `docs/superpowers/specs/2026-05-14-covert-art-wallpaper-design.md` for the
design.

## Development

```sh
direnv allow        # or: nix develop
./gradlew test          # unit tests
./gradlew runPreviews   # render sample wallpapers to app/build/previews/
./gradlew assembleDebug
```

## Release signing (one-time)

Generate a release keystore locally — it is never committed:

```sh
keytool -genkeypair -v \
  -keystore release.keystore -alias covertart \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Covert Art Wallpaper"
```

Add these GitHub Actions repository secrets:

| Secret                   | Value                                              |
|--------------------------|----------------------------------------------------|
| `SIGNING_KEYSTORE`       | `base64 -w0 release.keystore`                      |
| `SIGNING_KEY_ALIAS`      | the alias used above (`covertart`)                 |
| `SIGNING_KEY_PASSWORD`   | the key password                                   |
| `SIGNING_STORE_PASSWORD` | the keystore password                              |

```sh
gh secret set SIGNING_KEYSTORE       < <(base64 -w0 release.keystore)
gh secret set SIGNING_KEY_ALIAS      --body "covertart"
gh secret set SIGNING_KEY_PASSWORD   --body "<key-password>"
gh secret set SIGNING_STORE_PASSWORD --body "<store-password>"
```

## Distribution

CI builds a signed APK on every push to `main` and publishes a GitHub Release
tagged `v1.0.<run_number>`. Add the repository URL to Obtainium once; it will
offer each higher-`versionCode` build as an update.
```

- [ ] **Step 5: Verify the workflow file is valid YAML**

Run: `nix develop --command bash -c 'python3 -c "import yaml,sys; yaml.safe_load(open(\".github/workflows/build.yml\"))" && echo OK'`
Expected: `OK`.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts .github/workflows/build.yml README.md
git commit -m "build: add release signing, CI workflow, and setup docs"
```

---

## Self-Review

**Spec coverage:**
- Architecture components (§1): `MediaWatcherService` (T8), `TrackResolver` (T2), `ChangeGate` (T3), `WallpaperRenderer`/`RenderPlan`/`WallpaperExecutor` (T4–T6), `WallpaperApplier` (T8), `AppState` (T7), `MainActivity` (T9) — all covered.
- Data flow (§2): wired in `MediaWatcherService.evaluate` (T8), including `onListenerConnected` re-evaluation.
- "G" rendering pipeline (§3): `planRender` geometry (T4), `AndroidCanvasExecutor`/`AwtExecutor` (T5–T6), left scrim, no-art skip (T8).
- Build & distribution (§4): Nix flake (T0), Gradle scaffold + version catalog (T1), `versionCode`/`versionName` from run number (T1), signing + CI + per-build release tag (T10).
- Testing (§5): `TrackResolver` (T2), `RenderPlan` geometry (T4), `ChangeGate` (T3); sample covers double as fixtures (T6).
- Error handling (§6): no notification access — grant button (T9); master off — `ChangeGate` (T3); no session / no art / `setBitmap` throws / reboot — `evaluate` (T8); duplicate callbacks — `ChangeGate` + trailing debounce (T3/T8); bitmap recycle (T8).
- Preview harness (§7): `PreviewHarness` + sample covers + faux-Niagara overlay + `runPreviews` task + CI artifact (T6, T10).
- Open items: scrim values → `RenderConfig` defaults (T4); flake `androidenv` + unfree licence (T0); sample covers → procedural, license-clean (T6); release strategy → per-build `v1.0.<run_number>` tag (T10).

**Note on TrackResolver vs. PLAYING gate:** the spec says `TrackResolver` "prefers PLAYING". `TrackResolver.resolve` (T2) does the preference and falls back to most-recent when nothing plays; the "pause/stop produces no action" rule is enforced one level up in `MediaWatcherService.evaluate` (T8), which returns early unless the resolved track's `playbackState == PLAYING`. This keeps `TrackResolver` pure and matches §2 / §6 behaviour.

**Placeholder scan:** no TBD/TODO/"add error handling" placeholders — every code step contains complete code.

**Type consistency:** `SourceImage(width,height)`, `NowPlaying(artist,title,album,art,playbackState,identityKey)`, `MetadataView.string/bitmap`, `SessionSnapshot(metadata,playbackState,lastActiveAtMillis)`, `TrackResolver.resolve/pickSession`, `ChangeGate(debounceMillis).shouldApply(nowPlaying,masterEnabled,nowMillis)/markApplied(nowPlaying,nowMillis)`, `IntRect(left,top,right,bottom)`, `RenderConfig(scrimStartAlpha,scrimEndFraction)`, `RenderPlan(...)`, `planRender(srcWidth,srcHeight,targetWidth,targetHeight,config)`, `WallpaperExecutor<T>.execute(plan,source)`, `AndroidSourceImage(bitmap)`/`AwtSourceImage(image)`, `WallpaperRenderer<T>(executor,config).render(source,targetWidth,targetHeight)`, `AppState(context)`, `WallpaperApplier(context).apply(bitmap)`, `screenSize(context)` — consistent across all tasks.
