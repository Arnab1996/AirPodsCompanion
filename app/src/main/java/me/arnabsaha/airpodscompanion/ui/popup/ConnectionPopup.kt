package me.arnabsaha.airpodscompanion.ui.popup

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import me.arnabsaha.airpodscompanion.R
import me.arnabsaha.airpodscompanion.service.AacpBatteryState

/**
 * Dynamic Island-style popup overlay that appears when AirPods connect.
 * Slides down from the top of the screen, shows battery info, auto-dismisses.
 *
 * Uses SYSTEM_ALERT_WINDOW permission (TYPE_APPLICATION_OVERLAY).
 */
class ConnectionPopup(private val context: Context) {

    companion object {
        private const val TAG = "ConnectionPopup"
        private const val AUTO_DISMISS_MS = 5000L
        private const val ANIM_ENTER_MS = 500L
        private const val ANIM_EXIT_MS = 400L
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var popupView: View? = null
    private var isShowing = false

    @SuppressLint("InflateException")
    fun show(deviceName: String, battery: AacpBatteryState?, ancMode: String) {
        if (isShowing) dismiss()
        if (!android.provider.Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission not granted")
            return
        }

        handler.post {
            try {
                val view = createPopupView(deviceName, battery, ancMode)
                val params = createLayoutParams()

                windowManager.addView(view, params)
                popupView = view
                isShowing = true

                // Slide-in animation
                view.translationY = -300f
                view.alpha = 0f
                view.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(ANIM_ENTER_MS)
                    .setInterpolator(DecelerateInterpolator(1.5f))
                    .start()

                // Auto-dismiss
                handler.postDelayed({ dismiss() }, AUTO_DISMISS_MS)

                Log.d(TAG, "Popup shown for $deviceName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show popup: $e")
            }
        }
    }

    fun dismiss() {
        handler.post {
            val view = popupView ?: return@post
            if (!isShowing) return@post

            view.animate()
                .translationY(-300f)
                .alpha(0f)
                .setDuration(ANIM_EXIT_MS)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    try {
                        windowManager.removeView(view)
                    } catch (_: Exception) {}
                    popupView = null
                    isShowing = false
                }
                .start()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun createPopupView(deviceName: String, battery: AacpBatteryState?, ancMode: String): View {
        val dp = { value: Int -> (value * context.resources.displayMetrics.density).toInt() }

        // Main container — rounded dark pill
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(0xE6000000.toInt()) // Near-black with slight transparency
            elevation = dp(8).toFloat()

            // Make it rounded via clip
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(24).toFloat())
                }
            }
        }

        // AirPods icon
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.airpods_case)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                marginEnd = dp(12)
            }
        }
        container.addView(icon)

        // Text column
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Device name
        val nameText = TextView(context).apply {
            text = deviceName
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        textCol.addView(nameText)

        // Battery info
        if (battery != null) {
            val batteryText = TextView(context).apply {
                text = buildString {
                    append("L: ${if (battery.leftLevel >= 0) "${battery.leftLevel}%" else "—"}")
                    append("   R: ${if (battery.rightLevel >= 0) "${battery.rightLevel}%" else "—"}")
                    if (battery.caseLevel >= 0) append("   Case: ${battery.caseLevel}%")
                }
                setTextColor(0xB3FFFFFF.toInt()) // White 70%
                textSize = 12f
            }
            textCol.addView(batteryText)
        }

        // ANC mode
        val ancText = TextView(context).apply {
            text = ancMode
            setTextColor(0xFF34C759.toInt()) // Apple green
            textSize = 11f
        }
        textCol.addView(ancText)

        container.addView(textCol)

        // Wrap in a FrameLayout for margins
        val wrapper = FrameLayout(context).apply {
            val margin = dp(16)
            val topMargin = dp(48) // Below status bar
            setPadding(margin, topMargin, margin, 0)
            addView(container, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        // Tap to dismiss
        wrapper.setOnClickListener { dismiss() }

        return wrapper
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
