package com.digitalparenting.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val childUid = FirebaseAuth.getInstance().currentUser?.uid
        if (childUid.isNullOrBlank()) {
            Log.d("CHILD_BOOT", "Skipping monitoring restart: no authenticated child identity")
            return
        }

        val pendingResult = goAsync()
        FirebaseFirestore.getInstance()
            .collection("children")
            .document(childUid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists() && snapshot.getBoolean("paired") == true) {
                    Log.d("CHILD_BOOT", "Paired child detected after boot; restarting monitoring")
                    context.startForegroundService(Intent(context, MonitoringService::class.java))
                } else {
                    Log.d("CHILD_BOOT", "Skipping monitoring restart: child is not paired")
                }
            }
            .addOnFailureListener { error ->
                Log.w("CHILD_BOOT", "Unable to verify pairing state after boot", error)
            }
            .addOnCompleteListener {
                pendingResult.finish()
            }
    }
}
