package dev.unalivin.kodra

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.webkit.WebView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton

/**
 * Extra-keys row (Esc, Tab, Ctrl, Alt, arrows) above the soft keyboard.
 *
 * This view is inflated as an overlay above `MainActivity`'s WebView and sends
 * synthetic `KeyEvent`s to the WebView's `dispatchKeyEvent`. Ctrl/Alt are sticky
 * modifiers (toggle) — next key is sent with META_CTRL_ON / META_ALT_ON.
 */
class ExtraKeysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs) {

    private var ctrlHeld = false
    private var altHeld = false
    private var webView: WebView? = null

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
    }

    init {
        isHorizontalScrollBarEnabled = false
        addView(row)
        addKey("Esc", KeyEvent.KEYCODE_ESCAPE)
        addKey("Tab", KeyEvent.KEYCODE_TAB)
        addToggle("Ctrl") { ctrlHeld = !ctrlHeld; it.isSelected = ctrlHeld }
        addToggle("Alt") { altHeld = !altHeld; it.isSelected = altHeld }
        addKey("←", KeyEvent.KEYCODE_DPAD_LEFT)
        addKey("↓", KeyEvent.KEYCODE_DPAD_DOWN)
        addKey("↑", KeyEvent.KEYCODE_DPAD_UP)
        addKey("→", KeyEvent.KEYCODE_DPAD_RIGHT)
        addKey("Home", KeyEvent.KEYCODE_MOVE_HOME)
        addKey("End", KeyEvent.KEYCODE_MOVE_END)
        addKey("PgUp", KeyEvent.KEYCODE_PAGE_UP)
        addKey("PgDn", KeyEvent.KEYCODE_PAGE_DOWN)
    }

    fun attach(webView: WebView) { this.webView = webView }

    private fun addKey(label: String, keyCode: Int) {
        row.addView(
            MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = label
                setOnClickListener {
                    sendKey(keyCode)
                    if (ctrlHeld || altHeld) { ctrlHeld = false; altHeld = false; updateToggles() }
                }
            },
        )
    }

    private fun addToggle(label: String, onClick: (MaterialButton) -> Unit) {
        row.addView(
            MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = label
                setOnClickListener { onClick(this) }
            },
        )
    }

    private fun updateToggles() {
        for (i in 0 until row.childCount) (row.getChildAt(i) as? MaterialButton)?.let {
            if (it.text == "Ctrl") it.isSelected = ctrlHeld
            if (it.text == "Alt") it.isSelected = altHeld
        }
    }

    private fun sendKey(keyCode: Int) {
        val wv = webView ?: return
        wv.requestFocus()
        val meta = (if (ctrlHeld) KeyEvent.META_CTRL_ON else 0) or
            (if (altHeld) KeyEvent.META_ALT_ON else 0)
        val down = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, meta)
        val up = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, meta)
        wv.dispatchKeyEvent(down)
        wv.dispatchKeyEvent(up)
    }
}
