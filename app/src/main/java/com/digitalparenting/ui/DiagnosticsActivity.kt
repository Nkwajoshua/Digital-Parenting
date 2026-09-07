package com.digitalparenting.ui

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import com.digitalparenting.R
import com.digitalparenting.service.MonitoringService
import com.digitalparenting.util.ChildStatusSyncState
import com.digitalparenting.util.ProtectionStateManager
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        val user = FirebaseAuth.getInstance().currentUser
        val syncStatus = ChildStatusSyncState.status(this, user?.uid)
        val lastSyncAt = ChildStatusSyncState.lastSuccessfulSyncAt(this, user?.uid)

        tvMonitoringStatus.text = if (MonitoringService.isRunning) "Running" else "Stopped"
        tvAccessibilityStatus.text = if (ProtectionStateManager.isAccessibilityEnabled(this)) {
            "Enabled"
        } else {
            "Disabled"
        }
        tvOverlayStatus.text = if (ProtectionStateManager.isOverlayPermissionGranted(this)) {
            "Enabled"
        } else {
            "Disabled"
        }
        tvFirebaseStatus.text = when (syncStatus) {
            ChildStatusSyncState.Status.NOT_AUTHENTICATED -> "Not authenticated"
            ChildStatusSyncState.Status.WAITING -> "Authenticated · waiting for status sync"
            ChildStatusSyncState.Status.SYNCED -> "Authenticated · status synced"
            ChildStatusSyncState.Status.STALE -> "Authenticated · status sync stale"
        }
        tvDeviceId.text = user?.uid ?: "Unavailable"
        tvLastCheck.text = if (lastSyncAt > 0L) {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(lastSyncAt))
        } else {
            "No successful status write yet"
        }
    }
}
