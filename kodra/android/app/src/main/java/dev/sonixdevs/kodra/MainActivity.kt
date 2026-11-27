package dev.sonixdevs.kodra

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Single-Activity, full-screen WebView. No Electron — renders the browser workbench
 * served by the local REH-web server at http://127.0.0.1:<port>/?tkn=<token> (origin lock).
 *
 * - Edge-to-edge + WindowInsetsControllerCompat transientBarsBySwipe
 * - WebViewClient allowlist: 127.0.0.1 only; other hosts → Intent
 * - System file picker for Open File/Folder (SAF) via FilePickerBridge
 * - Hardware Back → WebView.goBack() if canGoBack (workbench handles history)
 */
class MainActivity : AppCompatActivity() {
    private var webView: WebView? = null
    private var service: KodraService? = null
    private var pendingLoad: Boolean = false

    private lateinit var filePicker: FilePickerBridge
    private lateinit var workbenchBridge: WorkbenchBridge
    private var extraKeysView: ExtraKeysView? = null

    fun bridgeView(): android.view.View = webView ?: window.decorView

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as KodraService.LocalBinder).service()
            maybeLoad()
        }
        override fun onServiceDisconnected(name: ComponentName?) { service = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        filePicker = FilePickerBridge(this)
        workbenchBridge = WorkbenchBridge(this, filePicker)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            }
            if (BuildConfig.ENABLE_WEBVIEW_DEBUGGING) WebView.setWebContentsDebuggingEnabled(true)
            webViewClient = KodraWebClient()
        }
        webView = web
        extraKeysView = ExtraKeysView(this).also { it.attach(web) }

        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(extraKeysView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.BOTTOM
            })
        }
        setContentView(container)
        InsetsBridge.apply(this, web)

        // Wire WebMessagePort (API 33+; fallback to no-op on older)
        runCatching {
            web.addWebMessageListener("kodra", setOf("https://127.0.0.1:*"), workbenchBridge.listener())
        }

        // Start service (or bind to existing)
        KodraService.start(this)
        bindService(Intent(this, KodraService::class.java), serviceConn, Context.BIND_AUTO_CREATE)

        // Retry load until service is ready (server takes ~1-3s)
        lifecycleScope.launch {
            repeat(30) {
                delay(800)
                if (webView?.url == null) maybeLoad()
            }
        }
    }

    private fun maybeLoad() {
        val svc = service ?: return
        val b = svc.bound() ?: return
        if (webView?.url != null || pendingLoad) return
        pendingLoad = true
        val token = TokenManager.readToken(this) ?: return
        val url = "http://${b.host}:${b.port}/?tkn=${Uri.encode(token)}"
        webView?.loadUrl(url)
    }

    override fun onDestroy() {
        try { unbindService(serviceConn) } catch (_: Exception) {}
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (webView?.canGoBack() == true && keyCode == KeyEvent.KEYCODE_BACK) {
            webView?.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    inner class KodraWebClient : WebViewClient() {
        @SuppressLint("QueryPermissionsNeeded")
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            if (url.host == "127.0.0.1") return false
            if (url.scheme == "blob" || url.scheme == "data") return false
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                Toast.makeText(this@MainActivity, getString(R.string.err_no_browser, url), Toast.LENGTH_SHORT).show()
            }
            return true
        }
    }
}
