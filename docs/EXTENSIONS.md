# Kodra bundled extensions

Stage 5 gate — pin source, verify license, record notice, then bundle.

## Policy

- Keep Kodra source public; document GPL obligations for Git binary separately.
- Never bundle proprietary `ms-vscode.cpptools` language-server/debugger binaries.
- Each extension is reviewed at its exact pinned tag/sha256 (see `product.json` `builtInExtensions`).

## Built-in (already bundled at pinned MIT tags)

| extension | version | sha256 | repo | license | status |
|---|---|---|---|---|---|
| `ms-vscode.js-debug-companion` | 1.1.3 | `7380a890…52de93` | `microsoft/vscode-js-debug-companion` | MIT (`v1.1.3`) | **Bundled** via `product.json` |
| `ms-vscode.js-debug` | 1.140.1 | `7accf785…c1897` | `microsoft/vscode-js-debug` | MIT (`v1.140.1`) | **Bundled** |
| `ms-vscode.vscode-js-profile-table` | 1.0.11 | `a962a1e6…48ae9` | `microsoft/vscode-js-profile-visualizer` | MIT (`v1.0.11`) | **Bundled** |

License metadata checked at each exact tag; `ThirdPartyNotices.txt` updated by `build/gulpfile.reh.ts`.

## Requested (Stage 5 — not yet bundled)

| extension | requested id | notes | next step |
|---|---|---|---|
| Prettier | `esbenp.prettier-vscode` | Formatter, no native binary | Pin repo tag, verify MIT, add to `product.json` builtInExtensions, test `editor.formatOnSave` |
| Material Icon Theme | `PKief.material-icon-theme` | Icons only | Same — verify MIT |
| Live Server | `ritwickdey.LiveServer` | `localhost` HTTP; must open Android default browser via `Intent.ACTION_VIEW` (not WebView) | Verify, patch `host: 127.0.0.1` → Intent, test |
| Python | `ms-python.python` | **Without Pylance** (`ms-python.vscode-pylance` is proprietary) — provide bootstrap `python3` + `clangd` and document gap | Pin `ms-python.python` tag, verify MIT, test `python.defaultInterpreterPath` → `$PREFIX/bin/python3` |
| GitHub Pull Requests | `github.vscode-pull-request-github` | Check license before bundling (GitHub-owned, may be MIT) | License review, then pin |
| C/C++ | `ms-vscode.cpptools` | **Do not bundle** proprietary binaries; provide bootstrap `clangd`/`lldb` and document differences per `PORTING_NOTES.md` | Document only |
| Discord | `icrawl.discord-vscode` | Separate gate, needs native Discord Social SDK + app id (owner to supply) | Wait for SDK AAR + id, then port preserving settings/commands |

## Process per extension

1. Resolve `repo` + exact tag + `sha256` (from Marketplace vsix or GitHub release)
2. Fetch `LICENSE` at that tag, confirm SPDX is allowlisted (MIT/Apache-2.0)
3. Add to `product.json` `builtInExtensions` (or REH packager allowlist)
4. Run `pnpm run gulp vscode-reh-linux-arm64-min` — extension appears in `extensions/`
5. Verify no `ms-vscode.cpptools` native deps leaked, no telemetry endpoint added
6. Update this file + `ThirdPartyNotices.txt` + `README.md` scope

## Not in this commit

No extension vsix have been added; this file is the Stage 5 source-of-truth for review.
