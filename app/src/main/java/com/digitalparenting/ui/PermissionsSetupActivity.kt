package com.digitalparenting.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.digitalparenting.R
import com.digitalparenting.service.MonitoringService

class PermissionsSetupActivity : EdgeToEdgeActivity() {

    companion object {
        const val EXTRA_STEP = "extra_step"

        private const val PREFS_NAME = "child_permission_setup"
        private const val KEY_ACCESSIBILITY_CONSENT_VERSION = "accessibility_consent_version"
        private const val ACCESSIBILITY_CONSENT_VERSION = 1
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    }

    private lateinit var tvStep: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvPrivacyTitle: TextView
    private lateinit var tvPrivacyLine1: TextView
    private lateinit var tvPrivacyLine2: TextView
    private lateinit var tvPermissionLabel: TextView
    private lateinit var tvPermissionStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnAction: Button
    private lateinit var btnBack: ImageButton

    private var step: Int = 1

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updatePermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions_setup)

        step = intent.getIntExtra(EXTRA_STEP, 1)

        tvStep = findViewById(R.id.tvStep)
        tvTitle = findViewById(R.id.tvPermissionTitle)
        tvDescription = findViewById(R.id.tvPermissionDescription)
        tvPrivacyTitle = findViewById(R.id.tvPrivacyTitle)
        tvPrivacyLine1 = findViewById(R.id.tvPrivacyLine1)
        tvPrivacyLine2 = findViewById(R.id.tvPrivacyLine2)
        tvPermissionLabel = findViewById(R.id.tvPermissionLabel)
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        progressBar = findViewById(R.id.progressSetup)
        btnAction = findViewById(R.id.btnPermissionAction)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener {
            if (step > 1) {
                openStep(step - 1)
            } else {
                finish()
            }
        }

        bindStep(step)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun bindStep(step: Int) {
        progressBar.max = 3
        progressBar.progress = step
        tvStep.text = "Step $step of 3"

        when (step) {
            1 -> {
                tvTitle.text = "Enable Accessibility"
                tvDescription.text = "Accessibility lets Digital Parenting identify the active app and enforce parental-control blocks on this Child device."
                tvPrivacyTitle.text = "Disclosure required"
                tvPrivacyLine1.text = "• You'll review exactly what Accessibility can observe before Android Settings opens."
                tvPrivacyLine2.text = "• Consent is separate from the Android permission and can be declined."
                tvPermissionLabel.text = "Accessibility Service"
            }

            2 -> {
                tvTitle.text = "Enable Display Over Apps"
                tvDescription.text = "This permission is needed to show the blocked screen when app limits are reached."
                tvPrivacyTitle.text = "Why this matters"
                tvPrivacyLine1.text = "• The block screen must appear above restricted apps."
                tvPrivacyLine2.text = "• This is only used for enforcement screens."
                tvPermissionLabel.text = "Overlay Permission"
            }

            3 -> {
                tvTitle.text = "Enable Notifications"
                tvDescription.text = "Notifications keep the ongoing protection status visible and surface important Child-device alerts."
                tvPrivacyTitle.text = "Your choice"
                tvPrivacyLine1.text = "• Android 13+ asks you whether Digital Parenting may show notifications."
                tvPrivacyLine2.text = "• If you decline, protection can still run, but notification-drawer visibility is reduced."
                tvPermissionLabel.text = "Notification Permission"
            }
        }

        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        when (step) {
            1 -> bindAccessibilityStep()
            2 -> bindOverlayStep()
            3 -> bindNotificationStep()
        }
    }

    private fun bindAccessibilityStep() {
        val enabled = isAccessibilityEnabled()
        val consented = hasAccessibilityDisclosureConsent()

        tvPermissionStatus.text = when {
            enabled && consented -> "ENABLED"
            enabled -> "DISCLOSURE REVIEW REQUIRED"
            else -> "PENDING"
        }

        btnAction.text = when {
            enabled && consented -> "Continue"
            enabled -> "Review Disclosure"
            consented -> "Enable in Settings"
            else -> "Review & Enable"
        }

        btnAction.setOnClickListener {
            when {
                !consented -> {
                    showAccessibilityDisclosure {
                        if (enabled) {
                            openStep(2)
                        } else {
                            openAccessibilitySettings()
                        }
                    }
                }

                enabled -> openStep(2)
                else -> openAccessibilitySettings()
            }
        }
    }

    private fun bindOverlayStep() {
        val enabled = Settings.canDrawOverlays(this)
        tvPermissionStatus.text = if (enabled) "ENABLED" else "PENDING"
        btnAction.text = if (enabled) "Continue" else "Grant Permission"
        btnAction.setOnClickListener {
            if (enabled) {
                openStep(3)
            } else {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    private fun bindNotificationStep() {
        val coreReady = isAccessibilityEnabled() && Settings.canDrawOverlays(this)
        val notificationGranted = hasNotificationPermission()
        val notificationRequested = hasRequestedNotificationPermission()

        if (!coreReady) {
            tvPermissionStatus.text = "SETUP REQUIRED"
            btnAction.text = "Review Setup"
            btnAction.setOnClickListener { openStep(1) }
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            tvPermissionStatus.text = "NOT REQUIRED"
            btnAction.text = "Finish Setup"
            btnAction.setOnClickListener { finishSetup() }
            return
        }

        when {
            notificationGranted -> {
                tvPermissionStatus.text = "ENABLED"
                btnAction.text = "Finish Setup"
                btnAction.setOnClickListener { finishSetup() }
            }

            !notificationRequested -> {
                tvPermissionStatus.text = "NOT ENABLED"
                btnAction.text = "Enable Notifications"
                btnAction.setOnClickListener {
                    markNotificationPermissionRequested()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            else -> {
                tvPermissionStatus.text = "NOTIFICATIONS OFF"
                btnAction.text = "Finish Without Notifications"
                btnAction.setOnClickListener { finishSetup() }
            }
        }
    }

    private fun showAccessibilityDisclosure(onAccepted: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Accessibility Service disclosure")
            .setMessage(
                "Digital Parenting uses Android AccessibilityService on this Child device to observe which app/window becomes active and to enforce parental-control blocks by returning the device away from a restricted app.\n\n" +
                    "The AccessibilityService is not used to read messages, passwords, typed text, or screen contents, and window-content retrieval is disabled. Accessibility event contents are not sent to the Parent account.\n\n" +
                    "Tap ‘I agree’ only if you understand and consent to this use. If you choose ‘Not now’ or dismiss this message, Android Accessibility settings will not open."
            )
            .setNegativeButton("Not now", null)
            .setPositiveButton("I agree") { _, _ ->
                recordAccessibilityDisclosureConsent()
                onAccepted()
            }
            .setCancelable(true)
            .show()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun finishSetup() {
        startForegroundService(Intent(this, MonitoringService::class.java))
        startActivity(Intent(this, HomeStatusActivity::class.java))
        finishAffinity()
    }

    private fun openStep(targetStep: Int) {
        val intent = Intent(this, PermissionsSetupActivity::class.java)
        intent.putExtra(EXTRA_STEP, targetStep)
        startActivity(intent)
        finish()
    }

    private fun hasAccessibilityDisclosureConsent(): Boolean {
        return permissionPrefs().getInt(KEY_ACCESSIBILITY_CONSENT_VERSION, 0) >=
            ACCESSIBILITY_CONSENT_VERSION
    }

    private fun recordAccessibilityDisclosureConsent() {
        permissionPrefs()
            .edit()
            .putInt(KEY_ACCESSIBILITY_CONSENT_VERSION, ACCESSIBILITY_CONSENT_VERSION)
            .apply()
    }

    private fun hasRequestedNotificationPermission(): Boolean {
        return permissionPrefs().getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
    }

    private fun markNotificationPermissionRequested() {
        permissionPrefs()
            .edit()
            .putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true)
            .apply()
    }

    private fun permissionPrefs() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(packageName, ignoreCase = true)
    }
}
