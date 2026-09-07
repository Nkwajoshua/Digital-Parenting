package com.digitalparenting.ui

import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.digitalparenting.R
import com.digitalparenting.data.local.AppDatabase
import com.digitalparenting.data.local.ProtectionIncidentEntity
import com.digitalparenting.ui.adapter.AlertsAdapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActivityAlertsActivity : EdgeToEdgeActivity() {

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val alertsAdapter = AlertsAdapter()

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activity_alerts)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.recyclerAlerts)
        emptyView = findViewById(R.id.tvEmptyAlerts)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = alertsAdapter
    }

    override fun onResume() {
        super.onResume()
        loadRecentIncidents()
    }

    override fun onDestroy() {
        loadJob?.cancel()
        uiScope.cancel()
        super.onDestroy()
    }

    private fun loadRecentIncidents() {
        loadJob?.cancel()
        loadJob = uiScope.launch {
            try {
                val incidents = withContext(Dispatchers.IO) {
                    database.protectionIncidentDao().getRecentIncidents(50)
                }

                if (isFinishing || isDestroyed) return@launch

                val items = incidents.map(::formatIncident)
                alertsAdapter.submitItems(items)
                recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                emptyView.text = "No protection alerts yet."
                emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("ACTIVITY_ALERTS", "Unable to load protection incidents", error)
                alertsAdapter.submitItems(emptyList())
                recyclerView.visibility = View.GONE
                emptyView.text = "Unable to load alerts."
                emptyView.visibility = View.VISIBLE
            }
        }
    }

    private fun formatIncident(incident: ProtectionIncidentEntity): String {
        val headline = incident.title.ifBlank { incident.type }
        val detail = if (
            incident.message.isBlank() || incident.message.equals(headline, ignoreCase = true)
        ) {
            headline
        } else {
            "$headline • ${incident.message}"
        }
        val whenText = DateUtils.getRelativeTimeSpanString(
            incident.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )

        return "$detail\n$whenText"
    }
}
