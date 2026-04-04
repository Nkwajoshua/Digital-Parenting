package com.digitalparenting.data

data class BehaviorPrediction(
    val likelyToBinge: Boolean,
    val predictedNextApp: String?,
    val predictedNextInMinutes: Int,
    val riskScore: Int,
    val reason: String,
    val peakTime: Boolean,
    val sequenceMatch: Boolean,
    val sessionMomentum: Int,
    val baselineUsageFactor: Double
)

data class PredictionRecord(
    val id: Int = 0,
    val timestamp: Long,
    val currentApp: String,
    val predictedNextApp: String?,
    val riskScore: Int,
    val wasAccurate: Boolean?
)
