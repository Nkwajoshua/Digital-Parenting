package com.digitalparenting.data

data class IntelligenceDashboardState(
    val predictionAccuracy: Float = 0f,
    val modelConfidence: Float = 0f,
    val liveRiskScore: Int = 0,
    val modelContribution: Float = 0f,
    val ruleContribution: Float = 0f,
    val topDrivers: Map<String, Float> = emptyMap(),
    val latestPredictionReason: String = "No prediction yet",
    val totalPredictions: Int = 0,
    val recentRiskScores: List<Int> = emptyList(),
    val recentAccuracyPoints: List<Float> = emptyList(),
    val rollingAccuracyPercentages: List<Float> = emptyList(),
    val twentyFourHourRiskData: List<Pair<Long, Int>> = emptyList(),
    val weeklyRiskData: List<Pair<String, Float>> = emptyList()
)
