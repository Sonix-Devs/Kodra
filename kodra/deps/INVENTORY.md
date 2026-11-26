# Bootstrap inventory — 2026-11-26
Generated from remote/package.json nativeBuild 24.22.1 @ https://nodejs.org/dist

| package | version | class | notes |
|---|---|---|---|
| `@xterm/headless` | `^6.1.0-beta.335` | binary | pure JS; keep. |
| `@vscode/windows-process-tree` | `^0.8.0` | omit | Windows-only. |
| `@vscode/windows-registry` | `^1.3.0` | omit | Windows-only. |
| `@parcel/watcher` | `^2.5.6` | rebuild | native watcher; rebuild or polling fallback. |
| `@vscode/native-watchdog` | `^1.4.6` | rebuild | watchdog/ptrace; bionic rebuild. |
| `@vscode/spdlog` | `^0.15.8` | rebuild | native logger; bionic rebuild or replace. |
| `@vscode/sqlite3` | `5.1.12-vscode` | rebuild | native sqlite; bionic toolchain or omit. |
| `node-pty` | `^1.2.0-beta.18` | rebuild | ABI: bionic Node 24.22.1; glibc prebuild unusable. |
| `@vscode/ripgrep-universal` | `^1.18.0` | replace | prebuilt glibc/musl; ship bootstrap rg. |
| `kerberos` | `2.1.1` | replace | GSSAPI rare on Android; document loss. |
| `@devcontainers/cli` | `0.88.0` | unknown | inspect usage under src/vs/server and REH layout. |
| `@hpke/core` | `^1.9.0` | unknown | inspect usage under src/vs/server and REH layout. |
| `@microsoft/1ds-core-js` | `^3.2.13` | unknown | inspect usage under src/vs/server and REH layout. |
| `@microsoft/1ds-post-js` | `^3.2.13` | unknown | inspect usage under src/vs/server and REH layout. |
| `@microsoft/copilot-sdk` | `2.0.1` | unknown | inspect usage under src/vs/server and REH layout. |
| `@vscode/copilot-api` | `^0.5.2` | unknown | inspect usage under src/vs/server and REH layout. |
| `@vscode/deviceid` | `^0.1.1` | unknown | inspect usage under src/vs/server and REH layout. |
| `@vscode/diff` | `0.0.2-145` | unknown | inspect usage under src/vs/server and REH layout. |
| `@vscode/fs-copyfile` | `2.0.0` | unknown | inspect usage under src/vs/server and REH layout. |
| `@vscode/iconv-lite-umd` | `0.7.1` | unknown | inspect usage under src/vs/server and REH layout. |
| `@vscode/proxy-agent` | `^0.46.0` | unknown | inspect usage under src/vs/server and REH layout. |
| `@vscode/sandbox-runtime` | `0.0.1` | unknown | inspect usage under src/vs/server and REH layout. |
| `@vscode/tree-sitter-wasm` | `^0.3.1` | unknown | inspect usage under src/vs/server and REH layout. |
| `@vscode/vscode-languagedetection` | `1.0.23` | unknown | inspect usage under src/vs/server and REH layout. |
| `@xterm/addon-clipboard` | `^0.3.0-beta.335` | unknown | inspect usage under src/vs/server and REH layout. |
| `@xterm/addon-image` | `^0.10.0-beta.332` | unknown | inspect usage under src/vs/server and REH layout. |
| `@xterm/addon-ligatures` | `^0.11.0-beta.331` | unknown | inspect usage under src/vs/server and REH layout. |
| `@xterm/addon-progress` | `^0.3.0-beta.331` | unknown | inspect usage under src/vs/server and REH layout. |
| `@xterm/addon-search` | `^0.17.0-beta.331` | unknown | inspect usage under src/vs/server and REH layout. |
| `@xterm/addon-serialize` | `^0.15.0-beta.331` | unknown | inspect usage under src/vs/server and REH layout. |
| `@xterm/addon-unicode11` | `^0.10.0-beta.331` | unknown | inspect usage under src/vs/server and REH layout. |
| `@xterm/addon-webgl` | `^0.20.0-beta.330` | unknown | inspect usage under src/vs/server and REH layout. |
| `@xterm/xterm` | `^6.1.0-beta.337` | unknown | inspect usage under src/vs/server and REH layout. |
| `cookie` | `^0.7.0` | unknown | inspect usage under src/vs/server and REH layout. |
| `detect-libc` | `^2.1.2` | unknown | inspect usage under src/vs/server and REH layout. |
| `http-proxy-agent` | `^7.0.0` | unknown | inspect usage under src/vs/server and REH layout. |
| `https-proxy-agent` | `^7.0.2` | unknown | inspect usage under src/vs/server and REH layout. |
| `jschardet` | `3.1.4` | unknown | inspect usage under src/vs/server and REH layout. |
| `katex` | `^0.16.22` | unknown | inspect usage under src/vs/server and REH layout. |
| `libsodium-wrappers` | `^0.8.4` | unknown | inspect usage under src/vs/server and REH layout. |
| `minimist` | `^1.2.8` | unknown | inspect usage under src/vs/server and REH layout. |
| `ssh2` | `^1.16.0` | unknown | inspect usage under src/vs/server and REH layout. |
| `tar` | `^7.5.20` | unknown | inspect usage under src/vs/server and REH layout. |
| `tas-client` | `0.4.6` | unknown | inspect usage under src/vs/server and REH layout. |
| `vscode-oniguruma` | `1.7.0` | unknown | inspect usage under src/vs/server and REH layout. |
| `vscode-regexpp` | `^3.1.0` | unknown | inspect usage under src/vs/server and REH layout. |
| `vscode-textmate` | `^9.3.2` | unknown | inspect usage under src/vs/server and REH layout. |
| `ws` | `^8.19.0` | unknown | inspect usage under src/vs/server and REH layout. |
| `yauzl` | `^3.3.1` | unknown | inspect usage under src/vs/server and REH layout. |
| `yazl` | `^2.4.3` | unknown | inspect usage under src/vs/server and REH layout. |
| `zod` | `^4.4.3` | unknown | inspect usage under src/vs/server and REH layout. |

_Total deps: 51; by class: rebuild=5, replace=2, omit=2, binary=1, unknown=41_

> Linux/glibc REH closure is NOT shippable on bionic. This file is the Stage-2 source of truth; CI will diff it against generated output.
