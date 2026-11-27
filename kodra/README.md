# Kodra — Stage 2: bootstrap (Android/bionic)

This directory will own Kodra-specific build glue for the Android runtime.

Stage 1 delivered a branded Linux/glibc REH/REH-web and a host-inventory CI gate
(`45707121359` on `a5831013`). Stage 1 does **not** provide an Android/bionic binary.

Stage 2 must produce a Termux-style prefix under Kodra's private app storage:

  /data/data/dev.sonixdevs.kodra/files/usr

and prove we can `pkg`/apt-install the bootstrap closure against bionic:

  bash, coreutils, pkg/apt, Git, OpenSSH, curl, Node.js 24.22.1, Python, clang, make, CMake, ripgrep

with at least `node-pty` rebuilt against the Android Node. See PORTING_NOTES §§
*Build and runtime findings* and *Architecture decisions* §5.

## Scope for this branch

- inventory the current packaged dependency closure (`remote/package.json` + REH layout)
- decide rebuild / replace / omit for each native/bionic-sensitive package with a documented impact
- scaffold `build/kodra/` inventory tooling and `kodra/` prefix layout docs
- NO Gradle project or APK yet — that is Stage 3.

## How to run locally (outside this sandbox)

```sh
pnpm install               # remote/ and build/
node build/kodra/inventoryNativeDeps.ts --json > kodra/deps.json
```

Artifacts are host-local; this sandbox has no Android NDK/SDK and cannot build
bionic binaries. Device verification is Stage 6.
