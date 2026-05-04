package com.digitalparenting.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.digitalparenting.R
import com.digitalparenting.service.MonitoringService
import com.digitalparenting.ui.adapter.UsageAdapter
import com.digitalparenting.ui.viewmodel.UsageViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: UsageViewModel
    private lateinit var adapter: UsageAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var txtChildUid: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Find and initialize UID display
        txtChildUid = findViewById(R.id.txtChildUid)
        
        signInAnonymously()

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
            viewModel.setLimit("com.whatsapp", 1)
            viewModel.setLimit("com.instagram.android", 1)
            viewModel.setLimit("com.facebook.katana", 1)
            Toast.makeText(this, "Limits set: 1 minute per app", Toast.LENGTH_SHORT).show()
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

        findViewById<Button>(R.id.btnSetLimits).setOnClickListener {
            startActivity(Intent(this, LimitsConfigActivity::class.java))
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

    private fun signInAnonymously() {
        val auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            Log.d("FirebaseAuth", "Already signed in: ${auth.currentUser?.uid}")
            val uid = auth.currentUser?.uid ?: "ERROR"
            Log.e("CHILD_UID_FIRESTORE", "👉 USE THIS UID IN FIRESTORE: $uid 👈")
            txtChildUid.text = uid
            registerChildDevice()
            return
        }

        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Log.d("FirebaseAuth", "Anonymous sign-in success. UID=${user?.uid}")
                    val uid = user?.uid ?: "ERROR"
                    Log.e("CHILD_UID_FIRESTORE", "👉 USE THIS UID IN FIRESTORE: $uid 👈")
                    txtChildUid.text = uid
                    registerChildDevice()
                } else {
                    Log.e("FirebaseAuth", "Anonymous sign-in failed", task.exception)
                    txtChildUid.text = "Auth failed - check internet"
                }
            }
    }

    private fun registerChildDevice() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return

        val db = FirebaseFirestore.getInstance()

        val data = hashMapOf(
            "uid" to user.uid,
            "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "platform" to "android",
            "monitoringActive" to true,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("children")
            .document(user.uid)
            .set(data)
            .addOnSuccessListener {
                Log.d("Firestore", "Child device registered successfully")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Failed to register child device", e)
            }
    }
}