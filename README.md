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
