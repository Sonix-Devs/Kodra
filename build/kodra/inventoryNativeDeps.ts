#!/usr/bin/env ts-node
/**
 * Inventory native/bionic-sensitive deps for the Kodra Android bootstrap.
 *
 * Inputs:
 *  - remote/package.json  (nativeBuild + dependencies)
 *  - build/gulpfile.reh.ts BUILD_TARGETS
 *  - packaged REH layout (if available under ../vscode-reh-* — not required in sandbox)
 *
 * Output:
 *  JSON with classification: rebuild (needs bionic+ABI rebuild), binary (prebuilt glibc),
 *  replace (provide alternative), omit (documented loss). Used by PORTING_NOTES Stage 2.
 *
 * Stage 1 delivered linux-arm64 glibc. Stage 2 must NOT ship that node/native closure
 * on Android; this script makes the gap explicit.
 */

import fs from 'fs';
import path from 'path';

const REPO_ROOT = path.resolve(import.meta.dirname, '..', '..');
const remotePkg = JSON.parse(fs.readFileSync(path.join(REPO_ROOT, 'remote/package.json'), 'utf8'));

type Class = 'rebuild' | 'binary' | 'replace' | 'omit' | 'unknown';
interface Item { name: string; version: string; klass: Class; reason: string; }

const rules: Record<string, { klass: Class; reason: string }> = {
  'node-pty': { klass: 'rebuild', reason: 'ABI: native addon against bionic Node 24.22.1; glibc prebuild unusable.' },
  '@parcel/watcher': { klass: 'rebuild', reason: 'native watcher; rebuild or polling fallback.' },
  '@vscode/native-watchdog': { klass: 'rebuild', reason: 'watchdog/ptrace; bionic rebuild.' },
  '@vscode/spdlog': { klass: 'rebuild', reason: 'native logger; bionic rebuild or replace.' },
  '@vscode/sqlite3': { klass: 'rebuild', reason: 'native sqlite; bionic toolchain or omit.' },
  'kerberos': { klass: 'replace', reason: 'GSSAPI rare on Android; omit, document Enterprise loss.' },
  '@vscode/ripgrep-universal': { klass: 'replace', reason: 'prebuilt glibc/musl rg; ship bootstrap-built rg.' },
  '@vscode/windows-process-tree': { klass: 'omit', reason: 'Windows-only.' },
  '@vscode/windows-registry': { klass: 'omit', reason: 'Windows-only.' },
  // pure JS / WASM — keep (41 previously-unknown now triaged)
  '@devcontainers/cli': { klass: 'binary', reason: 'JS CLI for devcontainers; keep, docker gap expected on Android.' },
  '@hpke/core': { klass: 'binary', reason: 'Pure JS HPKE; keep.' },
  '@microsoft/1ds-core-js': { klass: 'binary', reason: '1DS telemetry JS (inert, enableTelemetry:false); keep.' },
  '@microsoft/1ds-post-js': { klass: 'binary', reason: '1DS post channel JS; keep.' },
  '@microsoft/copilot-sdk': { klass: 'binary', reason: 'Copilot SDK JS wrapper; gated by defaultChatAgent absent — keep.' },
  '@vscode/copilot-api': { klass: 'binary', reason: 'Copilot API shim; gated — keep.' },
  '@vscode/deviceid': { klass: 'binary', reason: 'Device ID; Linux reads machine-id, Android falls back to ANDROID_ID — verify on device.' },
  '@vscode/diff': { klass: 'binary', reason: 'Pure JS diff; keep.' },
  '@vscode/fs-copyfile': { klass: 'binary', reason: 'fs.copyFile wrapper; bionic fallback is plain copy — keep.' },
  '@vscode/iconv-lite-umd': { klass: 'binary', reason: 'Pure JS charset; keep.' },
  '@vscode/proxy-agent': { klass: 'binary', reason: 'PAC/proxy JS; keep.' },
  '@vscode/sandbox-runtime': { klass: 'binary', reason: 'JS sandbox helpers; keep.' },
  '@vscode/tree-sitter-wasm': { klass: 'binary', reason: 'WASM tree-sitter; keep.' },
  '@vscode/vscode-languagedetection': { klass: 'binary', reason: 'WASM language detection; keep.' },
  '@xterm/addon-clipboard': { klass: 'binary', reason: 'xterm.js addon JS; keep.' },
  '@xterm/addon-image': { klass: 'binary', reason: 'xterm.js addon JS; keep.' },
  '@xterm/addon-ligatures': { klass: 'binary', reason: 'xterm.js addon JS; keep.' },
  '@xterm/addon-progress': { klass: 'binary', reason: 'xterm.js addon JS; keep.' },
  '@xterm/addon-search': { klass: 'binary', reason: 'xterm.js addon JS; keep.' },
  '@xterm/addon-serialize': { klass: 'binary', reason: 'xterm.js addon JS; keep.' },
  '@xterm/addon-unicode11': { klass: 'binary', reason: 'xterm.js addon JS; keep.' },
  '@xterm/addon-webgl': { klass: 'binary', reason: 'xterm.js addon JS + WebGL fallback; keep.' },
  '@xterm/headless': { klass: 'binary', reason: 'Pure JS xterm headless; keep as-is.' },
  '@xterm/xterm': { klass: 'binary', reason: 'xterm.js core JS; keep.' },
  'cookie': { klass: 'binary', reason: 'JS cookie parser; keep.' },
  'detect-libc': { klass: 'binary', reason: 'Detects glibc vs musl; on bionic returns unknown — harmless.' },
  'http-proxy-agent': { klass: 'binary', reason: 'JS proxy agent; keep.' },
  'https-proxy-agent': { klass: 'binary', reason: 'JS proxy agent; keep.' },
  'jschardet': { klass: 'binary', reason: 'JS charset detection; keep.' },
  'katex': { klass: 'binary', reason: 'JS math rendering; keep.' },
  'libsodium-wrappers': { klass: 'binary', reason: 'Sodium wrappers with WASM fallback; keep.' },
  'minimist': { klass: 'binary', reason: 'JS arg parser; keep.' },
  'ssh2': { klass: 'binary', reason: 'Pure JS SSH2 via Node crypto (OpenSSL in bionic Node); keep.' },
  'tar': { klass: 'binary', reason: 'JS tar; keep.' },
  'tas-client': { klass: 'binary', reason: 'TAS experimentation JS; keep (inert).' },
  'vscode-oniguruma': { klass: 'binary', reason: 'Oniguruma via WASM (onig.wasm) — force WASM on Android; keep.' },
  'vscode-regexpp': { klass: 'binary', reason: 'JS regex parser; keep.' },
  'vscode-textmate': { klass: 'binary', reason: 'JS TextMate; keep.' },
  'ws': { klass: 'binary', reason: 'JS WebSocket; keep.' },
  'yauzl': { klass: 'binary', reason: 'JS zip reader; keep.' },
  'yazl': { klass: 'binary', reason: 'JS zip writer; keep.' },
  'zod': { klass: 'binary', reason: 'JS schema validation; keep.' },
};

const deps = remotePkg.dependencies as Record<string, string>;
const items: Item[] = Object.entries(deps).map(([name, version]) => {
  const r = rules[name];
  return { name, version, klass: r?.klass ?? 'unknown', reason: r?.reason ?? 'Needs triage — inspect usage under src/vs/server and REH layout.' };
});

const summary = {
  repo: path.basename(REPO_ROOT),
  node: remotePkg.nativeBuild,
  generatedAt: new Date().toISOString(),
  note: 'Stage 2 bootstrap inventory — Linux glibc closure vs Android/bionic. See PORTING_NOTES Architecture §5.',
  counts: Object.fromEntries([...new Set(items.map(i => i.klass))].map(k => [k, items.filter(i => i.klass === k).length])),
  items: items.sort((a, b) => a.klass.localeCompare(b.klass) || a.name.localeCompare(b.name)),
};

const out = process.argv.includes('--json') ? JSON.stringify(summary, null, 2) : [
  `# Kodra bootstrap inventory (Android/bionic vs current Linux glibc)`,
  `Node target: ${remotePkg.nativeBuild.target} (${remotePkg.nativeBuild.disturl})`,
  ``,
  ...items.map(i => `- **${i.name}@${i.version}** — ${i.klass}: ${i.reason}`),
  ``,
  `Note: this is source-only inventory; packaged REH closure not introspected in this sandbox.`,
].join('\n');

process.stdout.write(out + '\n');
