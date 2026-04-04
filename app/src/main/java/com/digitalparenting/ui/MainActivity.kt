package com.digitalparenting.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.digitalparenting.R
import com.digitalparenting.service.MonitoringService
import com.digitalparenting.ui.adapter.UsageAdapter
import com.digitalparenting.ui.viewmodel.UsageViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: UsageViewModel
    private lateinit var adapter: UsageAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[UsageViewModel::class.java]

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.usageRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UsageAdapter()
        recyclerView.adapter = adapter

        // Observe LiveData
        viewModel.usageStats.observe(this) { stats ->
            adapter.submitList(stats)
        }

        // Set test limit (1 minute = 60000 ms) - for demonstration
        val setLimitButton = findViewById<Button>(R.id.setLimitButton)
        setLimitButton?.setOnClickListener {
            // Set 1 minute limit for all apps as test
            viewModel.setLimit("com.whatsapp", 60000)
            viewModel.setLimit("com.instagram.android", 60000)
            viewModel.setLimit("com.facebook.katana", 60000)
            Toast.makeText(this, "Limits set: 60s per app", Toast.LENGTH_SHORT).show()
        }

        checkUsagePermission()
        checkOverlayPermission()
        requestNotificationPermissionIfNeeded()

        // Start monitoring service
        val intent = Intent(this, MonitoringService::class.java)
        startForegroundService(intent)

        findViewById<Button>(R.id.openIntelligenceDashboardButton).setOnClickListener {
            startActivity(Intent(this, IntelligenceDashboardActivity::class.java))
        }

        findViewById<Button>(R.id.openProtectionCenterButton).setOnClickListener {
            startActivity(Intent(this, ProtectionCenterActivity::class.java))
        }

        // Load initial data
        viewModel.loadUsageStats()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when activity resumes
        viewModel.loadUsageStats()
    }

    private fun checkUsagePermission() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            "android:get_usage_stats",
            android.os.Process.myUid(),
            packageName
        )

        if (mode != AppOpsManager.MODE_ALLOWED) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(intent)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }
    }
}