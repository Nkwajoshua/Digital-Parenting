package com.digitalparenting.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.digitalparenting.R

class BlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked)

        val appName = intent.getStringExtra("appName") ?: "Unknown App"
        findViewById<TextView>(R.id.tvBlockedApp).text = appName

        findViewById<Button>(R.id.btnRequestMoreTime).setOnClickListener {
            // TODO: Implement request more time logic (could send notification to parent)
            finish()  // For now, just close
        }
    }

    override fun onBackPressed() {
        // Prevent exit - user cannot back out of block screen
    }
}
