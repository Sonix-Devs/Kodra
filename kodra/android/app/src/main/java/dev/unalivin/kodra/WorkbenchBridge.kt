package dev.unalivin.kodra

import android.webkit.JavaScriptReplyProxy
import android.webkit.WebMessageListener
import org.json.JSONObject

/**
 * WebMessagePort bridge — replaces addJavascriptInterface. Workbench calls
 * `window.kodra.postMessage({method, params})` and we reply via `JsReplyProxy`.
 * Only allowlisted methods in KodraBridgeWhiteList.
 */
class WorkbenchBridge(
    private val activity: MainActivity,
    private val filePicker: FilePickerBridge,
) {
    fun listener(): WebMessageListener = WebMessageListener { _, message, _, replyProxy, _ ->
        handle(message, replyProxy)
    }

    private fun handle(message: String, reply: JavaScriptReplyProxy) {
        runCatching {
            val req = JSONObject(message)
            val method = req.optString("method", "")
            val id = req.optString("id", "")
            if (method !in KodraBridgeWhiteList.allowedMethods) {
                reply("{\"id\":\"$id\",\"error\":\"forbidden method\"}", reply)
                return
            }
            when (method) {
                "clipboard.readText" -> {
                    val t = ClipboardBridge.readText(activity) ?: ""
                    reply("{\"id\":\"$id\",\"result\":${JSONObject.quote(t)}}", reply)
                }
                "clipboard.writeText" -> {
                    val text = req.optString("params", "")
                    ClipboardBridge.writeText(activity, text)
                    reply("{\"id\":\"$id\",\"result\":true}", reply)
                }
                "dialog.openFile" -> {
                    filePicker.pickFile { res ->
                        res.onSuccess { reply("{\"id\":\"$id\",\"result\":${JSONObject.quote(it)}}", reply) }
                            .onFailure { reply("{\"id\":\"$id\",\"error\":${JSONObject.quote(it.message ?: "")}}}", reply) }
                    }
                }
                "dialog.openFolder" -> {
                    filePicker.pickFolder { res ->
                        res.onSuccess { reply("{\"id\":\"$id\",\"result\":${JSONObject.quote(it)}}", reply) }
                            .onFailure { reply("{\"id\":\"$id\",\"error\":${JSONObject.quote(it.message ?: "")}}}", reply) }
                    }
                }
                "auth.githubDeviceFlow" -> {
                    val clientId = GithubAuth.getClientId(activity)
                    if (clientId == null) {
                        reply("{\"id\":\"$id\",\"error\":\"no client_id\"}", reply)
                        return
                    }
                    reply("{\"id\":\"$id\",\"result\":\"device_flow_started\"}", reply)
                }
                "auth.getToken" -> {
                    val tok = GithubAuth.loadToken(activity)
                    reply("{\"id\":\"$id\",\"result\":${if (tok != null) JSONObject.quote("***") else "null"}}", reply)
                }
                "auth.clearToken" -> {
                    GithubAuth.clearToken(activity)
                    reply("{\"id\":\"$id\",\"result\":true}", reply)
                }
                "service.stop" -> {
                    activity.stopService(android.content.Intent(activity, KodraService::class.java))
                    reply("{\"id\":\"$id\",\"result\":true}", reply)
                }
                "service.reconnect" -> {
                    KodraService.start(activity)
                    reply("{\"id\":\"$id\",\"result\":true}", reply)
                }
                "insets.get" -> reply("{\"id\":\"$id\",\"result\":true}", reply)
                "insets.setImmersive" -> {
                    val enabled = req.optJSONObject("params")?.optBoolean("enabled", true) ?: true
                    activity.runOnUiThread { InsetsBridge.setImmersive(activity, activity.webViewForBridge(), enabled) }
                    reply("{\"id\":\"$id\",\"result\":true}", reply)
                }
            }
        }.onFailure { e ->
            reply("{\"error\":${JSONObject.quote(e.message ?: "")}}}", reply)
        }
    }

    private fun reply(json: String, proxy: JavaScriptReplyProxy) {
        try {
            proxy.postMessage(json)
        } catch (_: Exception) {
        }
    }
}
