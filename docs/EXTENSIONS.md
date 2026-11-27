# Kodra bundled extensions

Source each extension from its upstream repository at a pinned tag, verify the license
in that repository, build the `.vsix`, and bundle it as a built-in extension.
Record the result here. The Extensions view keeps its icon and shows no Marketplace
results (`product.json` has no `extensionsGallery`).

## Policy

- Keep Kodra source public; document GPL obligations for the bundled `git` binary separately.
- Never bundle proprietary `ms-vscode.cpptools` language-server/debugger binaries.
- Each extension is reviewed at its exact pinned tag (see `product.json` `builtInExtensions`).
- `vscode.github-authentication` is replaced by Kodra's own `github` authentication provider (Device Flow via `DeviceAuthFlow.kt` + `GithubAuth.kt`). Do not bundle it.
- `vscode.microsoft-authentication` / `vscode.tunnel-forwarding` stay disabled (Microsoft services).
- Secrets (OAuth client ID, Discord app ID, keystore, SDK AARs) never go into the repo.

## Already bundled (pinned MIT, verified at tag)

| extension | version | sha256 | repo | license at tag | status |
|---|---|---|---|---|---|
| `ms-vscode.js-debug-companion` | 1.1.3 | `7380a890…52de93` | `microsoft/vscode-js-debug-companion` | MIT (`v1.1.3`) | **Bundled** via `product.json` |
| `ms-vscode.js-debug` | 1.140.1 | `7accf785…c1897` | `microsoft/vscode-js-debug` | MIT (`v1.140.1`) | **Bundled** |
| `ms-vscode.vscode-js-profile-table` | 1.0.11 | `a962a1e6…48ae9` | `microsoft/vscode-js-profile-visualizer` | MIT (`v1.0.11`) | **Bundled** |

License metadata checked at each exact tag; `ThirdPartyNotices.txt` updated by `build/gulpfile.reh.ts`.
These three are also the only `product.json` `builtInExtensions` entries today — every other row below
is **not yet bundled** and stays `Scaffolded, unverified` until a CI build proves it.

## Port — upstream source, license check, and what Kodra provides

For each row: source repo + pinned tag → check `LICENSE` at that tag → build `.vsix` → add to
`product.json` `builtInExtensions` → `pnpm run gulp vscode-reh-linux-arm64-min` puts it in `extensions/`
→ verify no `ms-vscode.cpptools` native dep leaked, no new `update.code.visualstudio.com` / `vscode-cdn.net`
endpoint, no telemetry host beyond the allowed SDK default constant.

### Formatting, icons, Live Server

| extension | marketplace id | repo | tag to pin | license expected | Kodra notes |
|---|---|---|---|---|---|
| Prettier | `esbenp.prettier-vscode` | `prettier/prettier-vscode` | latest `vX.Y.Z` (check `LICENSE` at tag) | MIT | Formatter, no native binary. Test `editor.formatOnSave`. |
| Material Icon Theme | `PKief.material-icon-theme` | `PKief/vscode-material-icon-theme` | latest `v4.Y.Z` | MIT | Icons only. |
| Live Server | `ritwickdey.LiveServer` | `ritwickdey/vscode-live-server` | latest `v5.Y.Z` | MIT | `localhost` HTTP; must open Android default browser via `Intent.ACTION_VIEW`, not WebView. `host: 127.0.0.1` → Intent. |
| Error Lens | `usernamehw.errorlens` | `usernamehw/vscode-error-lens` | latest `v3.Y.Z` | MIT | Diagnostics inline; no native dep. |
| Biome | `biomejs.biome` | `biomejs/biome` | latest `v1.Y.Z` or `v2.Y.Z` | MIT | Needs bionic `biome` binary — build from `biomejs/biome` with NDK `aarch64-linux-android28` and install as `$PREFIX/bin/biome`; extension's `biome.path` → that binary. |
| Go | `golang.go` | `golang/vscode-go` | latest `v0.Y.Z` | MIT | Add `go` + `gopls` to bootstrap (`kodra/prefix-layout.json` `bin`); extension's `go.toolsManagement` + `gopls` path → `$PREFIX/bin`. |
| Draw.io | `hediet.vscode-drawio` | `hediet/vscode-drawio` | latest `v1.Y.Z` | MIT | Webview-based; no native binary. |

### Python

| extension | marketplace id | repo | tag | license | Kodra notes |
|---|---|---|---|---|---|
| Python | `ms-python.python` | `microsoft/vscode-python` | latest `v2024.Y.Z` | MIT | **Without Pylance** — provide bootstrap `python3` (`kodra/BOOTSTRAP.md`) and set `python.defaultInterpreterPath` → `$PREFIX/bin/python3`. |
| Python Debugger | `ms-python.debugpy` | `microsoft/debugpy` | latest `v1.Y.Z` | MIT | Ships `debugpy` wheel; uses bootstrap `python3`. |
| Python Envs | `ms-python.vscode-python-envs` | `microsoft/vscode-python-envs` | latest `v1.Y.Z` | MIT | Env manager; verify on device. |

`ms-python.vscode-pylance` is **proprietary** — do not bundle. Replacement: `basedpyright` (MIT, `detachhead/basedpyright`) — configure `python.languageServer` → `basedpyright`.

### Containers (no daemon on Android)

| extension | marketplace id | repo | tag | license | Kodra notes |
|---|---|---|---|---|---|
| Docker | `ms-azuretools.vscode-docker` | `microsoft/vscode-docker` | latest `v1.Y.Z` | MIT | Installs, but Android has no Docker daemon. Document: Dockerfile/docker-compose language support + registry browsing work; build/run/debug do not. |
| Containers | `ms-azuretools.vscode-containers` | `microsoft/vscode-containers` | latest | MIT | Same — install, document daemon gap identically. |

### AnyCode + language packs

| extension | marketplace id | repo | tag | license | Kodra notes |
|---|---|---|---|---|---|
| AnyCode | `ms-vscode.anycode` | `microsoft/vscode-anycode` | latest | MIT | WASM tree-sitter; works on REH. |
| AnyCode packs | `anycode-*` | `ms-vscode` / `anycode-*` | matching AnyCode release | MIT | Bundle the packs for the languages the user needs; each is a separate `product.json` entry. |

### JavaScript debugging (already bundled)

`ms-vscode.js-debug` + `js-debug-companion` + `vscode-js-profile-table` are already in `product.json`
at MIT tags above. No further action.

### Built-ins that stay (except Microsoft-service ones)

Every `vscode.*` built-in from `extensions/` stays enabled, **except** the Microsoft-hosted service
extensions listed in the next section. Examples that stay: `vscode.git`, `vscode.github` (replaced auth
only), `vscode.npm`, `vscode.typescript-language-features`, etc. No Marketplace categories/results are
shown — `ExtensionGalleryManifestService` returns `Unavailable` when `product.json` has no `extensionsGallery.serviceUrl`.

## Do not bundle — reason + replacement

| extension | reason | replacement |
|---|---|---|
| `ms-python.vscode-pylance` | Proprietary license | `basedpyright` (`detachhead/basedpyright`, MIT). Set `python.languageServer` accordingly. |
| `ms-vscode.cpptools` (binaries) | Proprietary `cpptools` language-server/debugger binaries | Bootstrap `clangd` + `lldb`/`CodeLLDB` — `ms-vscode.cpptools` native deps must not leak into the build. Document gap in `PORTING_NOTES.md`. |
| `ms-vsliveshare.vsliveshare` | Proprietary + Microsoft relay | No replacement; document as unsupported. |
| `ms-vscode.remote-explorer` | Microsoft remote infra | Do not bundle. |
| `ms-vscode.remote-repositories` | Microsoft remote infra | Do not bundle. |
| `ms-vscode.remote-server` | Microsoft remote infra | Do not bundle. |
| `github.remotehub` | Proprietary | Do not bundle. |
| `github.codespaces` | Proprietary | Do not bundle. |
| `github.copilot-chat` | Proprietary | Do not bundle. |
| `typescriptteam.jsts-chat-features` | Proprietary | Do not bundle. |
| `ms-vscode.azure-repos` | Check license — GitHub-owned, likely not MIT; exclude if proprietary | Do not bundle unless license review passes (MIT/Apache-2.0 only). |
| `vscode.microsoft-authentication` | Microsoft service | Keep disabled. |
| `vscode.tunnel-forwarding` | Microsoft service | Keep disabled. |
| `vscode.github-authentication` | Replaced | Kodra's own `github` provider (Device Flow) replaces it. |

## Discord — icrawl.discord-vscode 5.9.2 (separate gate)

Do not re-implement presence logic in Kotlin. Bundle the original `icrawl.discord-vscode` **5.9.2**
TypeScript code (`extension.ts`, `activity.ts`, `util.ts`, `constants.ts`, `data/languages.json`) unchanged;
replace only the transport:

- Remove `@xhayper/discord-rpc` IPC client. Add a transport that sends activity updates
  (set/clear/connect/disconnect/reconnect) from the extension host to `DiscordPresenceService`
  inside the app over an authenticated loopback channel (per-launch random token, `127.0.0.1` or
  Unix socket). That service uses the Discord Social SDK (`DiscordSocialSdkClient` preserved from
  CodePresence). Presence lives inside Kodra; no separate app.
- Preserve all `discord.*` settings with original defaults, 4 commands (enable/disable/reconnect/disconnect),
  details/state templates/placeholders, 2-s throttling, idle behavior, repository button, workspace exclude
  patterns, and language icon + hover text exactly as original (e.g. "Editing ... file").
- Use the 205 PNGs from CodePresence `discord-assets/` unchanged. Owner uploads them to the Discord Developer Portal.
- Do **not** reuse the original author's Discord application ID. Owner must supply his own application ID
  and `discord_partner_sdk.aar` at this gate — until then, build with SDK-free fallback and mark "unverified".
- Document the one-time Discord authorization flow exactly as it behaves (browser/app tab) and clear presence on `KodraService.onDestroy`.

## Process per extension (repeat for each)

1. Resolve `repo` + exact tag + `sha256` (Marketplace vsix or GitHub release).
2. Fetch `LICENSE` at that tag, confirm SPDX is allowlisted (MIT/Apache-2.0).
3. Add to `product.json` `builtInExtensions` (or REH packager allowlist).
4. Run `pnpm run gulp vscode-reh-linux-arm64-min` — extension appears in `extensions/`.
5. Verify no `ms-vscode.cpptools` native dep leaked, no telemetry endpoint beyond allowed SDK constant.
6. Update this file + `ThirdPartyNotices.txt` + `README.md` scope with evidence link.

## Status in this commit

No new `product.json` entries beyond the 3 already-bundled js-debug/profile extensions. This file is
the source-of-truth for review; every other extension above is **not yet bundled** and will be added
one at a time with license evidence at its pinned tag.
