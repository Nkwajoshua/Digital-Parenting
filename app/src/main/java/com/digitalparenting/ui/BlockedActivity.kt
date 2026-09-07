package com.digitalparenting.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.digitalparenting.R

class BlockedActivity : EdgeToEdgeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_screen)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Blocked screen intentionally consumes back navigation.
                }
            }
        )

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
}
