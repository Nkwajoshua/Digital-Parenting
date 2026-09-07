package com.digitalparenting.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.digitalparenting.service.MonitoringService
import com.digitalparenting.util.AccessibilityConsentState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            Log.d("CHILD_AUTH", "Existing authenticated user UID=${currentUser.uid}")
            continueStartupFlow(currentUser.uid)
            return
        }

        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid.isNullOrBlank()) {
                    showAuthFailure()
                    return@addOnSuccessListener
                }

                Log.d("CHILD_AUTH", "Anonymous sign-in success UID=$uid")
                continueStartupFlow(uid)
            }
            .addOnFailureListener { error ->
                Log.e("CHILD_AUTH", "Anonymous sign-in failed", error)
                showAuthFailure()
            }
    }

    private fun continueStartupFlow(childUid: String) {
        FirebaseFirestore.getInstance()
            .collection("children")
            .document(childUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val paired = snapshot.exists() && snapshot.getBoolean("paired") == true

                if (paired && !AccessibilityConsentState.hasCurrentConsent(this)) {
                    Log.d(
                        "CHILD_AUTH",
                        "Paired child requires current Accessibility disclosure consent"
                    )
                    startActivity(
                        Intent(this, PermissionsSetupActivity::class.java)
                            .putExtra(PermissionsSetupActivity.EXTRA_STEP, 1)
                    )
                } else if (paired) {
                    Log.d("CHILD_AUTH", "Paired child detected; starting monitoring service")
                    startForegroundService(Intent(this, MonitoringService::class.java))
                    startActivity(Intent(this, HomeStatusActivity::class.java))
                } else {
                    Log.d("CHILD_AUTH", "Unpaired child detected; opening pairing flow")
                    startActivity(Intent(this, WelcomeActivity::class.java))
                }

                finish()
            }
            .addOnFailureListener { error ->
                Log.e("CHILD_AUTH", "Unable to resolve child pairing state", error)
                startActivity(Intent(this, WelcomeActivity::class.java))
                finish()
            }
    }

    private fun showAuthFailure() {
        Toast.makeText(
            this,
            "Authentication failed. Please relaunch the app.",
            Toast.LENGTH_LONG
        ).show()
    }
}
