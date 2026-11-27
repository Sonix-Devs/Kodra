package dev.unalivin.kodra

import android.content.Context
import java.io.File

/**
 * Git bridge — built-in Git extension stays, but its `git` binary is the bootstrap one.
 *
 * Workbench `extensions/git` discovers `git` via:
 *   `git.path` setting → else `$PATH` → else `which git`
 * Kodra sets:
 *   `git.path = $PREFIX/bin/git` (via default settings + user settings.json)
 *   `PATH = $PREFIX/bin:...` (BootstrapInstaller.busyBoxEnv)
 *
 * This Kotlin file installs the helper script and configures `git config` on first launch.
 */
object GitBridge {
    private const val HELPER_NAME = "kodra-credential-helper"

    fun install(context: Context, layout: BootstrapInstaller.Layout) {
        val bin = File(layout.prefix, "bin")
        val helper = File(bin, HELPER_NAME)
        helper.writeText(
            """
            #!/system/bin/sh
            # Kodra Git credential helper — called as: $HELPER_NAME get / store / erase
            # Token at ${context.filesDir.absolutePath}/github-token (written by GithubAuth.storeToken)
            if [ "${'$'}1" = "get" ]; then
              if [ -f "${context.filesDir.absolutePath}/github-token" ]; then
                echo "username=git"
                echo "password=$(cat "${context.filesDir.absolutePath}/github-token")"
              fi
            fi
            """.trimIndent(),
        )
        helper.setExecutable(true)

        val git = File(bin, "git")
        if (!git.exists()) return
        runCatching {
            ProcessBuilder(git.absolutePath, "config", "--global", "credential.helper", helper.absolutePath)
                .apply { environment()["HOME"] = layout.home.absolutePath }
                .start().waitFor()
        }
        runCatching {
            ProcessBuilder(git.absolutePath, "config", "--global", "--add", "safe.directory", "/storage/emulated/0/Kodra")
                .apply { environment()["HOME"] = layout.home.absolutePath }
                .start().waitFor()
        }
    }

    fun isAvailable(layout: BootstrapInstaller.Layout): Boolean =
        File(layout.prefix, "bin/git").exists()
}
