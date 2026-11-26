package dev.unalivin.kodra

import android.content.Context
import android.system.Os
import java.io.File

/**
 * Extracts assets/bootstrap.zip (Termux-style $PREFIX) + assets/reh-web.tar.gz
 * on first launch or when versionCode changes. Idempotent: skips if marker matches.
 *
 * Layout (see kodra/prefix-layout.json):
 *   $PREFIX = /data/data/dev.unalivin.kodra/files/usr
 *   $HOME   = /data/data/dev.unalivin.kodra/files/home
 *   shared  = /storage/emulated/0/Kodra (SAF bridge, outside prefix)
 */
object BootstrapInstaller {
    private const val MARKER = "bootstrap/version"
    private const val PREFIX = "usr" // filesDir/usr

    data class Layout(val prefix: File, val home: File, val tmp: File)

    fun layout(context: Context): Layout {
        val prefix = File(context.filesDir, PREFIX)
        val home = File(context.filesDir, "home").also { it.mkdirs() }
        val tmp = File(prefix, "tmp").also { it.mkdirs() }
        return Layout(prefix, home, tmp)
    }

    /** Returns true if extraction is needed */
    fun needsInstall(context: Context): Boolean {
        val marker = File(context.filesDir, MARKER)
        val expected = BuildConfig.VERSION_NAME
        return !marker.exists() || marker.readText().trim() != expected
    }

    /** Extract + chmod — must be called off UI thread (Service.onCreate background). */
    fun installIfNeeded(context: Context): Layout {
        val l = layout(context)
        if (!needsInstall(context)) return l
        // Real extraction is NDK/asset wiring (Stage 3 next):
        // AssetManager.open("bootstrap.zip") → unzip to l.prefix
        // AssetManager.open("reh-web.tar.gz") → untar to l.prefix/lib/node_modules/kodra-reh
        // For scaffold: create minimal dirs and marker so service can start even without assets
        l.prefix.mkdirs()
        File(l.prefix, "bin").mkdirs()
        File(l.prefix, "etc").mkdirs()
        File(l.prefix, "tmp").mkdirs()
        File(l.home, "storage").let { link ->
            // ~/storage → /storage/emulated/0 (if permitted, else SAF)
            if (!link.exists()) runCatching {
                Os.symlink("/storage/emulated/0", link.absolutePath)
            }
        }
        // Write marker
        File(context.filesDir, MARKER).apply {
            parentFile?.mkdirs()
            writeText(BuildConfig.VERSION_NAME)
        }
        return l
    }

    fun busyBoxEnv(layout: Layout): Array<String> = arrayOf(
        "HOME=${layout.home.absolutePath}",
        "PREFIX=${layout.prefix.absolutePath}",
        "TERM=xterm-256color",
        "TMPDIR=${layout.tmp.absolutePath}",
        "LD_LIBRARY_PATH=${layout.prefix.absolutePath}/lib",
        "PATH=${layout.prefix.absolutePath}/bin:${System.getenv("PATH")}"
    )
}
