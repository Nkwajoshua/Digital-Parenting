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

class PairingCodeActivity : AppCompatActivity() {

    private lateinit var pairingCodeInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing_code)

        pairingCodeInput = findViewById(R.id.etPairingCode)
        pairingCodeInput.filters = arrayOf(InputFilter.LengthFilter(6))

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnLinkDevice).setOnClickListener {
            val code = pairingCodeInput.text.toString().trim()

            if (code.length != 6) {
                Toast.makeText(this, "Enter a valid 6-digit pairing code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // TODO: Replace with real Firebase pairing validation
            val intent = Intent(this, PermissionsSetupActivity::class.java)
            intent.putExtra(PermissionsSetupActivity.EXTRA_STEP, 1)
            startActivity(intent)
        }
    }
}