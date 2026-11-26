# Kodra bootstrap assets — populated by Stage 2 NDK build + REH packaging

This directory is **empty in source** and is populated at build time:

- `bootstrap.zip` — Termux-style `$PREFIX` (bin/node 24.22.1 bionic, bash, git, rg, python, etc.)
  Produced by `node build/kodra/bionicBuild.ts --prefix $PREFIX --out kodra/android/app/src/main/assets`
  See `kodra/BOOTSTRAP.md` and `kodra/TOOLCHAIN.md` for NDK `aarch64-linux-android28` build.

- `reh-web.tar.gz` — REH-web output from `pnpm run gulp vscode-reh-web-linux-arm64-min`
  Contains `out/`, `extensions/`, `node_modules/` with bionic-rebuilt `node-pty`, `parcel/watcher`, etc.

CI `kodra-android.yml` warns if missing; `BootstrapInstaller` creates minimal dirs + version marker
so debug APK still installs without assets. Release APK must include both archives.

Do not commit binaries — add to `.gitignore` via `*.zip` and `*.tar.gz` if needed.
