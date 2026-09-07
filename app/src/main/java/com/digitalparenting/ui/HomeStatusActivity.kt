package com.digitalparenting.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import com.digitalparenting.R
import com.google.firebase.auth.FirebaseAuth

class HomeStatusActivity : EdgeToEdgeActivity() {

    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvCloudStatus: TextView
    private lateinit var tvDeviceName: TextView
    private lateinit var tvAndroidVersion: TextView
    private lateinit var tvAgentId: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_status)

        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvCloudStatus = findViewById(R.id.tvCloudStatus)
        tvDeviceName = findViewById(R.id.tvDeviceName)
        tvAndroidVersion = findViewById(R.id.tvAndroidVersion)
        tvAgentId = findViewById(R.id.tvAgentId)

        findViewById<Button>(R.id.btnForceSync).setOnClickListener {
            // TODO: connect to your real sync method
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

    private fun bindStatus() {
        val accessibilityEnabled = isAccessibilityEnabled()
        val overlayEnabled = Settings.canDrawOverlays(this)
        val firebaseConnected = FirebaseAuth.getInstance().currentUser != null

        tvAccessibilityStatus.text = if (accessibilityEnabled) "ON" else "OFF"
        tvOverlayStatus.text = if (overlayEnabled) "ON" else "OFF"
        tvCloudStatus.text = if (firebaseConnected) "SYNCED" else "NOT CONNECTED"

        tvDeviceName.text = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        tvAndroidVersion.text = "Android ${android.os.Build.VERSION.RELEASE}"
        tvAgentId.text = FirebaseAuth.getInstance().currentUser?.uid?.take(10)?.plus("...") ?: "Not linked"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.contains(packageName, ignoreCase = true)
    }
}