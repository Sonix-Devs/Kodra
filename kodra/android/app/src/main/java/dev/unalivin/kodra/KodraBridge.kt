package dev.unalivin.kodra

import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * Narrow, purpose-specific bridge for Stage 6+. NOT exposed as addJavascriptInterface
 * in Stage 3 scaffold — this stub documents the allowed surface and will be wired
 * via WebMessagePort once the workbench extension host handshake is proven.
 *
 * Allowed: clipboard read/write (ClipboardManager), openFile/openFolder (SAF),
 * auth (Keystore Device Flow), notification controls, insets/IME hints.
 * Forbidden: generic exec, file-system raw access, or privileged JS interface.
 */
object KodraBridgeWhiteList {
    const val CHANNEL = "kodra"
    val allowedMethods = setOf(
        "clipboard.readText", "clipboard.writeText",
        "dialog.openFile", "dialog.openFolder",
        "auth.githubDeviceFlow", "auth.getToken", "auth.clearToken",
        "service.stop", "service.reconnect",
        "insets.get", "insets.setImmersive"
    )
}

// Real bridge will be:
// webView.addWebMessageListener("kodra", setOf("https://127.0.0.1:*"), KodraWebMessageListener())
// For now, keep surface closed — MainActivity's shouldOverrideUrlLoading is the only bridge.
