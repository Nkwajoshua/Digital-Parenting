package com.digitalparenting.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_sessions")
data class AppSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val packageName: String,
    val appName: String? = null,
    val startTime: Long,
    val endTime: Long,
    val duration: Long
)
