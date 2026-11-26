import fs from 'fs';
import path from 'path';
const repoRoot = path.resolve(import.meta.dirname, '../..');
const pkg = JSON.parse(fs.readFileSync(path.join(repoRoot, 'remote/package.json'), 'utf8'));
const rules = {
  'node-pty': ['rebuild','ABI: bionic Node 24.22.1; glibc prebuild unusable.'],
  '@parcel/watcher': ['rebuild','native watcher; rebuild or polling fallback.'],
  '@vscode/native-watchdog': ['rebuild','watchdog/ptrace; bionic rebuild.'],
  '@vscode/spdlog': ['rebuild','native logger; bionic rebuild or replace.'],
  '@vscode/sqlite3': ['rebuild','native sqlite; bionic toolchain or omit.'],
  'kerberos': ['replace','GSSAPI rare on Android; document loss.'],
  '@vscode/ripgrep-universal': ['replace','prebuilt glibc/musl; ship bootstrap rg.'],
  '@vscode/windows-process-tree': ['omit','Windows-only.'],
  '@vscode/windows-registry': ['omit','Windows-only.'],
  '@xterm/headless': ['binary','pure JS; keep.'],
};
const deps = pkg.dependencies;
const rows = Object.entries(deps).map(([name,ver])=>{
  const r=rules[name]||['unknown','inspect usage under src/vs/server and REH layout.'];
  return {name, ver, klass:r[0], reason:r[1]};
}).sort((a,b)=>a.klass.localeCompare(b.klass)||a.name.localeCompare(b.name));
const md = [`# Bootstrap inventory — ${new Date().toISOString().slice(0,10)}`,
`Generated from remote/package.json nativeBuild ${pkg.nativeBuild.target} @ ${pkg.nativeBuild.disturl}`,
'',
'| package | version | class | notes |',
'|---|---|---|---|',
...rows.map(r=>`| \`${r.name}\` | \`${r.ver}\` | ${r.klass} | ${r.reason} |`),
'',
`_Total deps: ${rows.length}; by class: ${['rebuild','replace','omit','binary','unknown'].map(k=>`${k}=${rows.filter(r=>r.klass===k).length}`).join(', ')}_`,
'',
'> Linux/glibc REH closure is NOT shippable on bionic. This file is the Stage-2 source of truth; CI will diff it against generated output.',
];
fs.mkdirSync(path.join(repoRoot,'kodra/deps'),{recursive:true});
fs.writeFileSync(path.join(repoRoot,'kodra/deps/INVENTORY.md'), md.join('\n')+'\n');
console.log('wrote kodra/deps/INVENTORY.md with', rows.length, 'rows');
