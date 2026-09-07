package com.digitalparenting.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.digitalparenting.util.ProtectionStateManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Owns Child device/status publishing independently of the monitoring runtime.
 *
 * This component deliberately writes only fields permitted by the Child status
 * Firestore rule. Pairing and ownership remain server-authoritative.
 */
internal class ChildStatusPublisher(
    context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val heartbeatIntervalMillis: Long = 60_000L
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val appVersion: String by lazy { resolveAppVersion() }
    private var running = false

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            publishHeartbeat()
            if (running) handler.postDelayed(this, heartbeatIntervalMillis)
        }
    }

    fun publishInitialStatus() {
        val user = auth.currentUser ?: return
        firestore.collection("children")
            .document(user.uid)
            .update(
                mapOf(
                    "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}",
                    "platform" to "android",
                    "monitoringActive" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { error ->
                Log.w("CHILD_STATUS", "Unable to publish initial child status", error)
            }
    }

    fun start() {
        if (running) return
        running = true
        handler.post(heartbeatRunnable)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(heartbeatRunnable)
    }

    private fun publishHeartbeat() {
        val user = auth.currentUser ?: return
        val batteryStatus = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt()
        } else {
            null
        }
        val batteryState = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = batteryState == BatteryManager.BATTERY_STATUS_CHARGING ||
            batteryState == BatteryManager.BATTERY_STATUS_FULL

        val payload = linkedMapOf<String, Any>(
            "lastHeartbeatAt" to FieldValue.serverTimestamp(),
            "monitoringActive" to true,
            "charging" to charging,
            "appVersion" to appVersion,
            "deviceTime" to System.currentTimeMillis(),
            "accessibilityEnabled" to ProtectionStateManager.isAccessibilityEnabled(appContext),
            "overlayPermissionGranted" to ProtectionStateManager.isOverlayPermissionGranted(appContext),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (batteryPct != null) payload["batteryLevel"] = batteryPct

        firestore.collection("children")
            .document(user.uid)
            .update(payload)
            .addOnFailureListener { error ->
                Log.w("CHILD_STATUS", "Unable to publish child heartbeat", error)
            }
    }

    @Suppress("DEPRECATION")
    private fun resolveAppVersion(): String {
        return try {
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName
                .orEmpty()
                .ifBlank { "unknown" }
        } catch (error: Exception) {
            Log.w("CHILD_STATUS", "Unable to resolve app version", error)
            "unknown"
        }
    }
}
