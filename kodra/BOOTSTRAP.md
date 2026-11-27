# Kodra bootstrap — Termux-style prefix

Stage 1 shipped **Linux/glibc** `linux-arm64` Node 24.22.1 + REH JS. That closure
**cannot** run on Android/bionic. Stage 2 must replace it with a bionic-built
prefix that lives under Kodra's private app storage:

```
$PREFIX = /data/data/dev.sonixdevs.kodra/files/usr
$HOME   = /data/data/dev.sonixdevs.kodra/files/home
```

Both paths are app-private (no `WRITE_EXTERNAL_STORAGE` needed) and survive
re-installs without world-readable leaks. The shared projects default
`/storage/emulated/0/Kodra` is *outside* the prefix and only accessed via SAF
bridge (see Architecture §7).

## Package set (minimum viable bootstrap)

Inherited from Termux but rebuilt for Kodra's prefix and signing key:

| pkg | why | source / notes |
|-----|-----|---------------|
| `bash` | integrated terminal default shell | termux/bash, patch `$PREFIX` |
| `coreutils` | ls/cp/mv for extension host | upstream |
| `pkg` / `apt` + `termux-keyring` | bootstrap installer itself | termux/apt |
| `git` | built-in Git extension | upstream 2.44+, credential-helper → Kodra Keystore bridge |
| `openssh` | `ssh2` host + Git over SSH | upstream, no `ssh-askpass` GUI |
| `curl` | extension asset fetch, smoke-test | upstream |
| `nodejs` | **24.22.1** exact target from `remote/package.json:nativeBuild.target` | nodejs.org dist, **rebuilt for bionic** via `android-ndk r26+` |
| `python` | `ms-python.python` extension host | 3.11+, `ensurepip` |
| `clang`, `make`, `cmake` | on-device native builds (optional, Stage 4) | NDK sysroot |
| `ripgrep` | `@vscode/ripgrep-universal` replacement | bootstrap-built `rg`, not prebuilt glibc/musl |

All packages install into `$PREFIX/{bin,lib,etc,tmp}` and must be relocatable
(`$PREFIX` is baked via `--prefix` at build time and via `termux-elf-cleaner`).

## Node / native-addon ABI

- **Do not ship** `linux-arm64` Node from `build/checksums/nodejs.txt` nor any
  `remote/node_modules/**/prebuilds` built against glibc.
- Node 24.22.1 must be built with the Android NDK sysroot (`aarch64-linux-android28`)
  targeting `targetSdk 28` as in PORTING_NOTES § *Target and distribution*.
- Native addons that require rebuild (from `build/kodra/inventoryNativeDeps.ts`):
  `node-pty`, `@parcel/watcher`, `@vscode/native-watchdog`, `@vscode/spdlog`,
  `@vscode/sqlite3` — each needs `node-gyp` with `android_ndk_path` + bionic `libc`.
  `kerberos` → **replace** (document loss: Enterprise GSSAPI), `ripgrep-universal` → **replace**
  with bootstrap `rg`, `windows-*` → **omit**.

## How verification will work (Stage 6, but designed now)

1. `kodra` Gradle `assembleDebug` bundles `$PREFIX` as `assets/bootstrap.zip`
2. Device CI (`ubuntu-24.04-arm` emulator or Firebase) installs APK, extracts prefix,
   runs `$PREFIX/bin/node --version == 24.22.1` and `$PREFIX/bin/bash -c 'echo ok'`
   under the app UID, then `node-pty` smoke (`fork bash, write, read`).
3. Full REH smoke (`build/kodra/smokeTestPackagedServer.ts`) will be re-run with the
   **bionic** Node and rebuilt addons; glibc Linux package remains the reference
   but is not an acceptance gate for bionic.

## Not in this commit

- No NDK build scripts yet — only docs + inventory tooling.
- No Gradle project — that's Stage 3.
- No `$HOME`/`$PREFIX` Kotlin wiring — also Stage 3.

See `build/kodra/inventoryNativeDeps.ts` for the current Linux→bionic gap.
