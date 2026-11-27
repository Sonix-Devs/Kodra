package dev.unalivin.kodra

import android.content.Context
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

/**
 * Extracts assets/bootstrap.zip (Termux-style $PREFIX) + assets/reh-web.tar.gz
 * on first launch or when versionCode changes. Idempotent: skips if marker matches.
 *
 * Layout (see kodra/prefix-layout.json):
 *   $PREFIX = /data/data/dev.unalivin.kodra/files/usr
 *   $HOME   = /data/data/dev.unalivin.kodra/files/home
 *   shared  = /storage/emulated/0/Kodra (SAF bridge, outside prefix)
 *
 * Extraction uses only Android-available APIs: ZipInputStream + manual tar parsing
 * (no `tar` binary dependency — that binary won't exist until bootstrap is installed).
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

    fun needsInstall(context: Context): Boolean {
        val marker = File(context.filesDir, MARKER)
        val expected = BuildConfig.VERSION_NAME
        return !marker.exists() || marker.readText().trim() != expected
    }

    /** Extract + chmod — must be called off UI thread (Service.onCreate background). */
    fun installIfNeeded(context: Context): Layout {
        val layout = layout(context)
        if (!needsInstall(context)) return layout
        val tmpPrefix = File(context.filesDir, "usr.tmp").apply { deleteRecursively(); mkdirs() }
        var success = false
        try {
            extractBootstrapZip(context, tmpPrefix)
            extractRehTar(context, tmpPrefix)
            chmodBinaries(tmpPrefix)
            createHomeSymlink(layout.home)
            layout.prefix.deleteRecursively()
            if (!tmpPrefix.renameTo(layout.prefix)) {
                tmpPrefix.copyRecursively(layout.prefix, overwrite = true)
                tmpPrefix.deleteRecursively()
            }
            File(context.filesDir, MARKER).apply {
                parentFile?.mkdirs()
                writeText(BuildConfig.VERSION_NAME)
            }
            success = true
            return layout
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
                        // Zip-slip guard
                        val outFile = File(dest, entry.name)
                        require(outFile.canonicalPath.startsWith(dest.canonicalPath + File.separator)) {
                            "Zip entry outside dest: ${entry.name}"
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                            if (entry.name.startsWith("bin/")) outFile.setExecutable(true, false)
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (_: java.io.FileNotFoundException) {
            File(dest, "bin").mkdirs()
            File(dest, "etc").mkdirs()
            File(dest, "tmp").mkdirs()
        }
    }

    private fun extractRehTar(context: Context, dest: File) {
        try {
            context.assets.open("reh-web.tar.gz").use { input ->
                val tarDest = File(dest, "lib/node_modules/kodra-reh").apply { mkdirs() }
                GZIPInputStream(input).use { gz ->
                    extractTar(gz, tarDest)
                }
            }
        } catch (_: java.io.FileNotFoundException) {
            // Same fallback as zip — REH not yet bundled
        } catch (_: Exception) {
            // Corrupt tar — don't crash service, just keep minimal layout
        }
    }

    /**
     * Minimal POSIX tar (ustar) extractor — reads 512-byte headers, extracts
     * regular files and directories. Sufficient for REH tarball; no need for
     * pax headers, sparse files, or long-name GNU extensions in this context.
     */
    @Suppress("SameParameterValue")
    private fun extractTar(input: java.io.InputStream, dest: File) {
        val header = ByteArray(512)
        val destCanonical = dest.canonicalPath + File.separator
        while (true) {
            var read = 0
            while (read < 512) {
                val n = input.read(header, read, 512 - read)
                if (n == -1) return
                read += n
            }
            if (header.all { it == 0.toByte() }) {
                // Two zero blocks = end of archive; consume second if present
                val next = ByteArray(512)
                var r2 = 0
                while (r2 < 512) {
                    val n = input.read(next, r2, 512 - r2)
                    if (n == -1) return
                    r2 += n
                }
                if (next.all { it == 0.toByte() }) return
                // Single zero was padding inside — not expected, but don't loop forever
                return
            }
            val name = readTarString(header, 0, 100)
            if (name.isEmpty()) return
            val sizeStr = readTarString(header, 124, 12).trim().trimEnd('\u0000')
            val size = if (sizeStr.isEmpty()) 0L else sizeStr.trim().toLongOrNull(8) ?: 0L
            val typeFlag = header[156].toInt().toChar()

            val outFile = File(dest, name)
            require(outFile.canonicalPath.startsWith(destCanonical) || outFile.canonicalPath == dest.canonicalPath) {
                "Tar entry outside dest: $name"
            }

            when (typeFlag) {
                '5' -> outFile.mkdirs()
                '0', '\u0000' -> {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out ->
                        var remaining = size
                        val buf = ByteArray(8192)
                        while (remaining > 0) {
                            val toRead = minOf(buf.size.toLong(), remaining).toInt()
                            val n = input.read(buf, 0, toRead)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            remaining -= n
                        }
                    }
                    // Skip padding to next 512 boundary
                    val pad = (512 - (size % 512)) % 512
                    if (pad > 0) {
                        var skipped = 0L
                        while (skipped < pad) {
                            val n = input.read(header, 0, minOf(512, (pad - skipped).toInt()).coerceAtLeast(1))
                            if (n == -1) break
                            skipped += n
                        }
                    }
                    continue // already consumed padding
                }
                else -> {
                    // Unknown type — skip its data + padding
                    var remaining = size
                    val skip = ByteArray(8192)
                    while (remaining > 0) {
                        val n = input.read(skip, 0, minOf(skip.size.toLong(), remaining).toInt())
                        if (n == -1) break
                        remaining -= n
                    }
                }
            }
            // Directory entries have size 0 but still have no data to skip
            if (typeFlag == '5' || size == 0L) continue
            // For regular files we already handled padding; for other cases compute it
            if (typeFlag != '0' && typeFlag != '\u0000') {
                val pad = (512 - (size % 512)) % 512
                var skipped = 0L
                while (skipped < pad) {
                    val n = input.read(header, 0, minOf(512, (pad - skipped).toInt()).coerceAtLeast(1))
                    if (n == -1) break
                    skipped += n
                }
            }
        }
    }

    private fun readTarString(buf: ByteArray, offset: Int, len: Int): String {
        var end = offset
        val limit = minOf(offset + len, buf.size)
        while (end < limit && buf[end] != 0.toByte()) end++
        return String(buf, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun chmodBinaries(prefix: File) {
        val binDir = File(prefix, "bin")
        if (!binDir.exists()) return
        binDir.listFiles()?.forEach { f ->
            if (f.isFile) f.setExecutable(true, false)
        }
        File(prefix, "bin/node").takeIf { it.exists() }?.setExecutable(true, false)
    }

    private fun createHomeSymlink(home: File) {
        File(home, "storage").let { link ->
            if (!link.exists()) {
                runCatching { Os.symlink("/storage/emulated/0", link.absolutePath) }
            }
        }
        File(home, ".bashrc").takeIf { !it.exists() }?.writeText("export PS1='kodra:\\\\w\\\\$ '\n")
    }

    fun busyBoxEnv(layout: Layout): Array<String> = arrayOf(
        "HOME=${layout.home.absolutePath}",
        "PREFIX=${layout.prefix.absolutePath}",
        "TERM=xterm-256color",
        "TMPDIR=${layout.tmp.absolutePath}",
        "LD_LIBRARY_PATH=${layout.prefix.absolutePath}/lib",
        "PATH=${layout.prefix.absolutePath}/bin:${System.getenv("PATH")}",
    )
}
