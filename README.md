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

## Release signing

The release keystore is committed at `release.keystore` and the alias and
passwords are hardcoded in `app/build.gradle.kts`. This is deliberate: the app
is distributed only via Obtainium from this public repo, so a consistent
signing key just needs to be reproducible across builds, not secret. The key
is project-specific (it signs only `ca.hld.covertart`) and shares nothing with
any other keystore.

The practical trade-off: anyone could sign a build as `ca.hld.covertart`. Only
install builds from this repo's GitHub Releases.

## Distribution

CI builds a signed APK on every push to `main` and publishes a GitHub Release
tagged `v1.0.<run_number>`. Add the repository URL to Obtainium once; it will
offer each higher-`versionCode` build as an update.
