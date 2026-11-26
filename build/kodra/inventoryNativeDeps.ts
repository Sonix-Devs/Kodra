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
  'node-pty': { klass: 'rebuild', reason: 'ABI: native addon must be built against Android/bionic Node 24.22.1; glibc prebuild unusable on bionic.' },
  '@parcel/watcher': { klass: 'rebuild', reason: 'File-watcher: native binding; rebuild for bionic or replace with polling fallback.' },
  '@vscode/native-watchdog': { klass: 'rebuild', reason: 'Watchdog spawns/PTrace; requires bionic rebuild.' },
  '@vscode/spdlog': { klass: 'rebuild', reason: 'Native logger binding; rebuild for bionic or replace.' },
  '@vscode/sqlite3': { klass: 'rebuild', reason: 'sqlite3 native; bionic toolchain or omit (lose workspace-DB features).' },
  'kerberos': { klass: 'replace', reason: 'GSSAPI on Android rare; omit/optional—document Enterprise loss.' },
  '@vscode/ripgrep-universal': { klass: 'replace', reason: 'Prebuilt ripgrep binaries are glibc/musl; ship bootstrap-built ripgrep.' },
  '@vscode/windows-process-tree': { klass: 'omit', reason: 'Windows-only; no Android equivalent.' },
  '@vscode/windows-registry': { klass: 'omit', reason: 'Windows-only; omit.' },
  '@xterm/headless': { klass: 'binary', reason: 'Pure JS (xterm headless); keep as-is.' },
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
