package com.smarttv.remote.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.smarttv.remote.model.RemoteCommand

/**
 * The full v1 control surface, built programmatically (no layout XML, no
 * androidx): status line, PIN entry (shown only while pairing), d-pad with
 * center select, then back/home and volume rows.
 */
class RemotePadScreen(
    private val context: Context,
    private val onCommand: (RemoteCommand) -> Unit,
    private val onPinSubmit: (String) -> Unit,
) {
    private lateinit var statusView: TextView
    private lateinit var pinRow: LinearLayout
    private lateinit var pinInput: EditText
    private lateinit var padContainer: LinearLayout

    fun createView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#101418"))
            setPadding(dp(24), dp(48), dp(24), dp(24))
        }

        statusView = TextView(context).apply {
            text = "Searching for SmartTV…"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        root.addView(statusView, matchWrap())

        pinRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        pinInput = EditText(context).apply {
            hint = "PIN on TV"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 22f
            typeface = Typeface.MONOSPACE
            minWidth = dp(140)
        }
        pinRow.addView(pinInput)
        pinRow.addView(padButton("Pair") { onPinSubmit(pinInput.text.toString().trim()) })
        root.addView(pinRow, matchWrap(topMargin = dp(16)))

        padContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            alpha = 0.4f // dimmed until paired
        }

        // D-pad: up / (left select right) / down
        padContainer.addView(rowOf(padButton("▲") { onCommand(RemoteCommand.Navigate("up")) }), wrapWrap(topMargin = dp(32)))
        padContainer.addView(
            rowOf(
                padButton("◀") { onCommand(RemoteCommand.Navigate("left")) },
                padButton("OK", big = true) { onCommand(RemoteCommand.Select) },
                padButton("▶") { onCommand(RemoteCommand.Navigate("right")) },
            ),
            wrapWrap()
        )
        padContainer.addView(rowOf(padButton("▼") { onCommand(RemoteCommand.Navigate("down")) }), wrapWrap())

        // Back / Home
        padContainer.addView(
            rowOf(
                padButton("Back") { onCommand(RemoteCommand.Back) },
                padButton("Home") { onCommand(RemoteCommand.Home) },
            ),
            wrapWrap(topMargin = dp(32))
        )

        // Volume
        padContainer.addView(
            rowOf(
                padButton("Vol −") { onCommand(RemoteCommand.Volume("down")) },
                padButton("Vol +") { onCommand(RemoteCommand.Volume("up")) },
            ),
            wrapWrap(topMargin = dp(12))
        )

        root.addView(padContainer, matchWrap())
        return root
    }

    // MARK: state updates (main thread)

    fun setStatus(text: String) {
        statusView.text = text
    }

    fun showPinEntry(show: Boolean) {
        pinRow.visibility = if (show) View.VISIBLE else View.GONE
        if (show) pinInput.text.clear()
    }

    fun setControlsEnabled(enabled: Boolean) {
        padContainer.alpha = if (enabled) 1.0f else 0.4f
    }

    // MARK: helpers

    private fun padButton(label: String, big: Boolean = false, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            textSize = if (big) 20f else 16f
            setOnClickListener { onClick() }
            val size = if (big) dp(96) else dp(80)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(dp(6), dp(6), dp(6), dp(6))
            }
        }
    }

    private fun rowOf(vararg views: View): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            views.forEach { addView(it) }
        }
    }

    private fun matchWrap(topMargin: Int = 0) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { this.topMargin = topMargin }

    private fun wrapWrap(topMargin: Int = 0) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { this.topMargin = topMargin }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
