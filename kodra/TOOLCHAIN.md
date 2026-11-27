# Kodra NDK toolchain — bionic Node 24.22.1

## Why a separate toolchain

`remote/package.json` pins `nativeBuild.target=24.22.1` from `https://nodejs.org/dist`.
Stage 1 downloads the **Linux/glibc** `linux-arm64` tarball and ships its prebuilt
`node` + `prebuilds/` (glibc). That binary crashes on Android/bionic (`ld-linux-aarch64.so.1` vs `linker64`).

Stage 2 must build Node and every `rebuild` addon against the **Android NDK r26+**
sysroot for `aarch64-linux-android28` (`targetSdk 28`).

## Prerequisites (outside sandbox)

- Android NDK r26d+ at `$ANDROID_NDK_ROOT` (or `ANDROID_NDK_HOME`)
- Host toolchain: `clang`, `python3`, `make`, `ninja`, `pkg-config`
- Termux `termux-elf-cleaner` for relocatable `$PREFIX` ELFs

Sandbox has none of these — verification is host-only `node` syntax + inventory diff.

## Node build (bionic)

```sh
export NDK=$ANDROID_NDK_ROOT
export TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64
export CC=$TOOLCHAIN/bin/aarch64-linux-android28-clang
export CXX=$TOOLCHAIN/bin/aarch64-linux-android28-clang++
export AR=$TOOLCHAIN/bin/llvm-ar
export NDKVER=28

# Node 24.22.1 from nodejs.org/dist with bionic patches (termux/nodejs)
tar xzf node-v24.22.1.tar.gz && cd node-v24.22.1
./configure --dest-os=android --dest-cpu=arm64 \
  --with-intl=small --openssl-no-asm \
  --dest-cpu=arm64 --cross-compiling
make -j$(nproc) && make install DESTDIR=$PREFIX
$PREFIX/bin/node --version  # must print v24.22.1
```

Key patches vs upstream: `android/` linker flags, `uv` `getentropy` fallback to
`/dev/urandom` on older kernels, `node-gyp` `common.gypi` `android_ndk_path`.

## Native addons (5× rebuild)

Each `rebuild` entry from `kodra/deps/INVENTORY.md` (`node-pty`, `@parcel/watcher`,
`@vscode/native-watchdog`, `@vscode/spdlog`, `@vscode/sqlite3`) is built with:

```sh
export CC CXX AR   # as above
export npm_config_arch=arm64 npm_config_target_arch=arm64
export npm_config_target=24.22.1 npm_config_disturl=https://nodejs.org/dist
npm_config_build_from_source=true pnpm rebuild <pkg> --build-from-source
# Verify ELF is bionic, not glibc:
readelf -d remote/node_modules/<pkg>/build/Release/*.node | grep NEEDED
# must NOT contain ld-linux-aarch64.so.1; must be linker64 + liblog.so
```

`node-pty` additionally needs `termux-pty` shim for `grantpt` on Android.

## Replacements / omissions

- `kerberos` → omit (Enterprise GSSAPI; document as unsupported).
- `@vscode/ripgrep-universal` → build `rg` from `BurntSushi/ripgrep` with NDK and
  install as `$PREFIX/bin/rg`; shim `remote/node_modules/@vscode/ripgrep-universal`
  to call it.
- `@vscode/windows-process-tree`, `@vscode/windows-registry` → never installed on
  Android (guarded by `process.platform !== 'win32'` in `extensionHost`).
- `ms-vscode.cpptools` binaries → do not bundle; bootstrap `clangd`/`lldb` are the replacement.

## Verification (device CI)

```
$PREFIX/bin/node --version == v24.22.1
$PREFIX/bin/bash -c 'echo ok' == ok
$PREFIX/bin/rg --version
node -e "require('node-pty').spawn('bash',[],{}).onData(()=>{})"  # fork+read
```

glibc REH smoke (`build/kodra/smokeTestPackagedServer.ts`) will be duplicated with
the bionic Node once available; glibc remains reference only.

## Build glue in repo

- `build/kodra/bionicBuild.ts` — packages `$PREFIX` → `assets/bootstrap.zip` + `reh-web.tar.gz`, verifies ELF headers (no `ld-linux-aarch64.so.1`, must link `linker64`/`liblog.so`).
- `kodra/android/` — Gradle project (`kodra-android.yml` `assembleDebug`+`lint`).

Device CI verifies `$PREFIX/bin/node --version` + `bash -c` + `node-pty` smoke.
No NDK build has run yet — this is the spec plus the packaging glue.

## Bootstrap package manifest (what CI will publish)

`kodra/android/app/src/main/assets/bootstrap.zip` (from `$PREFIX`):

```
bin/{bash,node,rg,git,ssh,curl,python3,clang,make,cmake,go,gopls,biome,busybox}
lib/{libnode.so, libgit2.so, ...} + lib/node_modules/kodra-reh/{out/, extensions/, node_modules/ (5 rebuilt)}
etc/{bash.bashrc, gitconfig, ssh/ssh_config}
```

`reh-web.tar.gz` is extracted by `BootstrapInstaller.extractRehTar` (pure `GZIPInputStream` + `extractTar`, no `tar` binary).
