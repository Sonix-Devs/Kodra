# Kodra user guide

## Installation

1. Download `kodra-v0.3.0.apk` from [GitHub Releases](https://github.com/unalivin/kodra/releases) (not Play Store, `targetSdk 28`).
2. `adb install kodra-v0.3.0.apk` or sideload via file manager.

## First launch

1. Grant **All files access** when prompted (for `/storage/emulated/0/Kodra`).
2. Wait ~5s for bootstrap extraction (`assets/bootstrap.zip` → `$PREFIX`, `reh-web.tar.gz` → REH). Progress shown in notification.
3. WebView loads workbench at `http://127.0.0.1:PORT/?tkn=...` — token is per-launch, never in Logcat.

## Workbench

- Same as desktop: Activity Bar, Sidebar, Editor, Panel, Command Palette (`F1`), Settings (`Ctrl+,`).
- Open File/Folder uses **system picker** → real POSIX path (content-only URIs show error, copy to `/sdcard/Kodra` first).
- Save As / Git Clone uses **VS Code simple dialog** starting in `/storage/emulated/0/Kodra`.
- Terminal: `bash` is default shell (`$PREFIX/bin/bash`, `TERM=xterm-256color`). Extra-keys row above keyboard: Esc/Tab/Ctrl (sticky)/Alt/arrows/Home/End/PgUp/PgDn.

## Git & GitHub

- Bootstrap `git` is at `$PREFIX/bin/git` — built-in Git extension uses it (graph/timeline intact).
- Tap **Source Control** → **Sign in with GitHub** → Device Flow: shows `user_code` + opens `github.com/login/device` → enter code → token stored in `AndroidKeyStore` AES-GCM.
- Push/fetch via HTTPS uses credential helper `kodra-credential-helper` (reads Keystore, zero Logcat).
- SSH via `openssh` (`$PREFIX/bin/ssh`) — add keys to `$HOME/.ssh`.

## Settings

- `docs/sample-settings.json` → copy to `~/.kodra/user-data/User/settings.json` or edit via UI (Settings ↔ JSON round-trip verified, even for not-installed extensions).
- Telemetry `off`, update mode `none` — GitHub Releases is the channel.

## Troubleshooting

- **Notification disappeared:** Service killed by OEM — reopen Kodra, disable battery optimization (Settings → Apps → Kodra → Battery → Unrestricted, pin in Recents, see `docs/BATTERY.md`).
- **Bootstrap missing:** `logcat -s KodraService` shows `BootstrapMissingException` — reinstall APK (assets corrupted).
- **SAF content-only error:** Choose a file from **Device Storage / Kodra** or copy it there first.

See `PORTING_NOTES.md` for audit, `kodra/RELEASE.md` for signing, `docs/EXTENSIONS.md` for bundled extensions.
