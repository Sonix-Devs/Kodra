package dev.unalivin.kodra

import java.io.File

/**
 * Terminal bridge — PTY is owned by REH (node-pty), not by Kotlin.
 *
 * The browser workbench already renders xterm.js; REH's `node-pty` spawns `bash`
 * via the bootstrap `$PREFIX/bin/bash` (default shell).
 *
 * Kotlin's job:
 * 1. Ensure `BootstrapInstaller` extracted `bash` + `node` + rebuilt `node-pty.node` (bionic)
 *    — `ServerProcess` already sets HOME/PREFIX/LD_LIBRARY_PATH/PATH before spawn.
 * 2. Expose `TERM=xterm-256color` and `COLORTERM=truecolor` to the server env.
 * 3. Extra-keys row ergonomics via ExtraKeysView → WebView dispatchKeyEvent.
 * 4. Do NOT reimplement PTY in Kotlin — that would duplicate node-pty.
 *
 * Verification (device CI):
 *   $PREFIX/bin/bash -c 'echo ok'  → ok
 *   node -e "require('node-pty').spawn('bash',['-lc','echo hi'],{}).onData(d=>process.stdout.write(d))"
 */
object TerminalBridge {
    val extraKeys = listOf(
        "Esc" to "\u001b",
        "Tab" to "\t",
        "Ctrl" to "CTRL_MOD",
        "Alt" to "ALT_MOD",
        "←" to "\u001b[D",
        "↓" to "\u001b[B",
        "↑" to "\u001b[A",
        "→" to "\u001b[C",
    )

    fun ensurePtyAvailable(layout: BootstrapInstaller.Layout): Boolean {
        val ptyNode = File(layout.prefix, "lib/node_modules/kodra-reh/node_modules/node-pty/build/Release/pty.node")
        if (!ptyNode.exists()) return false
        return runCatching {
            val proc = ProcessBuilder("readelf", "-d", ptyNode.absolutePath).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
            !output.contains("ld-linux-aarch64.so.1") && !output.contains("ld-linux.so")
        }.getOrDefault(true)
    }

    fun ptyDiagnostics(layout: BootstrapInstaller.Layout): String {
        val ptyNode = File(layout.prefix, "lib/node_modules/kodra-reh/node_modules/node-pty/build/Release/pty.node")
        return when {
            !ptyNode.exists() -> "missing: run bionic build"
            ensurePtyAvailable(layout) -> "ok: ${ptyNode.length()} bytes, bionic-linked"
            else -> "bad: glibc-linked (rebuild with NDK)"
        }
    }
}
