package com.smarttv.remote.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.smarttv.remote.model.RemoteCommand

/**
 * Remote control surface, following the "SmartTV Remote Prototype" design:
 * a numeric-keypad PIN entry screen, then a dark rounded remote body with a
 * mic button, a ring-shaped d-pad (four edge buttons + a circular center
 * SELECT), icon Back/Home buttons, a skip-back/skip-forward row, and a
 * vertical volume rocker. Mouse mode and the on-screen keyboard (not part of
 * the original design) are tucked into a slim utility row so those real
 * features aren't lost.
 */
class RemotePadScreen(
    private val context: Context,
    private val onCommand: (RemoteCommand) -> Unit,
    private val onPinSubmit: (String) -> Unit,
    private val onVoiceRequest: () -> Unit,
) {
    private lateinit var searchingView: View
    private lateinit var pinView: View
    private lateinit var remoteView: View
    private lateinit var stateLineText: TextView
    private lateinit var statusText: TextView
    private lateinit var pinSlots: List<View>
    private lateinit var pinErrorText: TextView
    private lateinit var mouseOverlay: FrameLayout
    private lateinit var keyboardOverlay: FrameLayout

    private var pinEntry: String = ""

    fun createView(): View {
        val frame = FrameLayout(context).apply {
            setBackgroundColor(BG)
            fitsSystemWindows = true
        }

        searchingView = buildSearchingView()
        pinView = buildPinView()
        remoteView = buildRemoteView()

        frame.addView(searchingView, matchMatch())
        frame.addView(pinView, matchMatch())
        frame.addView(remoteView, matchMatch())

        mouseOverlay = buildMouseOverlay()
        frame.addView(mouseOverlay, matchMatch())
        keyboardOverlay = buildKeyboardOverlay()
        frame.addView(keyboardOverlay, matchMatch())

        showScreen(Screen.SEARCHING)
        return frame
    }

    private enum class Screen { SEARCHING, PIN, REMOTE }

    private fun showScreen(screen: Screen) {
        searchingView.visibility = if (screen == Screen.SEARCHING) View.VISIBLE else View.GONE
        pinView.visibility = if (screen == Screen.PIN) View.VISIBLE else View.GONE
        remoteView.visibility = if (screen == Screen.REMOTE) View.VISIBLE else View.GONE
    }

    // MARK: - Searching screen (pre-discovery; not part of the source design)

    private fun buildSearchingView(): View {
        statusText = TextView(context).apply {
            text = "Searching for SmartTV…"
            setTextColor(TEXT_SECONDARY)
            textSize = 15f
            gravity = Gravity.CENTER
        }
        return FrameLayout(context).apply {
            addView(statusText, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            ))
        }
    }

    // MARK: - PIN entry screen

    private fun buildPinView(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(48), dp(28), dp(28))
        }

        root.addView(TextView(context).apply {
            text = "Enter PIN"
            setTextColor(TEXT_PRIMARY)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, matchWrap(bottomMargin = dp(4)))

        root.addView(TextView(context).apply {
            text = "Shown on your SmartTV screen"
            setTextColor(TEXT_SECONDARY)
            textSize = 13f
        }, matchWrap(bottomMargin = dp(20)))

        val slotsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        pinSlots = (0 until 4).map {
            View(context).apply {
                background = roundedBg(PIN_SLOT_EMPTY, dp(8))
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(52)).apply {
                    setMargins(dp(6), 0, dp(6), 0)
                }
            }
        }
        pinSlots.forEach { slotsRow.addView(it) }
        root.addView(slotsRow, matchWrap(bottomMargin = dp(16)))

        pinErrorText = TextView(context).apply {
            text = "Incorrect PIN — try again"
            setTextColor(ERROR)
            textSize = 13f
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
        }
        root.addView(pinErrorText, matchWrap(bottomMargin = dp(8)))

        val keypad = android.widget.GridLayout(context).apply {
            columnCount = 3
            rowCount = 4
        }
        val labels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
        labels.forEachIndexed { index, label ->
            val cell = TextView(context).apply {
                text = label
                setTextColor(TEXT_PRIMARY)
                textSize = 19f
                gravity = Gravity.CENTER
                isClickable = true
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), roundedBg(KEYPAD_PRESSED, dp(12)))
                    addState(intArrayOf(), roundedBg(KEYPAD_BG, dp(12)))
                }
                setOnClickListener {
                    when (label) {
                        "⌫" -> clearPinDigit()
                        "OK" -> submitPin()
                        else -> addPinDigit(label)
                    }
                }
            }
            val params = android.widget.GridLayout.LayoutParams(
                android.widget.GridLayout.spec(index / 3, 1f),
                android.widget.GridLayout.spec(index % 3, 1f)
            ).apply {
                width = 0
                height = dp(58)
                setMargins(dp(5), dp(5), dp(5), dp(5))
            }
            keypad.addView(cell, params)
        }
        root.addView(keypad, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return root
    }

    private fun addPinDigit(digit: String) {
        if (pinEntry.length >= 4) return
        pinEntry += digit
        pinErrorText.visibility = View.INVISIBLE
        updatePinSlots()
        if (pinEntry.length == 4) submitPin()
    }

    private fun clearPinDigit() {
        if (pinEntry.isEmpty()) return
        pinEntry = pinEntry.dropLast(1)
        pinErrorText.visibility = View.INVISIBLE
        updatePinSlots()
    }

    private fun submitPin() {
        if (pinEntry.isEmpty()) return
        onPinSubmit(pinEntry)
    }

    private fun updatePinSlots() {
        pinSlots.forEachIndexed { i, slot ->
            slot.background = roundedBg(if (i < pinEntry.length) ACCENT else PIN_SLOT_EMPTY, dp(8))
        }
    }

    // MARK: - Paired remote screen

    private fun buildRemoteView(): View {
        val scroll = ScrollView(context).apply { isFillViewport = true }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }
        scroll.addView(root, matchMatch())

        stateLineText = TextView(context).apply {
            text = "TV: grid"
            setTextColor(TEXT_SECONDARY)
            textSize = 12f
            gravity = Gravity.CENTER
            background = roundedBg(PILL_BG, dp(10))
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(stateLineText, matchWrap(bottomMargin = dp(16)))

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(BODY_TOP, BODY_BOTTOM)
            ).apply { cornerRadius = dp(28).toFloat() }
            setPadding(dp(16), dp(28), dp(16), dp(32))
        }

        // Mic
        body.addView(circleButton("🎤", dp(52), MIC_BG) { onVoiceRequest() }, wrapWrap(bottomMargin = dp(28)))

        // D-pad ring
        body.addView(buildDpadRing(), wrapWrap(bottomMargin = dp(28)))

        // Back / Home
        body.addView(
            rowOf(
                iconLabelButton("◀◀", "BACK") { onCommand(RemoteCommand.Back) },
                iconLabelButton("⌂", "HOME") { onCommand(RemoteCommand.Home) },
            ).apply { (this as LinearLayout).let {} },
            wrapWrap(bottomMargin = dp(24))
        )

        // Skip back/forward (real seek, replacing the mockup's decorative row)
        body.addView(
            rowOf(
                smallGlyphButton("⏮") { onCommand(RemoteCommand.Seek("back")) },
                smallGlyphButton("⏭") { onCommand(RemoteCommand.Seek("forward")) },
            ),
            wrapWrap(bottomMargin = dp(20))
        )

        // Volume rocker
        body.addView(buildVolumeRocker(), wrapWrap())

        root.addView(body, matchWrap())

        // Slim utility row: mouse mode + keyboard (real features, not in the
        // original design, kept accessible without disrupting its layout).
        root.addView(
            rowOf(
                utilityButton("🖱 Mouse") { mouseOverlay.visibility = View.VISIBLE },
                utilityButton("⌨ Keyboard") { keyboardOverlay.visibility = View.VISIBLE },
            ),
            wrapWrap(topMargin = dp(18))
        )

        return scroll
    }

    private fun buildDpadRing(): View {
        val ringSize = dp(210)
        val armSize = dp(56)
        val centerSize = dp(84)
        val armOffset = dp(76) // distance from ring edge to the perpendicular edge of up/down/left/right arms
        val centerOffset = dp(63)

        val ring = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(ringSize, ringSize)
        }

        fun arm(symbol: String, edgeGravity: Int, width: Int, height: Int, radii: FloatArray, direction: String) {
            ring.addView(
                TextView(context).apply {
                    text = symbol
                    setTextColor(TEXT_SECONDARY)
                    textSize = 15f
                    gravity = Gravity.CENTER
                    isClickable = true
                    background = StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply { setColor(BUTTON_PRESSED); cornerRadii = radii })
                        addState(intArrayOf(), GradientDrawable().apply { setColor(BUTTON_BG); cornerRadii = radii })
                    }
                    setOnClickListener { onCommand(RemoteCommand.Navigate(direction)) }
                },
                FrameLayout.LayoutParams(width, height, edgeGravity)
            )
        }

        val r = dp(20).toFloat()
        val sharp = 0f
        // top: rounded top corners only
        arm("▲", Gravity.TOP or Gravity.CENTER_HORIZONTAL, armSize, armSize,
            floatArrayOf(r, r, r, r, sharp, sharp, sharp, sharp), "up")
        // bottom: rounded bottom corners only
        arm("▼", Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, armSize, armSize,
            floatArrayOf(sharp, sharp, sharp, sharp, r, r, r, r), "down")
        // left: rounded left corners only
        arm("◀", Gravity.START or Gravity.CENTER_VERTICAL, armSize, armSize,
            floatArrayOf(r, r, sharp, sharp, sharp, sharp, r, r), "left")
        // right: rounded right corners only
        arm("▶", Gravity.END or Gravity.CENTER_VERTICAL, armSize, armSize,
            floatArrayOf(sharp, sharp, r, r, r, r, sharp, sharp), "right")

        ring.addView(
            TextView(context).apply {
                text = "SELECT"
                setTextColor(Color.WHITE)
                textSize = 11f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                isClickable = true
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), ovalBg(ACCENT_PRESSED))
                    addState(intArrayOf(), ovalBg(ACCENT))
                }
                setOnClickListener { onCommand(RemoteCommand.Select) }
            },
            FrameLayout.LayoutParams(centerSize, centerSize, Gravity.CENTER)
        )

        return ring
    }

    private fun buildVolumeRocker(): View {
        val rocker = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg(MIC_BG, dp(24))
        }
        rocker.addView(volumeRockerButton("+") { onCommand(RemoteCommand.Volume("up")) })
        rocker.addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#22FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(1))
        })
        rocker.addView(volumeRockerButton("−") { onCommand(RemoteCommand.Volume("down")) })
        return rocker
    }

    private fun volumeRockerButton(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(TEXT_SECONDARY)
            textSize = 20f
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }
    }

    private fun iconLabelButton(icon: String, label: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), roundedBg(BUTTON_PRESSED, dp(16)))
                addState(intArrayOf(), roundedBg(BUTTON_BG, dp(16)))
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(74), dp(64)).apply {
                setMargins(dp(14), 0, dp(14), 0)
            }
            addView(TextView(context).apply {
                text = icon
                setTextColor(TEXT_SECONDARY)
                textSize = 16f
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = label
                setTextColor(TEXT_SECONDARY)
                textSize = 9f
                letterSpacing = 0.06f
                gravity = Gravity.CENTER
            })
        }
    }

    private fun smallGlyphButton(symbol: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = symbol
            setTextColor(TEXT_SECONDARY)
            textSize = 18f
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(44)).apply {
                setMargins(dp(10), 0, dp(10), 0)
            }
        }
    }

    private fun circleButton(label: String, size: Int, bg: Int, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = 17f
            gravity = Gravity.CENTER
            isClickable = true
            background = ovalBg(bg)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    private fun utilityButton(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(TEXT_SECONDARY)
            textSize = 13f
            gravity = Gravity.CENTER
            isClickable = true
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), roundedBg(BUTTON_PRESSED, dp(12)))
                addState(intArrayOf(), roundedBg(PILL_BG, dp(12)))
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)).apply {
                setMargins(dp(6), 0, dp(6), 0)
                leftMargin = dp(6); rightMargin = dp(6)
            }
            setPadding(dp(14), 0, dp(14), 0)
        }
    }

    // MARK: mouse mode overlay (unchanged behavior, restyled to match palette)

    private fun buildMouseOverlay(): FrameLayout {
        val overlay = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#F2101418"))
            visibility = View.GONE
        }
        overlay.addView(
            TextView(context).apply {
                text = "Mouse mode\n\nmove finger = cursor\ntap = click\ntwo fingers = scroll"
                setTextColor(TEXT_SECONDARY)
                textSize = 15f
                gravity = Gravity.CENTER
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        overlay.addView(
            utilityButton("✕ Exit mouse mode") { overlay.visibility = View.GONE },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                .apply { setMargins(0, 0, 0, dp(48)) }
        )
        installTrackpadTouch(overlay)
        return overlay
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun installTrackpadTouch(overlay: FrameLayout) {
        val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        var lastX = 0f; var lastY = 0f
        var moved = false; var scrolling = false
        var downTime = 0L
        overlay.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    lastX = event.x; lastY = event.y; moved = false; scrolling = false; downTime = event.eventTime
                    true
                }
                android.view.MotionEvent.ACTION_POINTER_DOWN -> { scrolling = true; true }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (!moved && (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop)) moved = true
                    if (moved) {
                        if (scrolling) onCommand(RemoteCommand.Scroll(dy * 1.2f))
                        else onCommand(RemoteCommand.PointerMove(dx * 1.8f, dy * 1.8f))
                        lastX = event.x; lastY = event.y
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!moved && !scrolling && event.eventTime - downTime < 250) onCommand(RemoteCommand.PointerClick)
                    true
                }
                else -> true
            }
        }
    }

    // MARK: keyboard overlay (unchanged behavior, restyled)

    private fun buildKeyboardOverlay(): FrameLayout {
        val overlay = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#F2101418"))
            visibility = View.GONE
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val input = android.widget.EditText(context).apply {
            hint = "Type on TV…"
            setTextColor(Color.WHITE)
            setHintTextColor(TEXT_SECONDARY)
            background = roundedBg(BUTTON_BG, dp(12))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(dp(240), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        column.addView(input, matchWrap(bottomMargin = dp(12)))
        column.addView(rowOf(
            utilityButton("Send") {
                val text = input.text.toString()
                if (text.isNotEmpty()) { onCommand(RemoteCommand.Text(text)); input.text.clear() }
            },
            utilityButton("⌫") { onCommand(RemoteCommand.Key("backspace")) },
            utilityButton("⏎") { onCommand(RemoteCommand.Key("return")) },
        ))
        column.addView(utilityButton("✕ Close") { overlay.visibility = View.GONE }, wrapWrap(topMargin = dp(16)))
        overlay.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        return overlay
    }

    // MARK: - State updates (main thread)

    fun setStatus(text: String) {
        statusText.text = text
        showScreen(Screen.SEARCHING)
    }

    fun showPinEntry(show: Boolean) {
        if (show) {
            pinEntry = ""
            updatePinSlots()
            pinErrorText.visibility = View.INVISIBLE
            showScreen(Screen.PIN)
        }
    }

    fun showPinError() {
        pinEntry = ""
        updatePinSlots()
        pinErrorText.visibility = View.VISIBLE
    }

    fun showPaired() {
        showScreen(Screen.REMOTE)
    }

    fun setStateLine(text: String) {
        stateLineText.text = text
    }

    fun setControlsEnabled(enabled: Boolean) {
        remoteView.alpha = if (enabled) 1.0f else 0.4f
        if (!enabled) {
            mouseOverlay.visibility = View.GONE
            keyboardOverlay.visibility = View.GONE
        }
    }

    // MARK: - helpers

    private fun roundedBg(color: Int, cornerRadiusPx: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = cornerRadiusPx.toFloat() }

    private fun ovalBg(color: Int): GradientDrawable =
        GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    private fun rowOf(vararg views: View): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            views.forEach { addView(it) }
        }

    private fun matchMatch() = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

    private fun matchWrap(topMargin: Int = 0, bottomMargin: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            this.topMargin = topMargin; this.bottomMargin = bottomMargin
        }

    private fun wrapWrap(topMargin: Int = 0, bottomMargin: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            this.topMargin = topMargin; this.bottomMargin = bottomMargin
        }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private val BG = Color.parseColor("#101418")
        private val BODY_TOP = Color.parseColor("#2B2F36")
        private val BODY_BOTTOM = Color.parseColor("#1C1F24")
        private val BUTTON_BG = Color.parseColor("#383D46")
        private val BUTTON_PRESSED = Color.parseColor("#454B55")
        private val ACCENT = Color.parseColor("#5B5FEF")
        private val ACCENT_PRESSED = Color.parseColor("#7075F5")
        private val MIC_BG = Color.parseColor("#3D4249")
        private val PILL_BG = Color.parseColor("#24272C")
        private val KEYPAD_BG = Color.parseColor("#2C3038")
        private val KEYPAD_PRESSED = Color.parseColor("#3A3F48")
        private val PIN_SLOT_EMPTY = Color.parseColor("#2E323A")
        private val TEXT_PRIMARY = Color.parseColor("#F1F2F4")
        private val TEXT_SECONDARY = Color.parseColor("#ACB2B9")
        private val ERROR = Color.parseColor("#E0523F")
    }
}
