package com.digitalparenting.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.digitalparenting.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class PairingCodeActivity : AppCompatActivity() {

    private lateinit var pairingCodeInput: EditText
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing_code)

        pairingCodeInput = findViewById(R.id.etPairingCode)
        pairingCodeInput.filters = arrayOf(InputFilter.LengthFilter(6))

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnLinkDevice).setOnClickListener {
            val code = pairingCodeInput.text.toString().trim()
            if (code.length != 6) {
                Toast.makeText(this, "Enter a valid 6-digit pairing code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            linkWithCode(code)
        }
    }

    private fun linkWithCode(code: String) {
        val childUid = FirebaseAuth.getInstance().currentUser?.uid
        if (childUid.isNullOrBlank()) {
            Toast.makeText(this, "Child authentication missing", Toast.LENGTH_SHORT).show()
            return
        }

        val codeRef = firestore.collection("pairing_codes").document(code)
        val childRef = firestore.collection("children").document(childUid)

        firestore.runTransaction { transaction ->
            val snap = transaction.get(codeRef)
            if (!snap.exists()) {
                throw IllegalStateException("Pairing code not found")
            }

            val status = snap.getString("status") ?: ""
            val expiresAt = snap.getTimestamp("expiresAt")
            val parentUid = snap.getString("parentUid")
            val expired = expiresAt == null || expiresAt.toDate().time <= System.currentTimeMillis()

            if (status != "pending" || expired || parentUid.isNullOrBlank()) {
                throw IllegalStateException("Pairing code invalid or expired")
            }

            transaction.set(
                childRef,
                mapOf(
                    "parentUid" to parentUid,
                    "paired" to true,
                    "pairedAt" to FieldValue.serverTimestamp(),
                    "pairingCode" to code,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )

            transaction.update(
                codeRef,
                mapOf(
                    "status" to "used",
                    "usedByChildUid" to childUid,
                    "usedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            parentUid
        }.addOnSuccessListener {
            val intent = Intent(this, PermissionsSetupActivity::class.java)
            intent.putExtra(PermissionsSetupActivity.EXTRA_STEP, 1)
            startActivity(intent)
        }.addOnFailureListener { error ->
            val message = when (error.message) {
                "Pairing code not found" -> "Pairing code not found"
                "Pairing code invalid or expired" -> "Pairing code invalid or expired"
                else -> "Pairing failed. Try again."
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
