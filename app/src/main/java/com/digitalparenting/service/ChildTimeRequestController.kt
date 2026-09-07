package com.digitalparenting.service

import android.util.Log
import com.digitalparenting.data.local.AppLimit
import com.digitalparenting.data.local.AppLimitDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns Child-side approved time-request consumption and application.
 *
 * Parent approval remains server-authoritative. This component listens only for
 * approved requests addressed to the authenticated anonymous Child identity,
 * applies the approved minutes to local limits, and acknowledges the request as
 * applied in Firestore.
 */
internal class ChildTimeRequestController(
    private val limitDao: AppLimitDao,
    private val onTimeApplied: (approvedMinutes: Int, appName: String) -> Unit,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private var listener: ListenerRegistration? = null

    fun start() {
        val user = auth.currentUser
        if (user == null) {
            Log.d("TimeRequest", "No Firebase user. Skipping time request listener.")
            stop()
            return
        }
        if (!user.isAnonymous) {
            Log.e("TimeRequest", "Active identity is not a Child identity. Time request listener not started.")
            stop()
            return
        }

        stop()
        listener = firestore.collection("time_requests")
            .whereEqualTo("childUid", user.uid)
            .whereEqualTo("status", "approved")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("TimeRequest", "Listener failed", error)
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) return@addSnapshotListener

                for (doc in snapshots.documents) {
                    val appPackage = doc.getString("appPackage") ?: continue
                    val appName = doc.getString("appName") ?: appPackage
                    val approvedMinutesLong =
                        doc.getLong("approvedMinutes") ?: doc.getLong("requestedMinutes") ?: 0L
                    val approvedMinutes = approvedMinutesLong.toInt()

                    if (approvedMinutes <= 0) continue

                    applyApprovedRequest(
                        requestId = doc.id,
                        appPackage = appPackage,
                        appName = appName,
                        approvedMinutes = approvedMinutes
                    )
                }
            }
    }

    fun stop() {
        listener?.remove()
        listener = null
    }

    private fun applyApprovedRequest(
        requestId: String,
        appPackage: String,
        appName: String,
        approvedMinutes: Int
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val existingLimit = limitDao.getLimit(appPackage)

                if (existingLimit != null) {
                    val newMinutes = existingLimit.maxMinutes + approvedMinutes
                    limitDao.setLimit(
                        existingLimit.copy(
                            appName = if (existingLimit.appName.isBlank()) appName else existingLimit.appName,
                            maxMinutes = newMinutes,
                            enabled = true
                        )
                    )
                    Log.d(
                        "TimeRequest",
                        "Applied approved request: $appPackage +$approvedMinutes min -> $newMinutes min"
                    )
                } else {
                    limitDao.setLimit(
                        AppLimit(
                            packageName = appPackage,
                            appName = appName,
                            maxMinutes = approvedMinutes,
                            enabled = true,
                            lastReset = System.currentTimeMillis()
                        )
                    )
                    Log.d(
                        "TimeRequest",
                        "Created new limit from approved request: $appPackage = $approvedMinutes min"
                    )
                }

                firestore.collection("time_requests")
                    .document(requestId)
                    .update(
                        mapOf(
                            "status" to "applied",
                            "appliedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .addOnFailureListener { error ->
                        Log.e("TimeRequest", "Failed to acknowledge applied request $requestId", error)
                    }

                withContext(Dispatchers.Main) {
                    onTimeApplied(approvedMinutes, appName)
                }
            } catch (error: Exception) {
                Log.e("TimeRequest", "Failed to apply approved request $requestId", error)
            }
        }
    }
}
