package me.arnabsaha.airpodscompanion.ui.popup

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import me.arnabsaha.airpodscompanion.R
import me.arnabsaha.airpodscompanion.service.AacpBatteryState
import me.arnabsaha.airpodscompanion.service.EarState

/**
 * Rich Dynamic Island-style popup overlay.
 *
 * Shows on connection with battery bars, ANC mode, and ear detection state.
 * User dismisses via swipe-up or tap — NO auto-dismiss.
 * Content updates in real-time while visible.
 * Can be re-shown from notification "Quick View" action.
 */
class ConnectionPopup(private val context: Context) {

    companion object {
        private const val TAG = "ConnectionPopup"
        private const val COOLDOWN_MS = 30_000L
        private const val SWIPE_THRESHOLD_DP = 50
        private const val SWIPE_VELOCITY_THRESHOLD = 500f // dp/s
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var popupView: View? = null
    private var isShowing = false
    private var lastShowTime = 0L

    // View references for real-time updates
    private var leftBatteryBar: ProgressBar? = null
    private var rightBatteryBar: ProgressBar? = null
    private var caseBatteryBar: ProgressBar? = null
    private var leftBatteryText: TextView? = null
    private var rightBatteryText: TextView? = null
    private var caseBatteryText: TextView? = null
    private var ancDot: View? = null
    private var ancText: TextView? = null
    private var leftEarDot: View? = null
    private var rightEarDot: View? = null

    private val dp = { value: Int -> (value * context.resources.displayMetrics.density).toInt() }

    @SuppressLint("InflateException")
    fun show(
        deviceName: String,
        battery: AacpBatteryState?,
        ancMode: String,
        earState: EarState = EarState(),
        forceByCooldown: Boolean = false
    ) {
        // Cooldown — don't spam popups on rapid reconnects (unless forced by user action)
        val now = System.currentTimeMillis()
        if (!forceByCooldown && now - lastShowTime < COOLDOWN_MS) {
            Log.d(TAG, "Popup cooldown active, skipping")
            return
        }

        if (isShowing) {
            forceRemoveView()
        }

        if (!android.provider.Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission not granted")
            return
        }

        handler.post {
            try {
                val view = createPopupView(deviceName, battery, ancMode, earState)
                val params = createLayoutParams()

                windowManager.addView(view, params)
                popupView = view
                isShowing = true
                lastShowTime = now

                view.translationY = -300f
                view.alpha = 0f
                view.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(500)
                    .setInterpolator(DecelerateInterpolator(1.5f))
                    .start()

                Log.d(TAG, "Popup shown for $deviceName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show popup: $e")
                isShowing = false
            }
        }
    }

    /** Update popup content in real-time while it's showing */
    fun updateContent(battery: AacpBatteryState?, ancMode: Byte, earState: EarState) {
        if (!isShowing) return
        handler.post {
            battery?.let { b ->
                updateBatteryBar(leftBatteryBar, leftBatteryText, b.leftLevel, b.leftCharging)
                updateBatteryBar(rightBatteryBar, rightBatteryText, b.rightLevel, b.rightCharging)
                updateBatteryBar(caseBatteryBar, caseBatteryText, b.caseLevel, b.caseCharging)
            }

            // Update ANC indicator
            val (ancColor, ancLabel) = when (ancMode) {
                0x02.toByte() -> 0xFF34C759.toInt() to "Noise Cancellation"       // Green
                0x03.toByte() -> 0xFF007AFF.toInt() to "Transparency"              // Blue
                0x04.toByte() -> 0xFFFF9500.toInt() to "Adaptive"                  // Orange
                else -> 0xFF8E8E93.toInt() to "Off"                                 // Gray
            }
            ancDot?.backgroundTintList = ColorStateList.valueOf(ancColor)
            ancText?.text = ancLabel

            // Update ear detection dots
            leftEarDot?.backgroundTintList = ColorStateList.valueOf(
                if (earState.leftInEar) 0xFF34C759.toInt() else 0xFF8E8E93.toInt()
            )
            rightEarDot?.backgroundTintList = ColorStateList.valueOf(
                if (earState.rightInEar) 0xFF34C759.toInt() else 0xFF8E8E93.toInt()
            )
        }
    }

    fun dismiss() {
        handler.post {
            val view = popupView ?: return@post
            if (!isShowing) return@post

            view.animate()
                .translationY(-300f)
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { forceRemoveView() }
                .start()

            // Safety net — force remove after animation timeout
            handler.postDelayed({ forceRemoveView() }, 500)
        }
    }

    private fun forceRemoveView() {
        val view = popupView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {}
        popupView = null
        isShowing = false
        // Clear view references
        leftBatteryBar = null; rightBatteryBar = null; caseBatteryBar = null
        leftBatteryText = null; rightBatteryText = null; caseBatteryText = null
        ancDot = null; ancText = null; leftEarDot = null; rightEarDot = null
    }

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    private fun createPopupView(
        deviceName: String,
        battery: AacpBatteryState?,
        ancMode: String,
        earState: EarState
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xF0282828.toInt())
            elevation = dp(8).toFloat()
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
                }
            }
        }

        // ── Row 1: Icon + Device Name ──
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(context).apply {
            setImageResource(R.drawable.airpods_case)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                marginEnd = dp(12)
            }
        }
        headerRow.addView(icon)

        headerRow.addView(TextView(context).apply {
            text = deviceName
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        container.addView(headerRow)

        // ── Row 2: Battery Bars ──
        if (battery != null) {
            container.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                ).apply { topMargin = dp(10); bottomMargin = dp(10) }
                setBackgroundColor(0x33FFFFFF)
            })

            val batteryGrid = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            // Left
            val (leftBar, leftText) = createBatteryItem("L", battery.leftLevel, battery.leftCharging, 1f)
            batteryGrid.addView(leftBar)
            leftBatteryBar = leftBar.findViewWithTag("bar")
            leftBatteryText = leftText

            batteryGrid.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8), 0)
            })

            // Right
            val (rightBar, rightText) = createBatteryItem("R", battery.rightLevel, battery.rightCharging, 1f)
            batteryGrid.addView(rightBar)
            rightBatteryBar = rightBar.findViewWithTag("bar")
            rightBatteryText = rightText

            if (battery.caseLevel >= 0) {
                batteryGrid.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(8), 0)
                })
                val (caseBar, caseText) = createBatteryItem("Case", battery.caseLevel, battery.caseCharging, 1f)
                batteryGrid.addView(caseBar)
                caseBatteryBar = caseBar.findViewWithTag("bar")
                caseBatteryText = caseText
            }

            container.addView(batteryGrid)
        }

        // ── Row 3: ANC Mode + Ear Detection ──
        val statusRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        // ANC indicator
        val (ancColor, _) = getAncColorAndLabel(ancMode)
        val dot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(6) }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(ancColor)
            }
            backgroundTintList = ColorStateList.valueOf(ancColor)
        }
        ancDot = dot
        statusRow.addView(dot)

        val ancLabel = TextView(context).apply {
            text = ancMode
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        ancText = ancLabel
        statusRow.addView(ancLabel)

        // Ear detection dots
        val earRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        earRow.addView(TextView(context).apply {
            text = "L"
            setTextColor(0x99FFFFFF.toInt())
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(3) }
        })

        val lEarDot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginEnd = dp(8) }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(if (earState.leftInEar) 0xFF34C759.toInt() else 0xFF8E8E93.toInt())
            }
            backgroundTintList = ColorStateList.valueOf(
                if (earState.leftInEar) 0xFF34C759.toInt() else 0xFF8E8E93.toInt()
            )
        }
        leftEarDot = lEarDot
        earRow.addView(lEarDot)

        earRow.addView(TextView(context).apply {
            text = "R"
            setTextColor(0x99FFFFFF.toInt())
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(3) }
        })

        val rEarDot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(6), dp(6))
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(if (earState.rightInEar) 0xFF34C759.toInt() else 0xFF8E8E93.toInt())
            }
            backgroundTintList = ColorStateList.valueOf(
                if (earState.rightInEar) 0xFF34C759.toInt() else 0xFF8E8E93.toInt()
            )
        }
        rightEarDot = rEarDot
        earRow.addView(rEarDot)

        statusRow.addView(earRow)
        container.addView(statusRow)

        // Wrap in FrameLayout with top padding
        val wrapper = FrameLayout(context).apply {
            val margin = dp(16)
            setPadding(margin, dp(48), margin, 0)
            addView(container, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        // ── Swipe-up / Tap gesture to dismiss ──
        wrapper.setOnTouchListener(object : View.OnTouchListener {
            private var startY = 0f
            private var velocityTracker: VelocityTracker? = null

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        velocityTracker = VelocityTracker.obtain()
                        velocityTracker?.addMovement(event)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        velocityTracker?.addMovement(event)
                        val deltaY = event.rawY - startY
                        if (deltaY < 0) {
                            popupView?.translationY = deltaY
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        velocityTracker?.apply {
                            addMovement(event)
                            computeCurrentVelocity(1000)
                        }
                        val deltaY = event.rawY - startY
                        val velocity = velocityTracker?.yVelocity ?: 0f
                        velocityTracker?.recycle()
                        velocityTracker = null

                        val threshold = SWIPE_THRESHOLD_DP * v.resources.displayMetrics.density
                        if (deltaY < -threshold || velocity < -SWIPE_VELOCITY_THRESHOLD) {
                            dismiss()
                        } else if (kotlin.math.abs(deltaY) < 10) {
                            dismiss()
                        } else {
                            popupView?.animate()?.translationY(0f)?.setDuration(200)?.start()
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        velocityTracker?.recycle()
                        velocityTracker = null
                        popupView?.animate()?.translationY(0f)?.setDuration(200)?.start()
                        return true
                    }
                }
                return false
            }
        })

        return wrapper
    }

    /** Create a battery item with label, progress bar, and percentage text */
    @SuppressLint("SetTextI18n")
    private fun createBatteryItem(
        label: String,
        level: Int,
        isCharging: Boolean,
        weight: Float
    ): Pair<LinearLayout, TextView> {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        }

        // Label
        item.addView(TextView(context).apply {
            text = label
            setTextColor(0x99FFFFFF.toInt())
            textSize = 10f
            gravity = Gravity.CENTER
        })

        // Progress bar
        val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            tag = "bar"
            max = 100
            progress = level.coerceAtLeast(0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(5)
            ).apply { topMargin = dp(3); bottomMargin = dp(3) }
            progressTintList = ColorStateList.valueOf(getBatteryColor(level, isCharging))
            progressBackgroundTintList = ColorStateList.valueOf(0x33FFFFFF)
        }
        item.addView(bar)

        // Percentage
        val pctText = TextView(context).apply {
            text = if (level >= 0) "${level}%" else "—"
            setTextColor(if (isCharging) 0xFF34C759.toInt() else 0xB3FFFFFF.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
        }
        item.addView(pctText)

        return item to pctText
    }

    private fun updateBatteryBar(bar: ProgressBar?, text: TextView?, level: Int, isCharging: Boolean) {
        bar?.progress = level.coerceAtLeast(0)
        bar?.progressTintList = ColorStateList.valueOf(getBatteryColor(level, isCharging))
        text?.text = if (level >= 0) "${level}%" else "—"
        text?.setTextColor(if (isCharging) 0xFF34C759.toInt() else 0xB3FFFFFF.toInt())
    }

    private fun getBatteryColor(level: Int, isCharging: Boolean): Int = when {
        isCharging -> 0xFF34C759.toInt()       // Green when charging
        level <= 10 -> 0xFFFF3B30.toInt()      // Red
        level <= 20 -> 0xFFFF9500.toInt()      // Orange
        else -> 0xFF34C759.toInt()              // Green
    }

    private fun getAncColorAndLabel(ancMode: String): Pair<Int, String> = when (ancMode) {
        "Noise Cancellation" -> 0xFF34C759.toInt() to ancMode
        "Transparency" -> 0xFF007AFF.toInt() to ancMode
        "Adaptive" -> 0xFFFF9500.toInt() to ancMode
        else -> 0xFF8E8E93.toInt() to ancMode
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
        }
    }
}
