package com.digitalparenting.service

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Registers the current Firebase Messaging installation token on the paired
 * Child document. Firestore rules restrict this write to the authenticated
 * anonymous Child identity and the fcmToken metadata fields only.
 */
internal class ChildFcmTokenRegistrar(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()
) {
    fun registerCurrentToken() {
        val user = auth.currentUser
        if (user == null) {
            Log.w("CHILD_FCM", "Cannot register FCM token without authenticated Child")
            return
        }
        if (!user.isAnonymous) {
            Log.e("CHILD_FCM", "Refusing FCM token registration for non-Child identity")
            return
        }

        messaging.token
            .addOnSuccessListener { token -> registerToken(token) }
            .addOnFailureListener { error ->
                Log.w("CHILD_FCM", "Unable to obtain current FCM token", error)
            }
    }

    fun registerToken(token: String) {
        if (token.isBlank()) {
            Log.w("CHILD_FCM", "Ignoring blank FCM token")
            return
        }

        val user = auth.currentUser
        if (user == null) {
            Log.w("CHILD_FCM", "FCM token refreshed before Child authentication; runtime registration will retry")
            return
        }
        if (!user.isAnonymous) {
            Log.e("CHILD_FCM", "Refusing refreshed FCM token for non-Child identity")
            return
        }

        firestore.collection("children")
            .document(user.uid)
            .update(
                mapOf(
                    "fcmToken" to token,
                    "fcmTokenUpdatedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                Log.d("CHILD_FCM", "FCM token registered for Child UID=${user.uid}")
            }
            .addOnFailureListener { error ->
                Log.w(
                    "CHILD_FCM",
                    "Unable to persist FCM token; paired runtime registration will retry",
                    error
                )
            }
    }
}
