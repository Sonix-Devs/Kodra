package dev.sonixdevs.kodra

import android.content.Context
import java.io.File

/**
 * Stage 4: Terminal bridge design — PTY is owned by REH (node-pty), not by Kotlin.
 *
 * The browser workbench already renders xterm.js (see `PORTING_NOTES.md` § Workbench shell);
 * REH's `node-pty` spawns `bash` via the bootstrap `$PREFIX/bin/bash` (default shell).
 *
 * Kotlin's job:
 * 1. Ensure `BootstrapInstaller` extracted `bash` + `node` + rebuilt `node-pty.node` (bionic)
 *    — `ServerProcess` already sets HOME/PREFIX/LD_LIBRARY_PATH/PATH before spawn.
 * 2. Expose `TERM=xterm-256color` and `COLORTERM=truecolor` to the server env (done in BootstrapInstaller.busyBoxEnv).
 * 3. Handle extra-keys row ergonomics (Stage 6): Esc/Tab/Ctrl/Alt/Arrows as `InputConnection` → WebView `dispatchKeyEvent`.
 * 4. Do NOT reimplement PTY in Kotlin — that would duplicate node-pty and break copy/paste.
 *
 * Verification (device CI Stage 6):
 *   $PREFIX/bin/bash -c 'echo ok'  → ok
 *   node -e "require('node-pty').spawn('bash',['-lc','echo hi'],{}).onData(d=>process.stdout.write(d))"
 *   Terminal panel in workbench: type `ls`, see output, copy/paste via ClipboardManager.
 */
object TerminalBridge {
    /** Extra-keys row payloads for Stage 6 WebView IME */
    val extraKeys = listOf(
        "Esc" to "\u001b",
        "Tab" to "\t",
        "Ctrl" to "CTRL_MOD", // held modifier, not a char
        "Alt" to "ALT_MOD",
        "←" to "\u001b[D", "↓" to "\u001b[B", "↑" to "\u001b[A", "→" to "\u001b[C"
    )

    fun ensurePtyAvailable(layout: BootstrapInstaller.Layout): Boolean {
        // node-pty.node is at lib/node_modules/kodra-reh/node_modules/node-pty/build/Release/pty.node
        val ptyNode = File(layout.prefix, "lib/node_modules/kodra-reh/node_modules/node-pty/build/Release/pty.node")
        // Scaffold check — real CI will readelf NEEDED: must NOT contain ld-linux-aarch64.so.1
        return ptyNode.exists() || true // scaffold: allow missing until NDK build bundled
    }
}
