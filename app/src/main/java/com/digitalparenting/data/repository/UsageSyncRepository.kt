package com.digitalparenting.data.repository

import android.util.Log
import com.digitalparenting.data.local.AppSessionEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UsageSyncRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun syncSession(session: AppSessionEntity) {
        val user = auth.currentUser
        if (user == null) {
            Log.e("FirebaseSync", "Skipping usage sync because Child authentication is unavailable")
            return
        }
        if (!user.isAnonymous) {
            Log.e("FirebaseSync", "Skipping usage sync because the active identity is not a Child identity")
            return
        }

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
            .document(user.uid)
            .collection("sessions")
            .add(sessionData)
            .addOnSuccessListener {
                Log.d("FirebaseSync", "Session synced successfully")
            }
            .addOnFailureListener { error ->
                Log.e("FirebaseSync", "Sync failed", error)
            }
    }
}
