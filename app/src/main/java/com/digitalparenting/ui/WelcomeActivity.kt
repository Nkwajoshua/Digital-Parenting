package com.digitalparenting.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.digitalparenting.R

class WelcomeActivity : EdgeToEdgeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        findViewById<Button>(R.id.btnStartPairing).setOnClickListener {
            startActivity(Intent(this, PairingCodeActivity::class.java))
        }
    }
}