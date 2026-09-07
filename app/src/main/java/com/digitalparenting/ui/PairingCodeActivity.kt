package com.digitalparenting.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import com.digitalparenting.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException

class PairingCodeActivity : EdgeToEdgeActivity() {

    private lateinit var pairingCodeInput: EditText
    private lateinit var linkButton: Button
    private val functions by lazy { FirebaseFunctions.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing_code)

        pairingCodeInput = findViewById(R.id.etPairingCode)
        pairingCodeInput.filters = arrayOf(InputFilter.LengthFilter(6))
        linkButton = findViewById(R.id.btnLinkDevice)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        linkButton.setOnClickListener {
            val code = pairingCodeInput.text.toString().trim()
            if (!code.matches(Regex("^\\d{6}$"))) {
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

        linkButton.isEnabled = false

        val payload = mapOf(
            "code" to code,
            "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "platform" to "android"
        )

        functions
            .getHttpsCallable("redeemPairingCode")
            .call(payload)
            .addOnSuccessListener {
                val intent = Intent(this, PermissionsSetupActivity::class.java)
                intent.putExtra(PermissionsSetupActivity.EXTRA_STEP, 1)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { error ->
                linkButton.isEnabled = true
                val functionsError = error as? FirebaseFunctionsException
                val message = when (functionsError?.code) {
                    FirebaseFunctionsException.Code.NOT_FOUND -> "Pairing code not found"
                    FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> "Pairing code has expired"
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION -> functionsError.message ?: "Pairing code is no longer valid"
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Child authentication missing"
                    FirebaseFunctionsException.Code.PERMISSION_DENIED -> "Pairing is not allowed for this device"
                    else -> "Pairing failed. Try again."
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
    }
}