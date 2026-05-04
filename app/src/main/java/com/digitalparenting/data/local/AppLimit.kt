package com.digitalparenting.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_limits")
data class AppLimit(
    @PrimaryKey val packageName: String,
    val appName: String,
    val maxMinutes: Int = 60,           // daily limit
    val enabled: Boolean = true,
    val lastReset: Long = System.currentTimeMillis()  // for daily reset logic
)
