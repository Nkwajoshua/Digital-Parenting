package com.digitalparenting.data.repository

import com.digitalparenting.data.IntelligenceDashboardState
import com.digitalparenting.data.local.AppLimit
import com.digitalparenting.data.local.AppLimitDao
import com.digitalparenting.data.local.AppSessionDao
import com.digitalparenting.data.local.AppUsageStats
import com.digitalparenting.data.local.PredictionRecordDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppUsageRepository(
    private val dao: AppSessionDao,
    private val limitDao: AppLimitDao,
    private val predictionRecordDao: PredictionRecordDao? = null
) {

    suspend fun getAllSessions() = dao.getAllSessions()

    suspend fun getUsageStats(): List<AppUsageStats> = dao.getUsageStats()

    suspend fun setLimit(packageName: String, dailyLimitMillis: Long) {
        limitDao.setLimit(AppLimit(packageName, dailyLimitMillis))
    }

    suspend fun getLimit(packageName: String) = limitDao.getLimit(packageName)

    suspend fun getAllLimits() = limitDao.getAllLimits()

    suspend fun getDashboardState(
        predictorAccuracy: Float,
        modelConfidence: Float,
        modelWeights: Map<String, Float>,
        modelContribution: Float,
        ruleContribution: Float
    ): IntelligenceDashboardState = withContext(Dispatchers.IO) {
        val latestPrediction = predictionRecordDao?.getLatestPrediction()
        val totalPredictions = predictionRecordDao?.getTotalPredictionCount() ?: 0

        val recentPredictions = predictionRecordDao?.getRecentPredictions(10) ?: emptyList()
        val recentEvaluated = predictionRecordDao?.getRecentEvaluatedPredictions(20) ?: emptyList()

        val recentRiskScores = recentPredictions.reversed().map { it.riskScore }
        val recentAccuracyPoints = recentEvaluated.reversed().map { if (it.wasAccurate == true) 1f else 0f }

        // Calculate rolling accuracy percentages (window of 5 predictions)
        val rollingAccuracyPercentages = if (recentEvaluated.size >= 5) {
            recentEvaluated.reversed().windowed(5, 1).map { window ->
                val accurate = window.count { it.wasAccurate == true }
                accurate.toFloat() / window.size
            }
        } else {
            recentAccuracyPoints
        }

        // Get 24-hour risk data
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val twentyFourHourPredictions = predictionRecordDao?.getPredictionsSince(twentyFourHoursAgo) ?: emptyList()
        val twentyFourHourRiskData = twentyFourHourPredictions.map { it.timestamp to it.riskScore }

        // Get weekly risk data
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val weeklyRiskStats = predictionRecordDao?.getDailyAverageRiskSince(sevenDaysAgo) ?: emptyList()
        val weeklyRiskData = weeklyRiskStats.map { it.date to it.avgRisk }

        IntelligenceDashboardState(
            predictionAccuracy = predictorAccuracy,
            modelConfidence = modelConfidence,
            liveRiskScore = latestPrediction?.riskScore ?: 0,
            modelContribution = modelContribution,
            ruleContribution = ruleContribution,
            topDrivers = modelWeights.toList()
                .sortedByDescending { it.second }
                .take(4)
                .toMap(),
            latestPredictionReason = latestPrediction?.reason ?: "No prediction yet",
            totalPredictions = totalPredictions,
            recentRiskScores = recentRiskScores,
            recentAccuracyPoints = recentAccuracyPoints,
            rollingAccuracyPercentages = rollingAccuracyPercentages,
            twentyFourHourRiskData = twentyFourHourRiskData,
            weeklyRiskData = weeklyRiskData
        )
    }
}
