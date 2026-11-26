# Kodra — Android runtime & app

This directory owns Kodra-specific build glue for the Android runtime and the
Kotlin app. Stage 1 delivered a branded Linux/glibc REH/REH-web and a host-inventory
CI gate (`45707121359` on `a5831013`). Stages 2–6 deliver the bionic replacement:

- **Bootstrap:** Termux-style prefix at `/data/data/dev.unalivin.kodra/files/usr`
  (`bash`, `coreutils`, `pkg/apt`, `git`, `openssh`, `curl`, `nodejs 24.22.1`,
  `python`, `clang`, `make`, `cmake`, `ripgrep`) with `node-pty` rebuilt against
  bionic Node — see `kodra/BOOTSTRAP.md`, `kodra/TOOLCHAIN.md`, `build/kodra/bionicBuild.ts`
- **Inventory:** `remote/package.json` → `kodra/deps/INVENTORY.md` (51 deps, 0 unknown,
  `rebuild=5/replace=2/omit=2/binary=42`) via `build/kodra/inventoryNativeDeps.ts`
- **Android app:** `kodra/android/` (Gradle 8.7 / AGP 8.5.2 / Kotlin 1.9.24) — single-Activity
  WebView + foreground service + SAF bridge, `kodra-android.yml` / `kodra-release.yml`
- **Docs:** `docs/` (sample settings, extensions, battery, user guide) + `kodra/RELEASE.md`

See `PORTING_NOTES.md` §§ *Build and runtime findings* and *Architecture decisions* for the full
design and verification gates.

## How to run locally (outside sandbox)

```sh
pnpm install
node build/kodra/inventoryNativeDeps.ts --json > kodra/deps.json
node build/kodra/inventory-to-markdown.mjs && git diff --exit-code -- kodra/deps/INVENTORY.md
./kodra/android/gradlew :app:assembleDebug  # requires Android SDK 34 + Java 17
./kodra/android/gradlew :app:lintDebug
```

Device verification (bionic `node --version`, `bash -c 'echo ok'`, `node-pty` smoke,
service lifecycle, SAF, Git/Device Flow) is Stage 6 — see `kodra/RELEASE.md`.
Sandbox has no NDK/SDK; CI performs artifact + endpoint checks.
