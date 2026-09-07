package com.digitalparenting.ui

import android.os.Bundle
import android.provider.Settings
import android.widget.ImageButton
import android.widget.TextView
import com.digitalparenting.R
import com.google.firebase.auth.FirebaseAuth

class DiagnosticsActivity : EdgeToEdgeActivity() {

    private lateinit var tvMonitoringStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvFirebaseStatus: TextView
    private lateinit var tvDeviceId: TextView
    private lateinit var tvLastCheck: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        tvMonitoringStatus = findViewById(R.id.tvMonitoringStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvFirebaseStatus = findViewById(R.id.tvFirebaseStatus)
        tvDeviceId = findViewById(R.id.tvDeviceId)
        tvLastCheck = findViewById(R.id.tvLastCheck)
    }

    override fun onResume() {
        super.onResume()
        bindDiagnostics()
    }

    private fun bindDiagnostics() {
        val accessibilityEnabled = isAccessibilityEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)
        val firebaseConnected = FirebaseAuth.getInstance().currentUser != null

        tvMonitoringStatus.text = "Active"
        tvAccessibilityStatus.text = if (accessibilityEnabled) "Enabled" else "Disabled"
        tvOverlayStatus.text = if (overlayEnabled) "Enabled" else "Disabled"
        tvFirebaseStatus.text = if (firebaseConnected) "Connected" else "Not connected"
        tvDeviceId.text = FirebaseAuth.getInstance().currentUser?.uid ?: "Unavailable"
        tvLastCheck.text = java.text.SimpleDateFormat(
            "dd MMM yyyy, hh:mm a",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(packageName, ignoreCase = true)
    }
}