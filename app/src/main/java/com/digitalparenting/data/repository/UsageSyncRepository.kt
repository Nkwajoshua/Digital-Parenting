package com.digitalparenting.data.repository

import android.util.Log
import com.digitalparenting.data.local.AppSessionEntity
import com.google.firebase.firestore.FirebaseFirestore

class UsageSyncRepository {
    private val firestore = FirebaseFirestore.getInstance()

    fun syncSession(childUid: String, session: AppSessionEntity) {
        val sessionData = hashMapOf(
            "packageName" to session.packageName,
            "appName" to session.appName,
            "startTime" to session.startTime,
            "endTime" to session.endTime,
            // Keep the legacy millisecond field for existing readers while
            // publishing the canonical display/analytics unit explicitly.
            "duration" to session.duration,
            "durationSeconds" to (session.duration / 1000L).coerceAtLeast(0L),
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
