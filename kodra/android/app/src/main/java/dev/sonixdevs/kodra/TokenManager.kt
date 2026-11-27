package dev.sonixdevs.kodra

import android.content.Context
import java.io.File
import java.security.SecureRandom
import android.util.Base64

/**
 * Per-launch token for the loopback server. Stored at filesDir/token (0600, MODE_PRIVATE)
 * and passed via --connection-token-file. Never logged; ServerProcess redacts ?tkn=.
 */
object TokenManager {
    private const val TOKEN_FILE = "token"
    private const val TOKEN_BYTES = 32

    fun generate(): String {
        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        // URL-safe base64 without padding — matches serverConnectionToken.ts expectations
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun writeToken(context: Context, token: String): File {
        val f = File(context.filesDir, TOKEN_FILE)
        f.writeText(token, Charsets.UTF_8)
        // MODE_PRIVATE already 0600; ensure no world-readable bit on older devices
        @Suppress("DEPRECATION") f.setReadable(false, true)
        f.setReadable(true, true)
        f.setWritable(true, true)
        return f
    }

    fun readToken(context: Context): String? =
        File(context.filesDir, TOKEN_FILE).takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }

    /** Redact token from any log line that contains ?tkn= */
    fun redact(line: String): String =
        line.replace(Regex("[?&]tkn=[^\\s&\"']+"), "?tkn=***")
}
