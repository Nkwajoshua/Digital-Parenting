package com.digitalparenting.data.repository

import com.digitalparenting.data.local.AppSessionEntity
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log

class UsageSyncRepository {
    private val firestore = FirebaseFirestore.getInstance()

    fun syncSession(childUid: String, session: AppSessionEntity) {
        // TODO(parent-dashboard): Build usage and analytics views from usage_sessions/{childUid}/sessions in parent web/app.
        val sessionData = hashMapOf(
            "packageName" to session.packageName,
            "appName" to session.appName,
            "startTime" to session.startTime,
            "endTime" to session.endTime,
            "duration" to session.duration,
            "syncedAt" to System.currentTimeMillis()
        )

        firestore.collection("usage_sessions")
            .document(childUid)
            .collection("sessions")
            .add(sessionData)
            .addOnSuccessListener {
                Log.d("FirebaseSync", "Session synced successfully")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseSync", "Sync failed", e)
            }
    }
}
