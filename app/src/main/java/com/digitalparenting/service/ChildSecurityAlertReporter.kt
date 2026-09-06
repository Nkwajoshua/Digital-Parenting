package com.digitalparenting.service

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions

/**
 * Sends child-originated protection alerts through the server-authoritative
 * callable control plane. The backend resolves the linked parent and creates
 * the parent notification with Admin SDK privileges.
 */
internal class ChildSecurityAlertReporter(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {
    fun report(type: String, title: String, body: String, severity: String) {
        functions
            .getHttpsCallable("reportChildSecurityAlert")
            .call(
                mapOf(
                    "type" to type,
                    "title" to title,
                    "body" to body,
                    "severity" to severity
                )
            )
            .addOnFailureListener { error ->
                Log.e("ChildSecurityAlert", "Failed to report child security alert", error)
            }
    }
}
