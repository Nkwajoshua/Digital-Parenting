package com.digitalparenting.service

import android.util.Log
import com.google.firebase.auth.FirebaseAuth

/**
 * Owns the Child runtime Firebase Auth lifecycle.
 *
 * The coordinator preserves the existing behavior: use the current Firebase
 * identity when present, otherwise retry anonymous sign-in, and notify the
 * service when authentication becomes available.
 */
internal class ChildAuthCoordinator(
    private val onAuthenticated: () -> Unit,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    fun start() {
        stop()

        val uid = auth.currentUser?.uid
        if (uid != null) {
            Log.d("CHILD_AUTH", "MonitoringService boot with UID=$uid")
        } else {
            Log.w("CHILD_AUTH", "MonitoringService boot without user; waiting for auth")
        }

        val listener = FirebaseAuth.AuthStateListener { currentAuth ->
            val user = currentAuth.currentUser
            if (user != null) {
                Log.d("CHILD_AUTH", "MonitoringService auth ready UID=${user.uid}")
                onAuthenticated()
            } else {
                Log.w("CHILD_AUTH", "MonitoringService auth unavailable; retrying anonymous sign-in")
                auth.signInAnonymously()
                    .addOnSuccessListener { result ->
                        Log.d(
                            "CHILD_AUTH",
                            "MonitoringService anonymous sign-in success UID=${result.user?.uid}"
                        )
                    }
                    .addOnFailureListener { error ->
                        Log.e("CHILD_AUTH", "MonitoringService anonymous sign-in failed", error)
                    }
            }
        }

        authStateListener = listener
        auth.addAuthStateListener(listener)
    }

    fun stop() {
        authStateListener?.let { auth.removeAuthStateListener(it) }
        authStateListener = null
    }
}
