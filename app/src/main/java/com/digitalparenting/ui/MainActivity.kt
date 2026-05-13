package com.digitalparenting.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.digitalparenting.service.MonitoringService

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Compatibility entry point only.
        // Child app architecture keeps setup flow in WelcomeActivity and parent-only dashboards out of child nav.
        val intent = Intent(this, MonitoringService::class.java)
        startForegroundService(intent)
        startActivity(Intent(this, WelcomeActivity::class.java))
        finish()
    }
}
