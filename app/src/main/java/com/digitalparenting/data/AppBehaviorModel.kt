package com.digitalparenting.data

import android.content.Context
import kotlin.math.min

/**
 * Simple on-device online model for risk score estimation.
 * This is a lightweight replacement for an actual TF Lite model
 * with same integration pattern.
 */
class AppBehaviorModel {

    private var timeWeight = 0.25f
    private var sequenceWeight = 0.30f
    private var momentumWeight = 0.30f
    private var baselineWeight = 0.15f
    private var trainingSamples = 0

    fun predictRisk(
        isPeakTime: Boolean,
        sequenceMatch: Boolean,
        sessionMomentum: Int,
        baselineUsageFactor: Double
    ): Float {
        val momentumScore = (sessionMomentum.coerceAtMost(20) / 20f)
        val baselineScore = baselineUsageFactor.toFloat().coerceIn(0f, 2f) / 2f

        val raw = (if (isPeakTime) 1f else 0f) * timeWeight +
            (if (sequenceMatch) 1f else 0f) * sequenceWeight +
            momentumScore * momentumWeight +
            baselineScore * baselineWeight

        return raw.coerceIn(0f, 1f)
    }

    fun updateModel(
        isPeakTime: Boolean,
        sequenceMatch: Boolean,
        sessionMomentum: Int,
        baselineUsageFactor: Double,
        label: Float
    ) {
        val prediction = predictRisk(isPeakTime, sequenceMatch, sessionMomentum, baselineUsageFactor)
        val error = label - prediction

        val lr = 0.05f
        val momentumScore = (sessionMomentum.coerceAtMost(20) / 20f)
        val baselineScore = baselineUsageFactor.toFloat().coerceIn(0f, 2f) / 2f

        timeWeight += lr * error * if (isPeakTime) 1f else 0f
        sequenceWeight += lr * error * if (sequenceMatch) 1f else 0f
        momentumWeight += lr * error * momentumScore
        baselineWeight += lr * error * baselineScore

        trainingSamples += 1

        normalizeWeights()
    }

    fun loadState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        timeWeight = prefs.getFloat(KEY_TIME_WEIGHT, timeWeight)
        sequenceWeight = prefs.getFloat(KEY_SEQUENCE_WEIGHT, sequenceWeight)
        momentumWeight = prefs.getFloat(KEY_MOMENTUM_WEIGHT, momentumWeight)
        baselineWeight = prefs.getFloat(KEY_BASELINE_WEIGHT, baselineWeight)
        trainingSamples = prefs.getInt(KEY_TRAINING_SAMPLES, trainingSamples)
    }

    fun saveState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_TIME_WEIGHT, timeWeight)
            .putFloat(KEY_SEQUENCE_WEIGHT, sequenceWeight)
            .putFloat(KEY_MOMENTUM_WEIGHT, momentumWeight)
            .putFloat(KEY_BASELINE_WEIGHT, baselineWeight)
            .putInt(KEY_TRAINING_SAMPLES, trainingSamples)
            .apply()
    }

    fun getModelWeights(): Map<String, Float> {
        return mapOf(
            "Night Usage" to timeWeight,
            "App Switching" to sequenceWeight,
            "Session Momentum" to momentumWeight,
            "Baseline Usage" to baselineWeight
        )
    }

    fun getConfidence(accuracy: Float): Float {
        val dataFactor = min(1f, trainingSamples / 100f)
        val accuracyFactor = accuracy
        return dataFactor * accuracyFactor
    }

    private fun normalizeWeights() {
        val total = timeWeight + sequenceWeight + momentumWeight + baselineWeight
        if (total == 0f) return
        timeWeight /= total
        sequenceWeight /= total
        momentumWeight /= total
        baselineWeight /= total
    }

    companion object {
        private const val PREFS_NAME = "app_behavior_model_prefs"
        private const val KEY_TIME_WEIGHT = "model_time_weight"
        private const val KEY_SEQUENCE_WEIGHT = "model_sequence_weight"
        private const val KEY_MOMENTUM_WEIGHT = "model_momentum_weight"
        private const val KEY_BASELINE_WEIGHT = "model_baseline_weight"
        private const val KEY_TRAINING_SAMPLES = "model_training_samples"
    }
}
