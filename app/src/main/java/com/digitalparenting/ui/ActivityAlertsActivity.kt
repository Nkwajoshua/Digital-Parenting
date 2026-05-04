package com.digitalparenting.ui

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.digitalparenting.R
import com.digitalparenting.ui.adapter.AlertsAdapter

class ActivityAlertsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activity_alerts)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerAlerts)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val sampleAlerts = listOf(
            "Instagram blocked • Daily limit reached",
            "Parent blocked TikTok remotely",
            "Request for more time sent to parent",
            "Protection service restarted successfully",
            "Cloud sync completed"
        )

        recyclerView.adapter = AlertsAdapter(sampleAlerts)
    }
}