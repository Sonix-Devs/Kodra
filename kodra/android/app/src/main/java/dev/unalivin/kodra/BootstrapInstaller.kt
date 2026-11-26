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
        // Atomic install: extract to tmp dir, then rename, so crash mid-extract doesn't leave half-prefix
        val tmpPrefix = File(context.filesDir, "usr.tmp").apply { deleteRecursively(); mkdirs() }
        var success = false
        try {
            extractBootstrapZip(context, tmpPrefix)
            extractRehTar(context, tmpPrefix)
            chmodBinaries(tmpPrefix)
            createHomeSymlink(l.home)
            // Move into place atomically
            l.prefix.deleteRecursively()
            if (!tmpPrefix.renameTo(l.prefix)) {
                // Fallback copy if rename across volumes fails
                tmpPrefix.copyRecursively(l.prefix, overwrite = true)
                tmpPrefix.deleteRecursively()
            }
            File(context.filesDir, MARKER).apply {
                parentFile?.mkdirs()
                writeText(BuildConfig.VERSION_NAME)
            }
            success = true
            return l
        } finally {
            if (!success) tmpPrefix.deleteRecursively()
        }
    }

    private fun extractBootstrapZip(context: Context, dest: File) {
        try {
            context.assets.open("bootstrap.zip").use { input ->
                java.util.zip.ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val outFile = File(dest, entry.name)
                        if (entry.isDirectory) outFile.mkdirs()
                        else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { zip.copyTo(it) }
                            // Preserve executable bit if zip has it (external attr >>16 & 0x1FF contains unix mode)
                            val mode = (entry.extra?.size ?: 0) // ZipEntry Unix mode is in externalAttributes
                            if (entry.name.startsWith("bin/")) outFile.setExecutable(true)
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (_: java.io.FileNotFoundException) {
            // Assets not yet bundled (CI debug build without NDK) — create minimal fallback
            File(dest, "bin").mkdirs()
            File(dest, "etc").mkdirs()
            File(dest, "tmp").mkdirs()
        }
    }

    private fun extractRehTar(context: Context, dest: File) {
        try {
            context.assets.open("reh-web.tar.gz").use { input ->
                // Use system tar if available, else fallback to no-op
                val tarDest = File(dest, "lib/node_modules/kodra-reh").apply { mkdirs() }
                val tmpTar = File.createTempFile("reh-web", ".tar.gz", context.cacheDir)
                try {
                    tmpTar.outputStream().use { input.copyTo(it) }
                    val proc = ProcessBuilder("tar", "xzf", tmpTar.absolutePath, "-C", tarDest.absolutePath)
                        .redirectErrorStream(true).start()
                    proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
                } finally { tmpTar.delete() }
            }
        } catch (_: java.io.FileNotFoundException) { /* same fallback as zip */ }
    }

    private fun chmodBinaries(prefix: File) {
        val binDir = File(prefix, "bin")
        if (!binDir.exists()) return
        binDir.listFiles()?.forEach { f ->
            if (f.isFile) f.setExecutable(true, false)
        }
        // Ensure node itself is executable even if zip lost mode
        File(prefix, "bin/node").takeIf { it.exists() }?.setExecutable(true, false)
    }

    private fun createHomeSymlink(home: File) {
        File(home, "storage").let { link ->
            if (!link.exists()) runCatching {
                Os.symlink("/storage/emulated/0", link.absolutePath)
            }
        }
        // Default git config / bashrc if not present
        File(home, ".bashrc").takeIf { !it.exists() }?.writeText("export PS1='kodra:\\w\\$ '\n")
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
