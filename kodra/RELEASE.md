# Kodra release and signing

Target: GitHub Releases **only** (no Play Store), `targetSdk 28` per owner decision.

## Signing

- Keystore lives **outside Git** at `~/.keystore/kodra-release.jks` (or env `KODRA_KEYSTORE`).
- `kodra/android/app/build.gradle.kts` release `signingConfig` reads from `local.properties` (see `local.properties.example`):
  ```
  kodra.keystore.path=/home/user/.keystore/kodra-release.jks
  kodra.keystore.password=...
  kodra.keyAlias=kodra
  kodra.keyPassword=...
  ```
- Debug APK is unsigned and sufficient for CI (`assembleDebug` is the gate). Release APK is signed in CI via secrets (see `.github/workflows/kodra-release.yml`).

## Release workflow

`.github/workflows/kodra-release.yml` triggers on `v*.*.*` tags:

1. Checkout + Java 17 + Android SDK + Gradle 8.7 wrapper generation
2. Restore keystore from `secrets.KODRA_KEYSTORE_BASE64` to `~/.keystore/kodra-release.jks`
3. `gradlew :app:assembleRelease` (uses `KODRA_KEYSTORE_PASSWORD`, `KODRA_KEY_ALIAS`, `KODRA_KEY_PASSWORD`)
4. Upload artifact `kodra-release-apk` + `softprops/action-gh-release` creates GitHub Release

## Device verification checklist (must be evidenced on device, not claimed)

- [ ] `$PREFIX/bin/node --version == 24.22.1` under app UID
- [ ] `$PREFIX/bin/bash -c 'echo ok'` + `git --version`
- [ ] `node-pty` fork+read smoke (see `kodra/TOOLCHAIN.md` `node-pty` termux-pty shim)
- [ ] Foreground service survives tap-home → reopen (no restart)
- [ ] Notification Stop kills server + PTY tree, removes wake lock
- [ ] SAF Open File/Folder → real POSIX path for filesystem-backed provider, error for content-only
- [ ] Git clone/fetch/push via `github` auth + credential helper (no token in logcat)
- [ ] Long-press context menu, pinch zoom (opt-in), IME composition, extra-keys row, hardware shortcuts
- [ ] Workbench settings round-trip (`docs/sample-settings.json` unchanged)
- [ ] Battery optimization exemption dialog shown, phantom-process doc linked (see `docs/BATTERY.md`)
- [ ] No Microsoft endpoint contacted (verified by `kodra-reh.yml` host inventory + device network trace)
