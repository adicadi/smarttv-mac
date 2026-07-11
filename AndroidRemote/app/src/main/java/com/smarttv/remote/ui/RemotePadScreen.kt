package com.smarttv.remote.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import com.smarttv.remote.model.RemoteCommand
import kotlin.math.abs

/**
 * Remote control surface, built programmatically (no layout XML, no androidx).
 *
 * Layout mimics a TV remote: status on top, a large Firestick-style circle
 * pad in the middle (swipe = d-pad steps, tap = select), control rows below,
 * keyboard/voice at the bottom. A full-screen "mouse mode" overlay turns the
 * whole phone into a trackpad (finger = cursor, tap = click, two fingers =
 * scroll).
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
            setBackgroundColor(BG)
            fitsSystemWindows = true
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(16), dp(24), dp(20))
        }
        scroll.addView(root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        frame.addView(scroll)

        statusView = TextView(context).apply {
            text = "Searching for SmartTV…"
            setTextColor(Color.parseColor("#8E9AA6"))
            textSize = 14f
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
        pinRow.addView(pillButton("Pair", dp(90)) { onPinSubmit(pinInput.text.toString().trim()) })
        root.addView(pinRow, matchWrap(topMargin = dp(10)))

        padContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            alpha = 0.4f // dimmed until paired
        }

        // flexible space pushes the circle toward the vertical middle
        padContainer.addView(Space(context), weightSpacer())

        padContainer.addView(buildCirclePad(), wrapWrap())
        padContainer.addView(sectionLabel("swipe to move · tap to select"), matchWrap(topMargin = dp(10)))

        padContainer.addView(
            rowOf(
                pillButton("‹ Back", dp(104)) { onCommand(RemoteCommand.Back) },
                pillButton("⌂ Home", dp(104)) { onCommand(RemoteCommand.Home) },
                pillButton("🖱", dp(64)) { showMouseMode(true) },
            ),
            wrapWrap(topMargin = dp(24))
        )
        padContainer.addView(
            rowOf(
                pillButton("Vol −", dp(104)) { onCommand(RemoteCommand.Volume("down")) },
                pillButton("Vol +", dp(104)) { onCommand(RemoteCommand.Volume("up")) },
                pillButton("🎤", dp(64)) { onVoiceRequest() },
            ),
            wrapWrap(topMargin = dp(10))
        )

        padContainer.addView(Space(context), weightSpacer())

        padContainer.addView(buildKeyboardRow(), matchWrap(topMargin = dp(16)))

        root.addView(padContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

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
        val size = dp(240)
        val pad = TextView(context).apply {
            text = "OK"
            setTextColor(Color.parseColor("#AAB6C2"))
            textSize = 20f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(CARD)
                setStroke(dp(2), Color.parseColor("#2E3944"))
            }
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
        val step = dp(70).toFloat() // finger travel per navigation step
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var startX = 0f; var startY = 0f
        var stepped = false
        var downTime = 0L
        pad.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent.requestDisallowInterceptTouchEvent(true)
                    startX = event.x; startY = event.y
                    stepped = false
                    downTime = event.eventTime
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - startX
                    val dy = event.y - startY
                    // Each `step` of travel in the dominant axis fires one
                    // discrete navigate and re-anchors, so a long swipe can
                    // step several tiles.
                    if (abs(dx) >= step || abs(dy) >= step) {
                        val direction = if (abs(dx) > abs(dy)) {
                            if (dx > 0) "right" else "left"
                        } else {
                            if (dy > 0) "down" else "up"
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
            setBackgroundColor(Color.parseColor("#F20B0E12"))
            visibility = View.GONE
        }
        val hint = TextView(context).apply {
            text = "Mouse mode\n\nmove finger = cursor\ntap = click\ntwo fingers = scroll"
            setTextColor(Color.parseColor("#5A646E"))
            textSize = 15f
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
        val exit = pillButton("✕ Exit mouse", dp(150)) { showMouseMode(false) }
        overlay.addView(
            exit,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { setMargins(0, 0, 0, dp(48)) }
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
            textSize = 15f
            background = roundedBg(CARD)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(input)
        row.addView(pillButton("Send", dp(72)) {
            val text = input.text.toString()
            if (text.isNotEmpty()) {
                onCommand(RemoteCommand.Text(text))
                input.text.clear()
            }
        })
        row.addView(pillButton("⌫", dp(52)) { onCommand(RemoteCommand.Key("backspace")) })
        row.addView(pillButton("⏎", dp(52)) { onCommand(RemoteCommand.Key("return")) })
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

    /// Rounded dark "remote button": flat TextView with a pressed state,
    /// instead of the boxy platform Button style.
    private fun pillButton(label: String, width: Int, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), roundedBg(CARD_PRESSED))
                addState(intArrayOf(), roundedBg(CARD))
            }
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(width, dp(48)).apply {
                setMargins(dp(5), dp(4), dp(5), dp(4))
            }
        }
    }

    private fun roundedBg(color: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(14).toFloat()
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
            setTextColor(Color.parseColor("#5A646E"))
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

    private fun weightSpacer() =
        LinearLayout.LayoutParams(0, 0, 1f)

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private val BG = Color.parseColor("#101418")
        private val CARD = Color.parseColor("#232B34")
        private val CARD_PRESSED = Color.parseColor("#33404D")
        private const val CURSOR_SPEED = 1.8f
        private const val SCROLL_SPEED = 1.2f
        private const val TAP_TIMEOUT_MS = 250L
    }
}
