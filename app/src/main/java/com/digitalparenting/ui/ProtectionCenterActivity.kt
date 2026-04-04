package com.digitalparenting.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.digitalparenting.R
import com.digitalparenting.data.ProtectionStatus
import com.digitalparenting.ui.adapter.ProtectionIncidentAdapter
import com.digitalparenting.ui.viewmodel.ProtectionCenterViewModel
import com.digitalparenting.util.ProtectionStateManager

class ProtectionCenterActivity : AppCompatActivity() {

    private lateinit var viewModel: ProtectionCenterViewModel
    private lateinit var adapter: ProtectionIncidentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_protection_center)

        viewModel = ViewModelProvider(this)[ProtectionCenterViewModel::class.java]

        // UI elements
        val monitoringStatusText = findViewById<TextView>(R.id.monitoringStatus)
        val accessibilityStatusText = findViewById<TextView>(R.id.accessibilityStatus)
        val overlayStatusText = findViewById<TextView>(R.id.overlayStatus)
        val fixPermissionsButton = findViewById<Button>(R.id.fixPermissionsButton)

        val incidentsRecyclerView = findViewById<RecyclerView>(R.id.incidentsRecyclerView)
        val noIncidentsText = findViewById<TextView>(R.id.noIncidentsText)

        // Action buttons
        val accessibilityButton = findViewById<Button>(R.id.accessibilityButton)
        val overlayButton = findViewById<Button>(R.id.overlayButton)
        val dashboardButton = findViewById<Button>(R.id.dashboardButton)

        // Setup RecyclerView
        adapter = ProtectionIncidentAdapter()
        incidentsRecyclerView.layoutManager = LinearLayoutManager(this)
        incidentsRecyclerView.adapter = adapter

        // Observe protection state
        viewModel.state.observe(this) { state ->
            // Update status indicators
            monitoringStatusText.apply {
                text = if (state.monitoringActive) "✓ Active" else "✗ Inactive"
                setTextColor(if (state.monitoringActive) Color.GREEN else Color.RED)
            }

            accessibilityStatusText.apply {
                text = if (state.accessibilityEnabled) "✓ Enabled" else "✗ Disabled"
                setTextColor(if (state.accessibilityEnabled) Color.GREEN else Color.RED)
            }

            overlayStatusText.apply {
                text = if (state.overlayGranted) "✓ Granted" else "✗ Missing"
                setTextColor(if (state.overlayGranted) Color.GREEN else Color.RED)
            }

            // Show fix button if needed
            fixPermissionsButton.visibility = if (
                !state.accessibilityEnabled || !state.overlayGranted
            ) Button.VISIBLE else Button.GONE

            // Update incidents list
            if (state.recentIncidents.isEmpty()) {
                incidentsRecyclerView.visibility = RecyclerView.GONE
                noIncidentsText.visibility = TextView.VISIBLE
            } else {
                incidentsRecyclerView.visibility = RecyclerView.VISIBLE
                noIncidentsText.visibility = TextView.GONE
                adapter.submitList(state.recentIncidents)
            }
        }

        // Button listeners
        fixPermissionsButton.setOnClickListener {
            if (!ProtectionStateManager.isAccessibilityEnabled(this)) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } else if (!ProtectionStateManager.isOverlayPermissionGranted(this)) {
                startActivity(ProtectionStateManager.buildOverlaySettingsIntent(this))
            }
        }

        accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        overlayButton.setOnClickListener {
            startActivity(ProtectionStateManager.buildOverlaySettingsIntent(this))
        }

        dashboardButton.setOnClickListener {
            startActivity(Intent(this, IntelligenceDashboardActivity::class.java))
        }

        viewModel.clearOldIncidents()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadProtectionState()
    }
}
