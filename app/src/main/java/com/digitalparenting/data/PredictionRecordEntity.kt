package com.digitalparenting.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prediction_records")
data class PredictionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val currentApp: String,
    val predictedNextApp: String?,
    val riskScore: Int,
    val reason: String = "",
    val wasAccurate: Boolean? = null,
    val actualOutcomeScore: Int? = null
)
