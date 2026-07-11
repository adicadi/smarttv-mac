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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.smarttv.remote.model.RemoteCommand
import kotlin.math.abs

/**
 * Remote control surface, built programmatically (no layout XML, no androidx).
 *
 * Firestick-style circle pad: swiping on the circle sends discrete d-pad
 * navigation steps, a single tap sends select. A separate full-screen
 * "mouse mode" overlay turns the whole phone screen into a trackpad
 * (one finger = cursor, tap = click, two fingers = scroll).
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
    private lateinit var mouseOverlay: FrameLayout

    fun createView(): View {
        val frame = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#101418"))
        }
        val scroll = ScrollView(context).apply { isFillViewport = true }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }
        scroll.addView(root)
        frame.addView(scroll)

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
        pinRow.addView(smallButton("Pair") { onPinSubmit(pinInput.text.toString().trim()) })
        root.addView(pinRow, matchWrap(topMargin = dp(16)))

        padContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            alpha = 0.4f // dimmed until paired
        }

        // Firestick-style circle: swipe = navigate, tap = select.
        padContainer.addView(sectionLabel("Swipe to move · tap to select"), matchWrap(topMargin = dp(24)))
        padContainer.addView(buildCirclePad(), wrapWrap(topMargin = dp(8)))

        // Back / Home / Mouse mode
        padContainer.addView(
            rowOf(
                smallButton("Back", dp(96)) { onCommand(RemoteCommand.Back) },
                smallButton("Home", dp(96)) { onCommand(RemoteCommand.Home) },
                smallButton("🖱 Mouse", dp(110)) { showMouseMode(true) },
            ),
            wrapWrap(topMargin = dp(20))
        )

        // Volume
        padContainer.addView(
            rowOf(
                smallButton("Vol −", dp(96)) { onCommand(RemoteCommand.Volume("down")) },
                smallButton("Vol +", dp(96)) { onCommand(RemoteCommand.Volume("up")) },
            ),
            wrapWrap(topMargin = dp(10))
        )

        // Keyboard row + voice input.
        padContainer.addView(sectionLabel("Keyboard"), matchWrap(topMargin = dp(22)))
        padContainer.addView(buildKeyboardRow(), matchWrap(topMargin = dp(8)))
        padContainer.addView(
            rowOf(smallButton("🎤 Speak", dp(200)) { onVoiceRequest() }),
            wrapWrap(topMargin = dp(12))
        )

        root.addView(padContainer, matchWrap())

        mouseOverlay = buildMouseOverlay()
        frame.addView(
            mouseOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        return frame
    }

    // MARK: circle pad (navigation)

    @SuppressLint("ClickableViewAccessibility")
    private fun buildCirclePad(): View {
        val size = dp(260)
        val pad = TextView(context).apply {
            text = "OK"
            setTextColor(Color.parseColor("#8899AA"))
            textSize = 22f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1C232B"))
                setStroke(dp(2), Color.parseColor("#2E3944"))
            }
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
        val step = dp(70).toFloat() // finger travel per navigation step
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var startX = 0f; var startY = 0f
        var accX = 0f; var accY = 0f
        var stepped = false
        var downTime = 0L
        pad.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    startX = event.x; startY = event.y
                    accX = 0f; accY = 0f
                    stepped = false
                    downTime = event.eventTime
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    accX = event.x - startX
                    accY = event.y - startY
                    // Each `step` of travel in the dominant axis fires one
                    // discrete navigate and re-anchors, so a long swipe can
                    // step several tiles.
                    if (abs(accX) >= step || abs(accY) >= step) {
                        val direction = if (abs(accX) > abs(accY)) {
                            if (accX > 0) "right" else "left"
                        } else {
                            if (accY > 0) "down" else "up"
                        }
                        onCommand(RemoteCommand.Navigate(direction))
                        stepped = true
                        startX = event.x; startY = event.y
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.x - startX) > slop || abs(event.y - startY) > slop
                    if (!stepped && !moved && event.eventTime - downTime < TAP_TIMEOUT_MS) {
                        onCommand(RemoteCommand.Select)
                    }
                    true
                }
                else -> true
            }
        }
        return pad
    }

    // MARK: full-screen mouse mode

    @SuppressLint("ClickableViewAccessibility")
    private fun buildMouseOverlay(): FrameLayout {
        val overlay = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#EE0B0E12"))
            visibility = View.GONE
        }
        val hint = TextView(context).apply {
            text = "Mouse mode\nmove finger = cursor · tap = click · two fingers = scroll"
            setTextColor(Color.parseColor("#66707A"))
            textSize = 14f
            gravity = Gravity.CENTER
        }
        overlay.addView(
            hint,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        val exit = smallButton("✕ Exit", dp(110)) { showMouseMode(false) }
        overlay.addView(
            exit,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply { setMargins(0, dp(40), dp(20), 0) }
        )

        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var lastX = 0f; var lastY = 0f
        var moved = false; var scrolling = false
        var downTime = 0L
        overlay.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x; lastY = event.y
                    moved = false; scrolling = false
                    downTime = event.eventTime
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> { scrolling = true; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
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
        return overlay
    }

    private fun showMouseMode(show: Boolean) {
        mouseOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    // MARK: keyboard row

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
        row.addView(input)
        row.addView(smallButton("Send", dp(72)) {
            val text = input.text.toString()
            if (text.isNotEmpty()) {
                onCommand(RemoteCommand.Text(text))
                input.text.clear()
            }
        })
        row.addView(smallButton("⌫", dp(56)) { onCommand(RemoteCommand.Key("backspace")) })
        row.addView(smallButton("⏎", dp(56)) { onCommand(RemoteCommand.Key("return")) })
        return row
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
        if (!enabled) showMouseMode(false)
    }

    // MARK: helpers

    private fun smallButton(label: String, width: Int = dp(80), onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(width, dp(56)).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
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

    private fun sectionLabel(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.GRAY)
            textSize = 12f
            gravity = Gravity.CENTER
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
        private const val CURSOR_SPEED = 1.8f
        private const val SCROLL_SPEED = 1.2f
        private const val TAP_TIMEOUT_MS = 250L
    }
}
