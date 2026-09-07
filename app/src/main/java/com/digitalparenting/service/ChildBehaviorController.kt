package com.digitalparenting.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.digitalparenting.data.AppSession
import com.digitalparenting.data.BehaviorAnalyzer
import com.digitalparenting.data.BehaviorPredictor
import com.digitalparenting.data.BehaviorRecord
import com.digitalparenting.data.BlockMode
import com.digitalparenting.data.BlockStateManager
import com.digitalparenting.data.InterventionEngine
import com.digitalparenting.data.PredictionRecordEntity
import com.digitalparenting.data.UserProfile
import com.digitalparenting.data.local.AppLimitDao
import com.digitalparenting.data.local.AppSessionDao
import com.digitalparenting.data.local.BehaviorRecordDao
import com.digitalparenting.data.local.PredictionRecordDao
import com.digitalparenting.data.local.UserProfileDao
import com.digitalparenting.util.ProtectionStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns Child behavior analysis, prediction, intervention selection, and
 * prediction-outcome learning.
 *
 * Android rendering and incident persistence are supplied as callbacks so this
 * component can preserve the existing policy without owning service UI state.
 */
internal class ChildBehaviorController(
    private val context: Context,
    private val sessionDao: AppSessionDao,
    private val limitDao: AppLimitDao,
    private val behaviorRecordDao: BehaviorRecordDao,
    private val userProfileDao: UserProfileDao,
    private val predictionRecordDao: PredictionRecordDao,
    private val onChildAlert: (title: String, message: String) -> Unit,
    private val onIncident: (
        type: String,
        title: String,
        message: String,
        appPackage: String?
    ) -> Unit,
    private val onHideOverlay: () -> Unit,
    private val onShowWarningOverlay: (currentApp: String?) -> Unit,
    private val onShowBlockOverlay: (currentApp: String?) -> Unit,
    private val currentAppProvider: () -> String?
) {
    private val handler = Handler(Looper.getMainLooper())
    private val predictionEvaluationDelayMillis = 15 * 60 * 1000L
    private val behaviorAnalyzer = BehaviorAnalyzer()
    private val behaviorPredictor = BehaviorPredictor()
    private val interventionEngine = InterventionEngine()

    fun loadState() {
        behaviorPredictor.loadState(context)
        println("Loaded BehaviorPredictor state: ${behaviorPredictor.getMetrics()}")
    }

    fun evaluate(newApp: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val limit = limitDao.getLimit(newApp)
                ?.let { if (it.enabled) it.maxMinutes * 60_000L else 0L }
                ?: 0L

            val sessionWindowEntities = sessionDao.getSessionsBetween(
                System.currentTimeMillis() - 7 * 24L * 60L * 60L * 1000L,
                System.currentTimeMillis()
            )
            val sessionWindow = sessionWindowEntities.map { entity ->
                AppSession(
                    packageName = entity.packageName,
                    startTime = entity.startTime,
                    endTime = entity.endTime
                )
            }

            val profile = behaviorAnalyzer.deriveUserProfile(sessionWindow)
            val sessionsForApp = sessionWindow.filter { it.packageName == newApp }
            val baseline = if (profile.avgDailyUsageMinutes <= 0L) {
                1.0
            } else {
                profile.avgDailyUsageMinutes.toDouble()
            }

            val prediction = behaviorPredictor.predict(
                newApp,
                sessionsForApp,
                profile,
                sessionWindow
            )
            val insight = behaviorAnalyzer.analyze(
                newApp,
                sessionsForApp,
                limit,
                baseline
            )

            var mode = interventionEngine.decide(insight)
            if (prediction.likelyToBinge && mode.ordinal < BlockMode.DELAY.ordinal) {
                mode = BlockMode.DELAY
            }
            if (prediction.riskScore >= 80 && mode.ordinal < BlockMode.BLOCKED.ordinal) {
                mode = BlockMode.BLOCKED
            }

            BlockStateManager.setBlockMode(newApp, mode)

            if (prediction.riskScore >= 80) {
                onChildAlert(
                    "High Risk Behavior",
                    "Risky usage pattern detected for $newApp"
                )
                onIncident(
                    "high_risk",
                    "High Risk Behavior",
                    "Risky usage pattern detected for $newApp",
                    newApp
                )
            }

            when (mode) {
                BlockMode.NONE -> {
                    BlockStateManager.clearBlocked()
                    ProtectionStateManager.clearPersistedBlockState(context)
                    handler.post { onHideOverlay() }
                }
                BlockMode.WARNING -> {
                    BlockStateManager.removeBlocked(newApp)
                    handler.post {
                        onShowWarningOverlay(currentAppProvider())
                    }
                }
                BlockMode.DELAY -> {
                    BlockStateManager.removeBlocked(newApp)
                    handler.postDelayed({
                        onShowBlockOverlay(currentAppProvider())
                    }, 5000)
                }
                BlockMode.BLOCKED -> {
                    BlockStateManager.setBlocked(setOf(newApp))
                    handler.post {
                        onShowBlockOverlay(currentAppProvider())
                    }
                    onChildAlert(
                        "App Blocked",
                        "$newApp was blocked due to limit or risk level"
                    )
                    onIncident(
                        "app_blocked",
                        "App Blocked",
                        "$newApp was blocked due to limit or risk level",
                        newApp
                    )
                }
            }

            if (BlockStateManager.blockedPackages.isNotEmpty()) {
                ProtectionStateManager.persistBlockState(
                    context,
                    BlockStateManager.blockedPackages,
                    BlockStateManager.blockModes
                )
            } else {
                ProtectionStateManager.clearPersistedBlockState(context)
            }

            userProfileDao.upsert(profile)
            behaviorRecordDao.insert(
                BehaviorRecord(
                    appPackage = newApp,
                    score = insight.addictionScore,
                    riskLevel = insight.riskLevel.name,
                    mode = mode.name,
                    timestamp = System.currentTimeMillis()
                )
            )

            val predictionId = predictionRecordDao.insert(
                PredictionRecordEntity(
                    timestamp = System.currentTimeMillis(),
                    currentApp = newApp,
                    predictedNextApp = prediction.predictedNextApp,
                    riskScore = prediction.riskScore,
                    reason = prediction.reason,
                    wasAccurate = null,
                    actualOutcomeScore = null
                )
            ).toInt()

            CoroutineScope(Dispatchers.IO).launch {
                delay(predictionEvaluationDelayMillis)
                evaluatePredictionOutcome(
                    predictionId,
                    newApp,
                    prediction,
                    profile
                )
            }

            println(
                "Prediction explainability: risk=${prediction.riskScore}, " +
                    "reason=${prediction.reason}"
            )
            println(
                "Behavior: Score=${insight.addictionScore} Risk=${insight.riskLevel} " +
                    "Mode=$mode App=$newApp"
            )
            println(
                "Prediction: Binge=${prediction.likelyToBinge} Risk=${prediction.riskScore} " +
                    "Next=${prediction.predictedNextApp} Reason=${prediction.reason}"
            )
        }
    }

    private suspend fun evaluatePredictionOutcome(
        predictionId: Int,
        appPackage: String,
        prediction: com.digitalparenting.data.BehaviorPrediction,
        profile: UserProfile
    ) {
        val now = System.currentTimeMillis()
        val windowStart = now - 15 * 60 * 1000L
        val usageWindow = sessionDao.getSessionsBetween(windowStart, now)
            .filter { it.packageName == appPackage }

        val actualOutcomeScore =
            (usageWindow.sumOf { it.duration } / 1000 / 60).toInt()
        val predictedThreshold =
            (profile.avgDailyUsageMinutes * 0.25).toInt().coerceAtLeast(1)

        val wasAccurate = if (prediction.likelyToBinge) {
            actualOutcomeScore >= predictedThreshold
        } else {
            actualOutcomeScore < predictedThreshold
        }

        predictionRecordDao.updateOutcome(
            predictionId,
            wasAccurate,
            actualOutcomeScore
        )

        behaviorPredictor.adjustWeights(prediction, wasAccurate)
        behaviorPredictor.saveState(context)

        println(
            "Prediction outcome evaluated for record=$predictionId " +
                "accurate=$wasAccurate actualMinutes=$actualOutcomeScore"
        )
        println(
            "Updated BehaviorPredictor metrics: ${behaviorPredictor.getMetrics()}"
        )
    }
}
