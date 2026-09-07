package com.digitalparenting.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.digitalparenting.util.NotificationHelper
import com.digitalparenting.util.ProtectionStateManager

/**
 * Monitors the Child device permissions that keep enforcement effective.
 *
 * This component owns permission-health checks and alert deduplication only.
 * Block-state decisions and persistence remain in the monitoring/enforcement
 * layer.
 */
internal class ChildProtectionHealthController(
    context: Context,
    private val notificationHelper: NotificationHelper,
    private val onIncident: (
        type: String,
        title: String,
        message: String,
        appPackage: String?
    ) -> Unit,
    private val securityAlertReporter: ChildSecurityAlertReporter = ChildSecurityAlertReporter()
) {
    private val appContext = context.applicationContext
    private var accessibilityAlertShown = false
    private var overlayAlertShown = false

    fun verify() {
        val accessibilityEnabled = ProtectionStateManager.isAccessibilityEnabled(appContext)
        val overlayGranted = ProtectionStateManager.isOverlayPermissionGranted(appContext)

        if (!accessibilityEnabled) {
            if (!accessibilityAlertShown) {
                launchAccessibilitySettings()
                notificationHelper.showSecurityAlert(
                    "Protection Weakened",
                    "Accessibility service is disabled. Re-enable it to continue protection."
                )
                onIncident(
                    "accessibility_disabled",
                    "Protection Weakened",
                    "Accessibility service is disabled. Re-enable it to continue protection.",
                    null
                )
                securityAlertReporter.report(
                    type = "permissions_revoked",
                    title = "Accessibility Disabled",
                    body = "Accessibility was disabled on child device.",
                    severity = "critical"
                )
                accessibilityAlertShown = true
            }
        } else {
            accessibilityAlertShown = false
        }

        if (!overlayGranted) {
            if (!overlayAlertShown) {
                appContext.startActivity(ProtectionStateManager.buildOverlaySettingsIntent(appContext))
                notificationHelper.showSecurityAlert(
                    "Overlay Permission Missing",
                    "Overlay permission is required for app blocking."
                )
                onIncident(
                    "overlay_missing",
                    "Overlay Permission Missing",
                    "Overlay permission is required for app blocking.",
                    null
                )
                securityAlertReporter.report(
                    type = "permissions_revoked",
                    title = "Overlay Permission Revoked",
                    body = "Overlay permission was revoked on child device.",
                    severity = "warning"
                )
                overlayAlertShown = true
            }
        } else {
            overlayAlertShown = false
        }
    }

    private fun launchAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }
}
