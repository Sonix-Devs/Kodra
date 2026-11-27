# Kodra battery & process survival

Kodra runs a local Node server + PTY tree in a foreground service with a partial
`WakeLock` and a low-importance persistent notification (owner requirement: must
have a visible **Stop** action). This doc explains what the OS may still do and
what the user must do.

## What Kodra guarantees

- Service is `START_STICKY`; if the system kills the process, it restarts and
  `KodraService.onCreate()` will restart the Node server with a **new** token.
  The Activity will reconnect to the new `127.0.0.1:<port>`.
- Notification `Stop Kodra` kills the server, PTY tree, `WakeLock`, and
  `stopSelf()` — no orphaned `node` process.
- `TokenManager` rotates the token per launch; old `?tkn=` URLs are 403 after restart.

## What Kodra cannot guarantee

**Android 12+ phantom-process limit** (`ActivityManager` `max_phantom_processes`):
the system may kill cached child processes (`node` via `ProcessBuilder`) after
the service is cached. Kodra cannot override OEM-specific `phantom` or `cached-apps-freezer`.

**Doze / App Standby / Battery optimization**:
on some OEMs (Xiaomi, OnePlus, Samsung, Huawei) the foreground notification is
not enough; the OEM task manager may force-stop the app even with `FOREGROUND_SERVICE`.

**Documented user steps (verify on device before claiming support):**

1. **Disable battery optimization for Kodra**
   Settings → Apps → Kodra → Battery → Unrestricted *(or* Settings → Battery →
   Background restriction → Don't optimize Kodra *— path varies by OEM).*

   Kodra also requests `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` → system dialog
   “Kodra wants to run in background without restrictions” → **Allow**.

2. **Pin / lock in recents**
   Open Recents → long-press Kodra card → **Lock** (Xiaomi/Oppo) or **Pin**.

3. **Autostart permission** (MIUI/ColorOS)
   Settings → Apps → Autostart → enable Kodra (only if you want auto-restart after reboot).

## How to tell if Kodra was killed

- Notification disappears → service was killed; reopen Kodra → new server starts.
- `adb logcat -s KodraService` shows `Server failed: …` or `BootstrapMissingException`.
- Workbench shows `Disconnected` overlay → Activity is polling `KodraService.bound()` every 800 ms for 30 s; after 30 s it shows `Reconnect` button which calls `KodraService.start()` again.

## Developer notes

- Do not acquire a full `WakeLock` or `WifiLock` — drains battery and is rejected in
  Play Review (not relevant since we ship via GitHub Releases, but still wasteful).
- `WakeLock` is reference-counted `false`, held for 10 h max then re-acquired on
  next Activity bind (so a truly idle Kodra eventually releases after 10 h).
- Test on Firebase Test Lab / local `emulator -avd` with `adb shell dumpsys activity processes` + `adb shell cmd appops get dev.sonixdevs.kodra RUN_IN_BACKGROUND`.

## Target/compile notes

`targetSdk 28` (owner choice) avoids Play Store scoped-storage enforcement but
still supports `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` on Android 9+. `compileSdk 34`
is needed for `WindowInsetsControllerCompat` transient-bars-by-swipe and
`WebView` modern APIs — target remains 28 per `PORTING_NOTES.md` § Target.
