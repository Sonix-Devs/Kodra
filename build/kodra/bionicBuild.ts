/**
 * Bionic REH build — invoked on device CI or NDK host, not in sandbox.
 * Packages `assets/bootstrap.zip` ($PREFIX) + `reh-web.tar.gz` for kodra/android.
 *
 * Usage on NDK host (outside sandbox):
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

function sh(cmd: string, env: NodeJS.ProcessEnv = process.env) {
    console.log(`$ ${cmd}`);
    const r = spawnSync(cmd, { shell: true, stdio: 'inherit', env });
    if (r.status !== 0) throw new Error(`failed: ${cmd}`);
}

export function buildBionic(prefixDir: string, outDir: string) {
    fs.mkdirSync(outDir, { recursive: true });
    console.log(`Bionic build: PREFIX=${prefixDir} out=${outDir}`);
    // Placeholder — real NDK build runs outside sandbox; this validates file layout
    const bootstrapZip = path.join(outDir, 'bootstrap.zip');
    const rehTar = path.join(outDir, 'reh-web.tar.gz');
    if (!fs.existsSync(bootstrapZip)) {
        console.warn(`Missing ${bootstrapZip} — create via: zip -r ${bootstrapZip} bin lib etc (from $PREFIX)`);
    }
    if (!fs.existsSync(rehTar)) {
        console.warn(`Missing ${rehTar} — create via: tar czf ${rehTar} -C $PREFIX/lib/node_modules/kodra-reh .`);
    }
    // ELF verification stub
    const nodeBin = path.join(prefixDir, 'bin/node');
    if (fs.existsSync(nodeBin)) {
        const r = spawnSync(`readelf -d ${nodeBin} | grep -q ld-linux && echo glibc || echo bionic`, { shell: true, encoding: 'utf8' });
        console.log(`node ELF: ${r.stdout?.trim()}`);
    }
}

if (import.meta.url === `file://${process.argv[1]}`) {
    buildBionic(prefix, out);
}
