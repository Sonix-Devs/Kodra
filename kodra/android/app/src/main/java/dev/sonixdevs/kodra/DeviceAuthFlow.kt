package dev.sonixdevs.kodra

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * GitHub Device Flow — completes TODO in GithubAuth.kt.
 * Network calls are plain HttpURLConnection (no extra dep) and are testable via
 * GithubAuthTest (not in this sandbox). The UI shows user_code + opens verification_uri.
 */
object DeviceAuthFlow {
    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String?,
        val interval: Long,
        val expiresIn: Long
    )
    data class TokenResponse(val accessToken: String, val tokenType: String, val scope: String)

    fun requestDeviceCode(clientId: String): DeviceCode {
        val body = "client_id=${enc(clientId)}&scope=${enc("repo read:user")}"
        val json = post("https://github.com/login/device/code", body, "application/json")
        val o = JSONObject(json)
        return DeviceCode(
            o.getString("device_code"), o.getString("user_code"),
            o.getString("verification_uri"), o.optString("verification_uri_complete", null),
            o.optLong("interval", 5), o.optLong("expires_in", 900)
        )
    }

    suspend fun pollForToken(clientId: String, deviceCode: String, intervalSec: Long, expiresInSec: Long): TokenResponse {
        val deadline = System.currentTimeMillis() + expiresInSec * 1000
        val bodyBase = "client_id=${enc(clientId)}&device_code=${enc(deviceCode)}&grant_type=${enc("urn:ietf:params:oauth:grant-type:device_code")}"
        while (System.currentTimeMillis() < deadline) {
            delay(intervalSec * 1000)
            val json = post("https://github.com/login/oauth/access_token", bodyBase, "application/json")
            val o = JSONObject(json)
            if (o.has("access_token")) {
                return TokenResponse(o.getString("access_token"), o.optString("token_type","bearer"), o.optString("scope",""))
            }
            val err = o.optString("error","")
            if (err == "authorization_pending") continue
            if (err == "slow_down") { delay(5000); continue }
            if (err == "expired_token" || err == "unsupported_grant_type") throw IllegalStateException("Device flow $err")
            if (err.isNotEmpty()) throw IllegalStateException("Device flow error: $err - $json")
        }
        throw IllegalStateException("Device flow timed out")
    }

    fun openVerification(context: Context, deviceCode: DeviceCode) {
        val uri = deviceCode.verificationUriComplete?.let { Uri.parse(it) } ?: Uri.parse(deviceCode.verificationUri)
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun post(url: String, body: String, accept: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Accept", accept)
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        return stream.bufferedReader().readText()
    }
}
