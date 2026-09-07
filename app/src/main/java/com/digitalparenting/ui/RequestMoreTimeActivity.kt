package com.digitalparenting.ui

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.digitalparenting.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class RequestMoreTimeActivity : EdgeToEdgeActivity() {

    private var selectedMinutes = 15

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_more_time)

        val appName = intent.getStringExtra("appName") ?: "this app"
        val appPackage = intent.getStringExtra("appPackage") ?: "unknown.package"

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvRequestSubtitle).text =
            "Choose how much extra time to request for $appName."

        val btn15 = findViewById<Button>(R.id.btn15Min)
        val btn30 = findViewById<Button>(R.id.btn30Min)
        val btn60 = findViewById<Button>(R.id.btn60Min)
        val btnSend = findViewById<Button>(R.id.btnSendRequest)

        fun select(minutes: Int) {
            selectedMinutes = minutes
            btn15.alpha = if (minutes == 15) 1f else 0.5f
            btn30.alpha = if (minutes == 30) 1f else 0.5f
            btn60.alpha = if (minutes == 60) 1f else 0.5f
        }

        btn15.setOnClickListener { select(15) }
        btn30.setOnClickListener { select(30) }
        btn60.setOnClickListener { select(60) }

        select(15)

        btnSend.setOnClickListener {
            sendTimeRequest(
                appName = appName,
                appPackage = appPackage,
                requestedMinutes = selectedMinutes
            )
        }
    }

    private fun sendTimeRequest(
        appName: String,
        appPackage: String,
        requestedMinutes: Int
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "Child device is not connected to Firebase", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()

        val requestData = hashMapOf(
            "childUid" to user.uid,
            "deviceName" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            "appName" to appName,
            "appPackage" to appPackage,
            "requestedMinutes" to requestedMinutes,
            "approvedMinutes" to null,
            "status" to "pending",
            "parentResponse" to null,
            "createdAt" to FieldValue.serverTimestamp(),
            "resolvedAt" to null
        )

        setBtnSendRequestLoading(true)

        db.collection("time_requests")
            .add(requestData)
            .addOnSuccessListener {
                setBtnSendRequestLoading(false)
                Toast.makeText(
                    this,
                    "Request for $requestedMinutes minutes sent to parent",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
            .addOnFailureListener { _ ->
                setBtnSendRequestLoading(false)
                Toast.makeText(
                    this,
                    "Failed to send request",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun setBtnSendRequestLoading(loading: Boolean) {
        val btnSend = findViewById<Button>(R.id.btnSendRequest)
        btnSend.isEnabled = !loading
        btnSend.text = if (loading) "Sending..." else "Send Request"
    }
}
