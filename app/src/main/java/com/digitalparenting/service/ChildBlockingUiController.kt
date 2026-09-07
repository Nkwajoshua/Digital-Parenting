package com.digitalparenting.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.digitalparenting.R
import com.digitalparenting.ui.BlockedActivity

/**
 * Owns Android UI mechanics used to enforce Child blocking decisions.
 *
 * Policy and risk decisions remain outside this class. It only renders or
 * removes the blocking/warning overlay and launches the dedicated blocked
 * screen when requested by the monitoring/enforcement layer.
 */
internal class ChildBlockingUiController(context: Context) {
    private val serviceContext = context
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: View? = null
    private var blockedScreenShowingFor: String? = null
    private var lastBlockedLaunchTime: Long = 0L

    fun launchBlockedScreen(appName: String, appPackage: String, reason: String) {
        val now = System.currentTimeMillis()
        if (blockedScreenShowingFor == appPackage && now - lastBlockedLaunchTime < 3000L) {
            return
        }

        blockedScreenShowingFor = appPackage
        lastBlockedLaunchTime = now

        val intent = Intent(serviceContext, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("appName", appName)
            putExtra("appPackage", appPackage)
            putExtra("reason", reason)
        }
        serviceContext.startActivity(intent)
    }

    fun showBlockOverlay(currentApp: String?) {
        if (overlayView != null) {
            println("Overlay already active")
            return
        }
        if (!Settings.canDrawOverlays(serviceContext)) {
            println("No overlay permission")
            return
        }

        try {
            overlayView = LayoutInflater.from(serviceContext).inflate(R.layout.block_overlay, null)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE
            )
            windowManager.addView(overlayView, params)
            println("✅ Block overlay shown for: $currentApp")
        } catch (error: Exception) {
            println("❌ Error showing overlay: ${error.message}")
            overlayView = null
            error.printStackTrace()
        }
    }

    fun hideOverlay() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
            println("✅ Block overlay hidden")
        } catch (error: Exception) {
            println("❌ Error hiding overlay: ${error.message}")
            error.printStackTrace()
        } finally {
            overlayView = null
        }
    }

    fun showWarningOverlay(currentApp: String?) {
        if (overlayView != null) {
            println("Warning overlay already active")
            return
        }
        if (!Settings.canDrawOverlays(serviceContext)) {
            println("No overlay permission")
            return
        }

        try {
            overlayView = LayoutInflater.from(serviceContext).inflate(R.layout.block_overlay, null)
            overlayView?.findViewById<android.widget.TextView>(R.id.overlayTitle)?.text = "⚠️ Warning"
            overlayView?.findViewById<android.widget.TextView>(R.id.overlayText)?.text =
                "You've been using this app for a while. Consider taking a break."

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE
            )
            windowManager.addView(overlayView, params)
            println("✅ Warning overlay shown for: $currentApp")
        } catch (error: Exception) {
            println("❌ Error showing warning overlay: ${error.message}")
            overlayView = null
            error.printStackTrace()
        }
    }
}
