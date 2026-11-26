# Kodra release and signing — Stage 6 scaffold

Target: GitHub Releases **only** (no Play Store), `targetSdk 28` per owner decision.

## Signing

- Keystore lives **outside Git** at `~/.keystore/kodra-release.jks` (or env `KODRA_KEYSTORE`).
- `kodra/android/app/build.gradle.kts` release `signingConfig` is **commented out** until `local.properties` provides:
  ```
  kodra.keystore.path=/home/user/.keystore/kodra-release.jks
  kodra.keystore.password=...
  kodra.keyAlias=kodra
  kodra.keyPassword=...
  ```
- Debug APK is unsigned and sufficient for CI (`assembleDebug` is the Stage 3 gate).

## Release workflow (`.github/workflows/kodra-release.yml` — next commit)

```yaml
on: push tags: v*.*
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4  # 17
      - uses: android-actions/setup-android@v3
      - run: ./kodra/android/gradlew :app:assembleRelease
        env:
          KODRA_KEYSTORE_BASE64: ${{ secrets.KODRA_KEYSTORE_BASE64 }}
          KODRA_KEYSTORE_PASSWORD: ${{ secrets.KODRA_KEYSTORE_PASSWORD }}
      - uses: softprops/action-gh-release@v1
        with: { files: kodra/android/app/build/outputs/apk/release/*.apk }
```

## Device verification checklist (Stage 6 — must be evidenced, not claimed)

- [ ] `$PREFIX/bin/node --version == 24.22.1` under app UID
- [ ] `$PREFIX/bin/bash -c 'echo ok'` + `git --version`
- [ ] `node-pty` fork+read smoke (see `kodra/TOOLCHAIN.md`)
- [ ] Foreground service survives tap-home → reopen (no restart)
- [ ] Notification Stop kills server + PTY tree, removes wake lock
- [ ] SAF Open File/Folder → real POSIX path for filesystem-backed provider, error for content-only
- [ ] Git clone/fetch/push via `github` auth + credential helper (no token in logcat)
- [ ] Long-press context menu, pinch zoom (opt-in), IME composition, extra-keys row
- [ ] Workbench settings round-trip (`docs/sample-settings.json` unchanged)
- [ ] Battery optimization exemption dialog shown, phantom-process doc linked

## Phantom-process / battery notes (to ship in `docs/BATTERY.md`)

Android 12+ may kill cached processes (`phantom process` limit). Document:
- Request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` → Settings → Don't optimize Kodra
- Long-running `node-pty` builds may be killed on some OEMs — advise user-visible notification
  that process was reaped and offer restart.
