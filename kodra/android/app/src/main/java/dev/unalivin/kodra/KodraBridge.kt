package dev.unalivin.kodra

import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * Narrow, purpose-specific bridge — exposed via WebView.addWebMessageListener
 * (MainActivity, API 33+). Validates origin (127.0.0.1:*), allowlists methods,
 * and redacts tokens before reply.
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

// Wired in MainActivity.onCreate via WorkbenchBridge.listener()
