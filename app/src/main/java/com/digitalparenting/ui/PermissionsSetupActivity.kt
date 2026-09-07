package com.digitalparenting.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import com.digitalparenting.R
import com.digitalparenting.service.MonitoringService

class PermissionsSetupActivity : EdgeToEdgeActivity() {

    companion object {
        const val EXTRA_STEP = "extra_step"
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
                tvDescription.text = "This permission is required to monitor app usage, enforce time limits, and block content safely."
                tvPrivacyTitle.text = "Data Privacy"
                tvPrivacyLine1.text = "• We only monitor app usage time and blocked screen overlays."
                tvPrivacyLine2.text = "• We never read messages, passwords, or personal content."
                tvPermissionLabel.text = "Accessibility Service"
                btnAction.text = "Enable in Settings"
            }

            2 -> {
                tvTitle.text = "Enable Display Over Apps"
                tvDescription.text = "This permission is needed to show the blocked screen when app limits are reached."
                tvPrivacyTitle.text = "Why this matters"
                tvPrivacyLine1.text = "• The block screen must appear above restricted apps."
                tvPrivacyLine2.text = "• This is only used for enforcement screens."
                tvPermissionLabel.text = "Overlay Permission"
                btnAction.text = "Grant Permission"
            }

            3 -> {
                tvTitle.text = "Final Diagnostics"
                tvDescription.text = "We're checking that monitoring, permissions, and cloud sync are all ready."
                tvPrivacyTitle.text = "Background requirements"
                tvPrivacyLine1.text = "• Notifications help keep protection active."
                tvPrivacyLine2.text = "• Battery optimization may need adjustment later."
                tvPermissionLabel.text = "System Readiness"
                btnAction.text = "Finish Setup"
            }
        }

        btnAction.setOnClickListener {
            when (step) {
                1 -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                2 -> {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                3 -> {
                    startActivity(Intent(this, HomeStatusActivity::class.java))
                    finishAffinity()
                }
            }
        }

        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        when (step) {
            1 -> {
                val enabled = isAccessibilityEnabled()
                tvPermissionStatus.text = if (enabled) "ENABLED" else "PENDING"
                if (enabled) btnAction.text = "Continue"
                btnAction.setOnClickListener {
                    if (enabled) {
                        openStep(2)
                    } else {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }
            }

            2 -> {
                val enabled = Settings.canDrawOverlays(this)
                tvPermissionStatus.text = if (enabled) "ENABLED" else "PENDING"
                if (enabled) btnAction.text = "Continue"
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

            3 -> {
                val ready = isAccessibilityEnabled() && Settings.canDrawOverlays(this)
                tvPermissionStatus.text = if (ready) "READY" else "CHECKING"
                btnAction.text = if (ready) "Finish Setup" else "Review Setup"
                btnAction.setOnClickListener {
                    if (ready) {
                        startForegroundService(Intent(this, MonitoringService::class.java))
                        startActivity(Intent(this, HomeStatusActivity::class.java))
                        finishAffinity()
                    } else {
                        openStep(1)
                    }
                }
            }
        }
    }

    private fun openStep(targetStep: Int) {
        val intent = Intent(this, PermissionsSetupActivity::class.java)
        intent.putExtra(EXTRA_STEP, targetStep)
        startActivity(intent)
        finish()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(packageName, ignoreCase = true)
    }
}
