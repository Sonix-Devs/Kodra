package dev.unalivin.kodra

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Clipboard bridge — narrow surface: workbench calls `clipboard.readText` / `writeText`
 * via WebMessagePort, which delegates here. No `addJavascriptInterface` exposure.
 *
 * Security: only plain text, no HTML; read requires user gesture (workbench menu / Ctrl+V).
 */
object ClipboardBridge {
    fun readText(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return null
        return if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(context).toString() else null
    }

    fun writeText(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Kodra", text))
    }
}
