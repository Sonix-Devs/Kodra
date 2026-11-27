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

If `KODRA_KEYSTORE_BASE64` is not set, the job still builds `assembleRelease` as **unsigned** and uploads it as an artifact — the GitHub Release step is skipped. The APK is debuggable via `adb install` but not distributable as a signed release.

## Device verification checklist — step-by-step for tablet + phone

Do each step on both devices. Check the box only after you see the expected result.
All items are **unverified** until you report back with what you saw.

### Bootstrap + Node

1. Install APK: `adb install kodra-debug.apk` (or sideload via file manager).
2. Launch Kodra → grant **All files access** when prompted → tap **Allow**.
   - Expected: workbench loads within ~5s (bootstrap extraction is silent; check `adb logcat -s KodraService` for `Bootstrap not installed` if it fails).
3. Open Terminal panel → type `node --version` → must print `v24.22.1`.
4. Type `bash -c 'echo ok'` → must print `ok`.

### Service + notification

5. Tap Home → reopen Kodra from launcher.
   - Expected: same workbench session, no restart. Notification still shows `Kodra is running 127.0.0.1:PORT`.
6. Pull down notification shade → tap **Stop**.
   - Expected: notification disappears, `adb shell ps -A | grep kodra` shows no `node` process.

### File picker (SAF)

7. File → Open File → system picker appears → choose any file under **Kodra** folder on device storage.
   - Expected: file opens in editor, path is real POSIX (`/storage/emulated/0/Kodra/...`).
8. Try opening a file from a content-only provider (e.g. Google Drive if installed).
   - Expected: error toast `This file provider does not expose a filesystem path...` — not a silent wrong path.

### Git + GitHub

9. Open Source Control → **Sign in with GitHub** → Device Flow: note the `user_code` shown, browser opens `github.com/login/device` → enter code → authorize.
   - Expected: `GithubAuth.loadToken()` returns a token, `git push` via HTTPS works (credential helper prints `password=<token>`).
10. Clone a test repo via Command Palette → `Git: Clone` → paste HTTPS URL → choose folder via simple dialog.
    - Expected: repo clones to `/storage/emulated/0/Kodra/<repo>`, graph/timeline appear.
11. Check Logcat: `adb logcat | grep -i token` must show `?tkn=***` (redacted), never the raw token.

### Touch, IME, extra keys

12. Long-press in editor → context menu appears.
13. Pinch-zoom (if enabled in workbench settings) → zooms editor.
14. Tap Terminal → extra-keys row shows above keyboard: Esc, Tab, Ctrl (sticky), Alt, arrows, Home/End/PgUp/PgDn → tap each, verify the key reaches the shell.
15. Hardware keyboard (if available): `Ctrl+T` opens terminal, etc.

### Settings round-trip

16. Open Settings UI → change font size → check `~/.kodra/user-data/User/settings.json` via Terminal (`cat $HOME/.kodra/user-data/User/settings.json`) → value persisted.
17. Add a key for a not-installed extension (e.g. `"discord.enabled": true`) → close/reopen Settings → key still present (not stripped).
    - Reference file: `docs/sample-settings.json` must survive unchanged through the round-trip.

### Battery + phantom-process

18. Settings → Apps → Kodra → Battery → **Unrestricted** (or the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog → **Allow**).
19. Recents → long-press Kodra card → **Lock** (Xiaomi/Oppo) / **Pin** (others).
20. See `docs/BATTERY.md` for OEM-specific notes and the Android 12+ `max_phantom_processes` limitation.

### Microsoft endpoint check

21. No Microsoft endpoint contacted — verified by `kodra-reh.yml` host inventory (`kodra-hosts.log` artifact, top 40 in job summary) + manual device network trace (`adb shell dumpsys netstats` or proxy).
