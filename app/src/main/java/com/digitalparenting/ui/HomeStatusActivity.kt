package com.digitalparenting.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.digitalparenting.R
import com.digitalparenting.service.MonitoringService
import com.digitalparenting.util.ChildStatusSyncState
import com.digitalparenting.util.ProtectionStateManager
import com.google.firebase.auth.FirebaseAuth

class HomeStatusActivity : EdgeToEdgeActivity() {

    private lateinit var tvAgentStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvCloudStatus: TextView
    private lateinit var tvDeviceName: TextView
    private lateinit var tvAndroidVersion: TextView
    private lateinit var tvAgentId: TextView
    private lateinit var btnRefreshStatus: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_status)

        tvAgentStatus = findViewById(R.id.tvAgentStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvCloudStatus = findViewById(R.id.tvCloudStatus)
        tvDeviceName = findViewById(R.id.tvDeviceName)
        tvAndroidVersion = findViewById(R.id.tvAndroidVersion)
        tvAgentId = findViewById(R.id.tvAgentId)
        btnRefreshStatus = findViewById(R.id.btnForceSync)

        btnRefreshStatus.setOnClickListener {
            requestStatusRefresh()
        }

        findViewById<Button>(R.id.btnVerifyPermissions).setOnClickListener {
            startActivity(
                Intent(this, PermissionsSetupActivity::class.java)
                    .putExtra(PermissionsSetupActivity.EXTRA_STEP, 1)
            )
        }

        findViewById<Button>(R.id.btnOpenDiagnostics).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenAlerts).setOnClickListener {
            startActivity(Intent(this, ActivityAlertsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        bindStatus()
    }

    private fun requestStatusRefresh() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Child is not authenticated", Toast.LENGTH_SHORT).show()
            bindStatus()
            return
        }

        btnRefreshStatus.isEnabled = false
        btnRefreshStatus.text = "Refreshing..."

        ContextCompat.startForegroundService(
            this,
            Intent(this, MonitoringService::class.java)
                .setAction(MonitoringService.ACTION_REFRESH_STATUS)
        )

        window.decorView.postDelayed({
            bindStatus()
            btnRefreshStatus.isEnabled = true
            btnRefreshStatus.text = "Refresh Status"
        }, REFRESH_RECHECK_DELAY_MILLIS)
    }

    private fun bindStatus() {
        val accessibilityEnabled = ProtectionStateManager.isAccessibilityEnabled(this)
        val overlayEnabled = ProtectionStateManager.isOverlayPermissionGranted(this)
        val user = FirebaseAuth.getInstance().currentUser

        tvAgentStatus.text = when {
            !MonitoringService.isRunning -> "INACTIVE"
            accessibilityEnabled && overlayEnabled -> "PROTECTING"
            else -> "DEGRADED"
        }

        tvAccessibilityStatus.text = if (accessibilityEnabled) "ON" else "OFF"
        tvOverlayStatus.text = if (overlayEnabled) "ON" else "OFF"
        tvCloudStatus.text = when (ChildStatusSyncState.status(this, user?.uid)) {
            ChildStatusSyncState.Status.NOT_AUTHENTICATED -> "NOT AUTHENTICATED"
            ChildStatusSyncState.Status.WAITING -> "WAITING"
            ChildStatusSyncState.Status.SYNCED -> "SYNCED"
            ChildStatusSyncState.Status.STALE -> "STALE"
        }

        tvDeviceName.text = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        tvAndroidVersion.text = "Android ${android.os.Build.VERSION.RELEASE}"
        tvAgentId.text = user?.uid?.take(10)?.plus("...") ?: "Not linked"
    }

    companion object {
        private const val REFRESH_RECHECK_DELAY_MILLIS = 1_500L
    }
}
