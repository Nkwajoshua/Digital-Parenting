package com.digitalparenting.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.digitalparenting.R

class BlockedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_screen)

        val appName = intent.getStringExtra("appName") ?: "This app"
        val reason = intent.getStringExtra("reason") ?: "Daily limit exceeded"

        findViewById<TextView>(R.id.tvBlockedTitle).text = "$appName Blocked"
        findViewById<TextView>(R.id.tvBlockedReason).text = reason

        findViewById<Button>(R.id.btnRequestMoreTime).setOnClickListener {
            startActivity(Intent(this, RequestMoreTimeActivity::class.java).apply {
                putExtra("appName", appName)
                putExtra("appPackage", intent.getStringExtra("appPackage") ?: "unknown.package")
            })
        }
    }

    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        // Prevent easy bypass - do nothing to block back navigation
    }
}