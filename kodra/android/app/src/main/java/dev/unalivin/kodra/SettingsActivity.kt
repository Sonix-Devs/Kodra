package dev.unalivin.kodra

import android.os.Bundle
import android.widget.Switch
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Minimal settings — immersive toggle, battery optimization doc link, GitHub auth status.
 * Stage 6: full Settings UI ↔ settings.json round-trip is verified via docs/sample-settings.json
 */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("kodra_settings", MODE_PRIVATE)
        val immersive = prefs.getBoolean("immersive", true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        root.addView(TextView(this).apply { text = "Kodra Settings"; textSize = 20f })
        root.addView(Switch(this).apply {
            text = "Immersive (transient bars by swipe)"
            isChecked = immersive
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("immersive", checked).apply()
            }
        })
        root.addView(TextView(this).apply {
            text = "GitHub: ${if (GithubAuth.loadToken(this@SettingsActivity) != null) "signed in" else "not signed in"}"
        })
        root.addView(TextView(this).apply {
            text = "See docs/BATTERY.md for battery optimization steps"
            setOnClickListener {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/unalivin/kodra/blob/main/docs/BATTERY.md")))
            }
        })
        // Battery optimization intent
        root.addView(android.widget.Button(this).apply {
            text = "Request ignore battery optimizations"
            setOnClickListener {
                runCatching {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    })
                }
            }
        })
        setContentView(root)
    }
}
