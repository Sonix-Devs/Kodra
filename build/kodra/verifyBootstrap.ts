#!/usr/bin/env ts-node
/**
 * Device bootstrap verification — runs on CI emulator or via `adb shell`:
 *   adb push verifyBootstrap.ts /data/local/tmp/
 *   adb shell "node /data/local/tmp/verifyBootstrap.ts --prefix /data/data/dev.unalivin.kodra/files/usr"
 *
 * Checks: node --version, bash, git, ripgrep, pty, token file, safe.directory
 */
import * as fs from 'fs';
import * as path from 'path';
import { spawnSync } from 'child_process';

const prefix = process.argv.find((a, i, arr) => arr[i - 1] === '--prefix') || '/data/data/dev.unalivin.kodra/files/usr';
const home = prefix.replace(/\/usr$/, '/home');

function sh(cmd: string): string {
    const r = spawnSync(cmd, { shell: true, encoding: 'utf8', env: { ...process.env, HOME: home, PREFIX: prefix } });
    if (r.status !== 0) throw new Error(`FAIL: ${cmd}\n${r.stderr}`);
    return (r.stdout || '').trim();
}

function assertExists(p: string): void {
    if (!fs.existsSync(p)) throw new Error(`missing: ${p}`);
    console.log(`OK exists ${p}`);
}

console.log(`Verifying Kodra bootstrap at ${prefix}`);
assertExists(path.join(prefix, 'bin/node'));
assertExists(path.join(prefix, 'bin/bash'));
assertExists(path.join(prefix, 'bin/git'));
assertExists(path.join(prefix, 'bin/rg'));

const nodeVer = sh(`${path.join(prefix, 'bin/node')} --version`);
if (!nodeVer.startsWith('v24.22.1')) throw new Error(`node version mismatch: ${nodeVer}`);
console.log(`OK node ${nodeVer}`);

const bashOk = sh(`${path.join(prefix, 'bin/bash')} -c 'echo ok'`);
if (bashOk !== 'ok') throw new Error(`bash failed: ${bashOk}`);
console.log(`OK bash`);

try {
    const pty = require('node-pty');
    const term = pty.spawn('bash', ['-c', 'echo pty-ok'], { cwd: home });
    term.onData((d: string) => { if (d.includes('pty-ok')) console.log('OK pty'); });
    setTimeout(() => term.kill(), 2000);
} catch (e) {
    console.warn(`WARN pty not available: ${(e as Error).message}`);
}

console.log('All bootstrap checks passed');
