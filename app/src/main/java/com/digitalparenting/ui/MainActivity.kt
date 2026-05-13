package com.digitalparenting.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.digitalparenting.service.MonitoringService
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            Log.d("CHILD_AUTH", "Existing authenticated user UID=${currentUser.uid}")
            continueStartupFlow()
            return
        }

        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: "UNKNOWN"
                Log.d("CHILD_AUTH", "Anonymous sign-in success UID=$uid")
                continueStartupFlow()
            }
            .addOnFailureListener { error ->
                Log.e("CHILD_AUTH", "Anonymous sign-in failed", error)
                Toast.makeText(
                    this,
                    "Authentication failed. Please relaunch the app.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun continueStartupFlow() {
        Log.d("CHILD_AUTH", "MonitoringService starting after auth")
        startForegroundService(Intent(this, MonitoringService::class.java))
        startActivity(Intent(this, WelcomeActivity::class.java))
        finish()
    }
}
