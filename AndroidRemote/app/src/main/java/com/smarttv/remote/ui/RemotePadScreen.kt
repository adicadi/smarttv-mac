package com.smarttv.remote.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.smarttv.remote.model.RemoteCommand

/**
 * The remote control surface, built programmatically (no layout XML, no
 * androidx): status line, PIN entry (shown only while pairing), d-pad with
 * center select, back/home and volume rows, a trackpad area (one finger
 * moves the cursor, tap clicks, two fingers scroll), a text/keyboard row
 * and a voice-input button.
 */
class RemotePadScreen(
    private val context: Context,
    private val onCommand: (RemoteCommand) -> Unit,
    private val onPinSubmit: (String) -> Unit,
    private val onVoiceRequest: () -> Unit,
) {
    private lateinit var statusView: TextView
    private lateinit var pinRow: LinearLayout
    private lateinit var pinInput: EditText
    private lateinit var padContainer: LinearLayout

    fun createView(): View {
        val scroll = ScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#101418"))
            isFillViewport = true
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }
        scroll.addView(root)

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

        // Trackpad: one finger moves the cursor, tap clicks, two fingers scroll.
        padContainer.addView(sectionLabel("Trackpad — tap to click, two fingers to scroll"), matchWrap(topMargin = dp(24)))
        padContainer.addView(buildTrackpad(), matchWrap(topMargin = dp(8)))

        // Keyboard row: free text + special keys.
        padContainer.addView(sectionLabel("Keyboard"), matchWrap(topMargin = dp(20)))
        padContainer.addView(buildKeyboardRow(), matchWrap(topMargin = dp(8)))

        // Voice input (speech-to-text, sent as typed text).
        padContainer.addView(
            rowOf(padButton("🎤 Speak", wide = true) { onVoiceRequest() }),
            wrapWrap(topMargin = dp(12))
        )

        root.addView(padContainer, matchWrap())
        return scroll
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildTrackpad(): View {
        val pad = View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1C232B"))
                cornerRadius = dp(16).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(200)
            )
        }
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var lastX = 0f
        var lastY = 0f
        var downTime = 0L
        var moved = false
        var scrolling = false
        pad.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Don't let the enclosing ScrollView steal the gesture.
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    lastX = event.x; lastY = event.y
                    downTime = event.eventTime
                    moved = false; scrolling = false
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    scrolling = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) moved = true
                    if (moved) {
                        if (scrolling) {
                            onCommand(RemoteCommand.Scroll(dy * SCROLL_SPEED))
                        } else {
                            onCommand(RemoteCommand.PointerMove(dx * CURSOR_SPEED, dy * CURSOR_SPEED))
                        }
                        lastX = event.x; lastY = event.y
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && !scrolling && event.eventTime - downTime < TAP_TIMEOUT_MS) {
                        onCommand(RemoteCommand.PointerClick)
                    }
                    true
                }
                else -> true
            }
        }
        return pad
    }

    private fun buildKeyboardRow(): LinearLayout {
        val input = EditText(context).apply {
            hint = "Type on TV…"
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun smallButton(label: String, onClick: () -> Unit) = Button(context).apply {
            text = label
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(56)).apply {
                setMargins(dp(4), 0, 0, 0)
            }
        }
        row.addView(input)
        row.addView(smallButton("Send") {
            val text = input.text.toString()
            if (text.isNotEmpty()) {
                onCommand(RemoteCommand.Text(text))
                input.text.clear()
            }
        })
        row.addView(smallButton("⌫") { onCommand(RemoteCommand.Key("backspace")) })
        row.addView(smallButton("⏎") { onCommand(RemoteCommand.Key("return")) })
        return row
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.GRAY)
            textSize = 12f
            gravity = Gravity.CENTER
        }
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

    private fun padButton(
        label: String,
        big: Boolean = false,
        wide: Boolean = false,
        onClick: () -> Unit,
    ): Button {
        return Button(context).apply {
            text = label
            textSize = if (big) 20f else 16f
            setOnClickListener { onClick() }
            val size = if (big) dp(96) else dp(80)
            val width = if (wide) dp(200) else size
            layoutParams = LinearLayout.LayoutParams(width, if (wide) dp(64) else size).apply {
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

    companion object {
        private const val CURSOR_SPEED = 1.6f
        private const val SCROLL_SPEED = 1.2f
        private const val TAP_TIMEOUT_MS = 250L
    }
}
