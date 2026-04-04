package com.digitalparenting.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "behavior_records")
data class BehaviorRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val appPackage: String,
    val score: Int,
    val riskLevel: String,
    val mode: String,
    val timestamp: Long
)
