package com.digitalparenting.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.digitalparenting.R
import com.digitalparenting.data.local.AppLimit
import com.digitalparenting.data.local.AppDatabase
import com.digitalparenting.ui.adapter.LimitsAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LimitsConfigActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LimitsAdapter
    private lateinit var limitDao: com.digitalparenting.data.local.AppLimitDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_limits_config)

        recyclerView = findViewById(R.id.rvLimits)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Get DAO from database
        val database = AppDatabase.getDatabase(applicationContext)
        limitDao = database.appLimitDao()

        loadInstalledAppsAndLimits()
    }

    private fun loadInstalledAppsAndLimits() {
        CoroutineScope(Dispatchers.IO).launch {
            // For MVP: show some popular apps + any saved limits
            val limits = limitDao.getAllLimits()
            val sampleApps = listOf(
                AppLimit(packageName = "com.instagram.android", appName = "Instagram", maxMinutes = 30),
                AppLimit(packageName = "com.tiktok.android", appName = "TikTok", maxMinutes = 20),
                AppLimit(packageName = "com.whatsapp", appName = "WhatsApp", maxMinutes = 60)
            )

            withContext(Dispatchers.Main) {
                adapter = LimitsAdapter(sampleApps + limits) { updatedLimit ->
                    saveLimit(updatedLimit)
                }
                recyclerView.adapter = adapter
            }
        }
    }

    private fun saveLimit(limit: AppLimit) {
        CoroutineScope(Dispatchers.IO).launch {
            limitDao.setLimit(limit)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@LimitsConfigActivity, "${limit.appName} limit saved!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}