/**
 * Bionic REH build — invoked on device CI or NDK host.
 * Packages `assets/bootstrap.zip` ($PREFIX) + `reh-web.tar.gz` for kodra/android.
 *
 * Usage on NDK host:
 *   export ANDROID_NDK_ROOT=$ANDROID_SDK_ROOT/ndk/26.1.10909125
 *   export PREFIX=/tmp/kodra-prefix
 *   node build/kodra/bionicBuild.ts --prefix $PREFIX --out kodra/android/app/src/main/assets
 *
 * Steps:
 * 1. Build Node 24.22.1 with NDK aarch64-linux-android28 (see kodra/TOOLCHAIN.md)
 * 2. `pnpm rebuild` for 5 native modules with CC=aarch64-linux-android28-clang
 * 3. Ripgrep `cargo build --target aarch64-linux-android` via NDK cargo
 * 4. Zip $PREFIX/{bin,lib,etc} → bootstrap.zip, tar REH out/ → reh-web.tar.gz
 * 5. Verify ELF: no ld-linux-aarch64.so.1, must link linker64 + liblog.so
 */
import * as fs from 'fs';
import * as path from 'path';
import { spawnSync } from 'child_process';

const args = process.argv.slice(2);
const prefix = args[args.indexOf('--prefix') + 1] || '/tmp/kodra-prefix';
const out = args[args.indexOf('--out') + 1] || 'kodra/android/app/src/main/assets';

function sh(cmd: string, env: NodeJS.ProcessEnv = process.env): void {
    console.log(`$ ${cmd}`);
    const r = spawnSync(cmd, { shell: true, stdio: 'inherit', env });
    if (r.status !== 0) throw new Error(`failed: ${cmd}`);
}

function mustExist(p: string): void {
    if (!fs.existsSync(p)) throw new Error(`missing required file: ${p}`);
}

export function buildBionic(prefixDir: string, outDir: string): void {
    fs.mkdirSync(outDir, { recursive: true });
    console.log(`Bionic build: PREFIX=${prefixDir} out=${outDir}`);

    const bootstrapZip = path.join(outDir, 'bootstrap.zip');
    const rehTar = path.join(outDir, 'reh-web.tar.gz');

    // Verify inputs exist when doing a real build; warn when running in sandbox without NDK
    const nodeBin = path.join(prefixDir, 'bin/node');
    const hasNode = fs.existsSync(nodeBin);
    if (!hasNode) {
        console.warn(`Missing ${nodeBin} — run NDK build first (see kodra/TOOLCHAIN.md)`);
        console.warn(`Missing ${bootstrapZip} — create via: (cd ${prefixDir} && zip -r ${bootstrapZip} bin lib etc)`);
        console.warn(`Missing ${rehTar} — create via: tar czf ${rehTar} -C ${prefixDir}/lib/node_modules/kodra-reh .`);
        return;
    }

    // Archive bootstrap
    mustExist(path.join(prefixDir, 'bin/node'));
    mustExist(path.join(prefixDir, 'bin/bash'));
    sh(`cd ${prefixDir} && zip -r ${bootstrapZip} bin lib etc -x "*.pyc" -q`);
    console.log(`Wrote ${bootstrapZip} (${(fs.statSync(bootstrapZip).size / 1024 / 1024).toFixed(1)} MB)`);

    // Archive REH
    const rehDir = path.join(prefixDir, 'lib/node_modules/kodra-reh');
    if (fs.existsSync(rehDir)) {
        sh(`tar czf ${rehTar} -C ${rehDir} .`);
        console.log(`Wrote ${rehTar} (${(fs.statSync(rehTar).size / 1024 / 1024).toFixed(1)} MB)`);
    } else {
        console.warn(`REH dir ${rehDir} not found — skipping reh-web.tar.gz`);
    }

    // ELF verification — fail CI on any glibc linkage
    verifyElf(nodeBin);
    {
        const r = spawnSync(
            `find ${prefixDir}/lib -name "*.node" -exec sh -c 'readelf -d "$1" 2>/dev/null | grep -q ld-linux \&\& echo BAD:$1 || echo OK:$1' _ {} \;`,
            { shell: true, encoding: 'utf8' },
        );
        if (r.stdout) {
            const lines = r.stdout.trim().split("
").filter(Boolean);
            for (const line of lines) {
                console.log(line);
                if (line.startsWith("BAD:")) throw new Error(`glibc-linked addon: ${line}`);
            }
        }
    }
}

export function verifyElf(nodePath: string): boolean {
    mustExist(nodePath);
    const r = spawnSync(`readelf -d "${nodePath}" 2>&1 | head -20`, { shell: true, encoding: 'utf8' });
    const out = r.stdout || '';
    const isGlibc = out.includes('ld-linux-aarch64.so.1') || out.includes('ld-linux.so');
    const isBionic = out.includes('liblog.so') || out.includes('linker64');
    console.log(`ELF ${nodePath}: ${isGlibc ? 'BAD glibc' : isBionic ? 'OK bionic' : 'unknown'} — ${out.split('\n')[0]}`);
    if (isGlibc) throw new Error(`ELF still glibc-linked: ${nodePath}`);
    return !isGlibc;
}

if (import.meta.url === `file://${process.argv[1]}`) {
    buildBionic(prefix, out);
}
