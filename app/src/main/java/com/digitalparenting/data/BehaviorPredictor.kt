package com.digitalparenting.data

import java.util.*

class BehaviorPredictor {

    private var weights = PredictionWeights()
    private var accurateCount = 0
    private var inaccurateCount = 0
    private val behaviorModel = AppBehaviorModel()

    fun loadState(context: android.content.Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        weights = PredictionWeights(
            timeWeight = prefs.getInt(KEY_TIME_WEIGHT, weights.timeWeight),
            sequenceWeight = prefs.getInt(KEY_SEQUENCE_WEIGHT, weights.sequenceWeight),
            momentumWeight = prefs.getInt(KEY_MOMENTUM_WEIGHT, weights.momentumWeight),
            baselineWeight = prefs.getInt(KEY_BASELINE_WEIGHT, weights.baselineWeight)
        ).also { it.normalize() }
        accurateCount = prefs.getInt(KEY_ACCURATE_COUNT, 0)
        inaccurateCount = prefs.getInt(KEY_INACCURATE_COUNT, 0)
        behaviorModel.loadState(context)
    }

    fun saveState(context: android.content.Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_TIME_WEIGHT, weights.timeWeight)
            .putInt(KEY_SEQUENCE_WEIGHT, weights.sequenceWeight)
            .putInt(KEY_MOMENTUM_WEIGHT, weights.momentumWeight)
            .putInt(KEY_BASELINE_WEIGHT, weights.baselineWeight)
            .putInt(KEY_ACCURATE_COUNT, accurateCount)
            .putInt(KEY_INACCURATE_COUNT, inaccurateCount)
            .apply()
        behaviorModel.saveState(context)
    }

    fun getAccuracy(): Float {
        val total = accurateCount + inaccurateCount
        val smoothedAccuracy = (accurateCount + 1f) / (total + 2f)
        return smoothedAccuracy
    }

    fun getWeights(): PredictionWeights = weights

    fun getMetrics(): String {
        return "Accuracy=${getAccuracy()}, correct=$accurateCount, incorrect=$inaccurateCount, weights=$weights"
    }

    fun getModelWeights(): Map<String, Float> = behaviorModel.getModelWeights()

    fun predict(
        currentApp: String,
        recentSessions: List<AppSession>,
        userProfile: UserProfile,
        allHistoricalSessions: List<AppSession>
    ): BehaviorPrediction {

        val peakTimes = calculatePeakUsageHours(allHistoricalSessions)
        val appSequences = calculateAppSequences(allHistoricalSessions)
        val sessionMomentum = calculateSessionMomentum(recentSessions)

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isPeakTime = peakTimes.contains(currentHour)

        val predictedNext = predictNextApp(currentApp, appSequences, recentSessions)

        val baselineUsage = if (userProfile.avgDailyUsageMinutes <= 0L) 1.0 else userProfile.avgDailyUsageMinutes.toDouble()

        var riskScore = 0
        var reason = ""

        if (sessionMomentum > 4) {
            riskScore += weights.momentumWeight
            reason += "High session momentum detected. "
        }

        if (isPeakTime) {
            riskScore += weights.timeWeight
            reason += "Peak usage time. "
        }

        if (predictedNext != null) {
            riskScore += weights.sequenceWeight
            reason += "Following known sequence. "
        }

        if (baselineUsage > 0 && userProfile.avgDailyUsageMinutes > baselineUsage * 1.5) {
            riskScore += weights.baselineWeight
            reason += "Above baseline usage. "
        }

        val ruleRiskNormalized = (riskScore.coerceIn(0, 100) / 100f)

        val modelRisk = behaviorModel.predictRisk(
            isPeakTime = isPeakTime,
            sequenceMatch = predictedNext != null,
            sessionMomentum = sessionMomentum,
            baselineUsageFactor = if (baselineUsage <= 0.0) 1.0 else userProfile.avgDailyUsageMinutes / baselineUsage
        )

        val modelWeight = behaviorModel.getConfidence(getAccuracy())
        val ruleWeight = 1 - modelWeight

        val finalRiskFloat = modelWeight * modelRisk + ruleWeight * ruleRiskNormalized
        val finalRisk = (finalRiskFloat * 100).toInt().coerceIn(0, 100)

        val likelyToBinge = finalRisk >= 55

        val explanation = reason.trim() + " ModelRisk=${String.format("%.2f", modelRisk)} RuleRisk=${String.format("%.2f", ruleRiskNormalized)} ModelWeight=${String.format("%.2f", modelWeight)} RuleWeight=${String.format("%.2f", ruleWeight)}"

        return BehaviorPrediction(
            likelyToBinge = likelyToBinge,
            predictedNextApp = predictedNext,
            predictedNextInMinutes = if (isPeakTime) 5 else 15,
            riskScore = finalRisk,
            reason = explanation,
            peakTime = isPeakTime,
            sequenceMatch = predictedNext != null,
            sessionMomentum = sessionMomentum,
            baselineUsageFactor = if (baselineUsage <= 0.0) 1.0 else userProfile.avgDailyUsageMinutes / baselineUsage
        )
    }

    fun adjustWeights(prediction: BehaviorPrediction, wasAccurate: Boolean) {
        if (wasAccurate) {
            accurateCount += 1
            weights.timeWeight += 1
            weights.sequenceWeight += 1
            weights.momentumWeight += 1
            weights.baselineWeight += 1
        } else {
            inaccurateCount += 1
            weights.timeWeight -= 1
            weights.sequenceWeight -= 2
            weights.momentumWeight -= 1
            weights.baselineWeight -= 1
        }
        weights.normalize()

        val label = if (wasAccurate) 1f else 0f
        behaviorModel.updateModel(
            isPeakTime = prediction.peakTime,
            sequenceMatch = prediction.sequenceMatch,
            sessionMomentum = prediction.sessionMomentum,
            baselineUsageFactor = prediction.baselineUsageFactor,
            label = label
        )
    }

    companion object {
        private const val PREFS_NAME = "behavior_predictor_prefs"
        private const val KEY_TIME_WEIGHT = "time_weight"
        private const val KEY_SEQUENCE_WEIGHT = "sequence_weight"
        private const val KEY_MOMENTUM_WEIGHT = "momentum_weight"
        private const val KEY_BASELINE_WEIGHT = "baseline_weight"
        private const val KEY_ACCURATE_COUNT = "accurate_count"
        private const val KEY_INACCURATE_COUNT = "inaccurate_count"
    }

    private fun calculatePeakUsageHours(sessions: List<AppSession>): Set<Int> {
        if (sessions.isEmpty()) return emptySet()

        val hourCounts = mutableMapOf<Int, Int>()
        sessions.forEach { session ->
            val hour = Calendar.getInstance().apply { timeInMillis = session.startTime }.get(Calendar.HOUR_OF_DAY)
            hourCounts[hour] = (hourCounts[hour] ?: 0) + 1
        }

        val avgCount = hourCounts.values.average()
        return hourCounts.filter { it.value > avgCount }.keys
    }

    private fun calculateAppSequences(sessions: List<AppSession>): Map<String, Map<String, Int>> {
        if (sessions.size < 2) return emptyMap()

        val sorted = sessions.sortedBy { it.startTime }
        val sequences = mutableMapOf<String, MutableMap<String, Int>>()

        for (i in 0 until sorted.size - 1) {
            val current = sorted[i].packageName
            val next = sorted[i + 1].packageName

            sequences.getOrPut(current) { mutableMapOf() }[next] =
                (sequences[current]?.get(next) ?: 0) + 1
        }

        return sequences
    }

    private fun predictNextApp(
        currentApp: String,
        sequences: Map<String, Map<String, Int>>,
        recentSessions: List<AppSession>
    ): String? {
        val subsequences = sequences[currentApp] ?: return null
        return subsequences.entries.maxByOrNull { it.value }?.key
    }

    private fun calculateSessionMomentum(recentSessions: List<AppSession>): Int {
        if (recentSessions.isEmpty()) return 0

        val now = System.currentTimeMillis()
        val last10Minutes = now - 10 * 60 * 1000

        return recentSessions.count { it.startTime >= last10Minutes }
    }
}
