package dev.unalivin.kodra

import android.app.Application
import android.os.Build

/** App-scoped init — notification channel, strict-mode in debug, etc. */
class KodraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        // Keep WebView init off main-thread if needed; defer to MainActivity.
        if (BuildConfig.DEBUG) {
            // Enable StrictMode for file-URI leaks during dev only.
            // Keep disabled in release (false positive on WebView).
        }
    }
}
