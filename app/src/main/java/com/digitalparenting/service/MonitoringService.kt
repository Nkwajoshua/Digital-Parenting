package com.digitalparenting.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import android.app.usage.UsageStatsManager
import com.digitalparenting.util.NotificationHelper
import com.digitalparenting.util.ProtectionStateManager
import android.app.usage.UsageEvents
import android.provider.Settings
import com.digitalparenting.R
import com.digitalparenting.data.AppSession
import com.digitalparenting.data.BehaviorAnalyzer
import com.digitalparenting.data.BehaviorRecord
import com.digitalparenting.data.BehaviorPredictor
import com.digitalparenting.data.BlockStateManager
import com.digitalparenting.data.InterventionEngine
import com.digitalparenting.data.BlockMode
import com.digitalparenting.data.UserProfile
import com.digitalparenting.data.PredictionRecordEntity
import com.digitalparenting.data.local.AppDatabase
import com.digitalparenting.data.local.ProtectionIncidentEntity
import com.digitalparenting.data.local.AppLimitDao
import com.digitalparenting.data.local.AppSessionDao
import com.digitalparenting.data.local.AppSessionEntity
import com.digitalparenting.data.local.AppUsageStats
import com.digitalparenting.data.local.BehaviorRecordDao
import com.digitalparenting.data.local.UserProfileDao
import com.digitalparenting.ui.BlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MonitoringService : Service() {

    private lateinit var notificationHelper: NotificationHelper
    private val handler = Handler(Looper.getMainLooper())
    private val predictionEvaluationDelayMillis = 15 * 60 * 1000L // 15 minutes real-world evaluation window

    private var currentApp: String? = null
    private var currentSession: AppSession? = null
    private var accessibilityAlertShown = false
    private var overlayAlertShown = false

    private lateinit var database: AppDatabase
    private lateinit var dao: AppSessionDao
    private lateinit var limitDao: AppLimitDao
    private lateinit var behaviorRecordDao: BehaviorRecordDao
    private lateinit var userProfileDao: UserProfileDao
    private lateinit var predictionRecordDao: com.digitalparenting.data.local.PredictionRecordDao
    private lateinit var incidentDao: com.digitalparenting.data.local.ProtectionIncidentDao
    private var overlayView: android.view.View? = null
    private lateinit var windowManager: WindowManager
    private val behaviorAnalyzer = BehaviorAnalyzer()
    private val behaviorPredictor = BehaviorPredictor()
    private val interventionEngine = InterventionEngine()

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        notificationHelper.createChannels()
        startForeground(
            NotificationHelper.FOREGROUND_NOTIFICATION_ID,
            notificationHelper.buildForegroundNotification("Monitoring usage and enforcing protections")
        )

        database = AppDatabase.getDatabase(this)
        dao = database.appSessionDao()
        limitDao = database.appLimitDao()
        behaviorRecordDao = database.behaviorRecordDao()
        userProfileDao = database.userProfileDao()
        predictionRecordDao = database.predictionRecordDao()
        incidentDao = database.protectionIncidentDao()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        behaviorPredictor.loadState(this)
        println("Loaded BehaviorPredictor state: ${behaviorPredictor.getMetrics()}")

        restoreProtectionState()
        updateForegroundNotification()
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restart automatically if killed by the system
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        val restartServiceIntent = Intent(applicationContext, MonitoringService::class.java)
        val restartPendingIntent = PendingIntent.getService(
            applicationContext,
            1,
            restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 2000,
            restartPendingIntent
        )
        super.onTaskRemoved(rootIntent)
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                checkForegroundApp()
                enforceBlockState()
                handler.postDelayed(this, 2000)
            }
        })
    }

    private fun enforceBlockState() {
        if (currentApp != null && BlockStateManager.isBlocked(currentApp!!)) {
            showBlockOverlay()
        } else {
            hideBlockOverlay()
        }

        verifyProtectionState()
        updateForegroundNotification()
    }

    private fun checkForegroundApp() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()

        val events = usm.queryEvents(time - 5000, time)
        val event = UsageEvents.Event()

        var detectedApp: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                detectedApp = event.packageName
            }
        }

        if (detectedApp != null && detectedApp != currentApp) {
            handleAppSwitch(detectedApp)
        }
    }

    private fun handleAppSwitch(newApp: String) {
        val now = System.currentTimeMillis()

        currentSession?.let {
            it.endTime = now
            logSession(it)
        }

        currentSession = AppSession(
            packageName = newApp,
            startTime = now
        )

        currentApp = newApp

        println("Switched to: $newApp")

        // 🔥 INSTANT enforcement (synchronous, no delay)
        CoroutineScope(Dispatchers.IO).launch {
            val limit = limitDao.getLimit(newApp)?.dailyLimitMillis ?: 0L

            val sessionWindowEntities = dao.getSessionsBetween(
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
            val baseline = if (profile.avgDailyUsageMinutes <= 0L) 1.0 else profile.avgDailyUsageMinutes.toDouble()

            // 🚀 Predictive Intelligence
            val prediction = behaviorPredictor.predict(newApp, sessionsForApp, profile, sessionWindow)

            val insight = behaviorAnalyzer.analyze(newApp, sessionsForApp, limit, baseline)
            
            // Boost intervention mode if prediction risk is high
            var mode = interventionEngine.decide(insight)
            if (prediction.likelyToBinge && mode.ordinal < BlockMode.DELAY.ordinal) {
                mode = BlockMode.DELAY  // Escalate to at least DELAY if binge predicted
            }
            if (prediction.riskScore >= 80 && mode.ordinal < BlockMode.BLOCKED.ordinal) {
                mode = BlockMode.BLOCKED  // Escalate to BLOCKED if very high risk
            }

            BlockStateManager.setBlockMode(newApp, mode)

            if (prediction.riskScore >= 80) {
                notificationHelper.showRiskAlert(
                    "High Risk Behavior",
                    "Risky usage pattern detected for $newApp"
                )
                logIncident(
                    "high_risk",
                    "High Risk Behavior",
                    "Risky usage pattern detected for $newApp",
                    newApp
                )
            }

            when (mode) {
                    BlockMode.NONE -> {
                    BlockStateManager.clearBlocked()
                    ProtectionStateManager.clearPersistedBlockState(this@MonitoringService)
                    handler.post { hideBlockOverlay() }
                }
                BlockMode.WARNING -> {
                    // Show warning overlay
                    BlockStateManager.removeBlocked(newApp)
                    handler.post { showWarningOverlay() }
                }
                BlockMode.DELAY -> {
                    // Add delay friction
                    BlockStateManager.removeBlocked(newApp)
                    handler.postDelayed({
                        showBlockOverlay()
                    }, 5000)
                }
                BlockMode.BLOCKED -> {
                    BlockStateManager.setBlocked(setOf(newApp))
                    handler.post { showBlockOverlay() }
                    notificationHelper.showRiskAlert(
                        "App Blocked",
                        "$newApp was blocked due to limit or risk level"
                    )
                    logIncident(
                        "app_blocked",
                        "App Blocked",
                        "$newApp was blocked due to limit or risk level",
                        newApp
                    )
                }
            }

            if (BlockStateManager.blockedPackages.isNotEmpty()) {
                ProtectionStateManager.persistBlockState(
                    this@MonitoringService,
                    BlockStateManager.blockedPackages,
                    BlockStateManager.blockModes
                )
            } else {
                ProtectionStateManager.clearPersistedBlockState(this@MonitoringService)
            }

            // Persist profile and behavior record
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

            // Persist prediction record and schedule outcome evaluation
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

            // Evaluate after a real-world production delay (15 minutes)
            CoroutineScope(Dispatchers.IO).launch {
                delay(predictionEvaluationDelayMillis)
                evaluatePredictionOutcome(predictionId, newApp, prediction, profile)
            }

            // Log behavior and prediction
            println("Prediction explainability: risk=${prediction.riskScore}, reason=${prediction.reason}")
            println("Behavior: Score=${insight.addictionScore} Risk=${insight.riskLevel} Mode=$mode App=$newApp")
            println("Prediction: Binge=${prediction.likelyToBinge} Risk=${prediction.riskScore} Next=${prediction.predictedNextApp} Reason=${prediction.reason}")
        }
    }

    private suspend fun isLimitExceeded(packageName: String): Boolean {
        val limit = limitDao.getLimit(packageName) ?: return false

        val usageStats = dao.getUsageStats()
        val usage = usageStats.find { it.packageName == packageName }

        return usage != null && usage.totalTime >= limit.dailyLimitMillis
    }

    private fun showBlockOverlay() {
        // 🔥 Prevent duplicate overlays
        if (overlayView != null) {
            println("Overlay already active")
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            println("No overlay permission")
            return
        }

        try {
            overlayView = LayoutInflater.from(this).inflate(R.layout.block_overlay, null)

            // 🔥 Aggressive flags for maximum blockability
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE
            )

            windowManager.addView(overlayView, params)
            println("✅ Block overlay shown for: $currentApp")
        } catch (e: Exception) {
            println("❌ Error showing overlay: ${e.message}")
            overlayView = null
            e.printStackTrace()
        }
    }

    private fun hideBlockOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView)
                println("✅ Block overlay hidden")
            } catch (e: Exception) {
                println("❌ Error hiding overlay: ${e.message}")
                e.printStackTrace()
            } finally {
                overlayView = null
            }
        }
    }

    private fun showWarningOverlay() {
        // Similar to block overlay but warning
        if (overlayView != null) {
            println("Warning overlay already active")
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            println("No overlay permission")
            return
        }

        try {
            overlayView = LayoutInflater.from(this).inflate(R.layout.block_overlay, null)

            // Modify text to warning
            val titleView = overlayView?.findViewById<android.widget.TextView>(R.id.overlayTitle)
            val textView = overlayView?.findViewById<android.widget.TextView>(R.id.overlayText)
            titleView?.text = "⚠️ Warning"
            textView?.text = "You've been using this app for a while. Consider taking a break."

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE
            )

            windowManager.addView(overlayView, params)
            println("✅ Warning overlay shown for: $currentApp")
        } catch (e: Exception) {
            println("❌ Error showing warning overlay: ${e.message}")
            overlayView = null
            e.printStackTrace()
        }
    }

    private fun logSession(session: AppSession) {
        val duration = session.getDuration()

        val entity = AppSessionEntity(
            packageName = session.packageName,
            startTime = session.startTime,
            endTime = session.endTime,
            duration = duration
        )

        CoroutineScope(Dispatchers.IO).launch {
            dao.insertSession(entity)

            val durationSeconds = duration / 1000
            println("DB: App: ${session.packageName} Start: ${formatTime(session.startTime)} End: ${formatTime(session.endTime)} Duration: ${durationSeconds}s")
        }
    }

    private suspend fun evaluatePredictionOutcome(
        predictionId: Int,
        appPackage: String,
        prediction: com.digitalparenting.data.BehaviorPrediction,
        profile: UserProfile
    ) {
        val now = System.currentTimeMillis()
        val windowEnd = now

        val windowStart = now - 15 * 60 * 1000L // 15 min window
        val usageWindow = dao.getSessionsBetween(windowStart, windowEnd)
            .filter { it.packageName == appPackage }

        val actualOutcomeScore = (usageWindow.sumOf { it.duration } / 1000 / 60).toInt()

        val predictedThreshold = (profile.avgDailyUsageMinutes * 0.25).toInt().coerceAtLeast(1)

        val wasAccurate = if (prediction.likelyToBinge) {
            actualOutcomeScore >= predictedThreshold
        } else {
            actualOutcomeScore < predictedThreshold
        }

        predictionRecordDao.updateOutcome(predictionId, wasAccurate, actualOutcomeScore)

        behaviorPredictor.adjustWeights(prediction, wasAccurate)
        behaviorPredictor.saveState(this)

        println("Prediction outcome evaluated for record=$predictionId accurate=$wasAccurate actualMinutes=$actualOutcomeScore")
        println("Updated BehaviorPredictor metrics: ${behaviorPredictor.getMetrics()}")
    }

    private fun formatTime(timestamp: Long): String {
        return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false

            enabledServices.contains(packageName ?: "")
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun launchAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun launchOverlaySettings() {
        startActivity(ProtectionStateManager.buildOverlaySettingsIntent(this))
    }

    private fun restoreProtectionState() {
        val snapshot = ProtectionStateManager.restoreBlockState(this)
        if (snapshot.blockedPackages.isNotEmpty()) {
            BlockStateManager.setBlocked(snapshot.blockedPackages)
            snapshot.blockModes.forEach { (pkg, mode) ->
                BlockStateManager.setBlockMode(pkg, mode)
            }
        }
    }

    private fun verifyProtectionState() {
        val accessibilityEnabled = ProtectionStateManager.isAccessibilityEnabled(this)
        val overlayGranted = ProtectionStateManager.isOverlayPermissionGranted(this)

        if (!accessibilityEnabled) {
            if (!accessibilityAlertShown) {
                launchAccessibilitySettings()
                notificationHelper.showSecurityAlert(
                    "Protection Weakened",
                    "Accessibility service is disabled. Re-enable it to continue protection."
                )
                logIncident(
                    "accessibility_disabled",
                    "Protection Weakened",
                    "Accessibility service is disabled. Re-enable it to continue protection.",
                    null
                )
                accessibilityAlertShown = true
            }
        } else {
            accessibilityAlertShown = false
        }

        if (!overlayGranted) {
            if (!overlayAlertShown) {
                launchOverlaySettings()
                notificationHelper.showSecurityAlert(
                    "Overlay Permission Missing",
                    "Overlay permission is required for app blocking."
                )
                logIncident(
                    "overlay_missing",
                    "Overlay Permission Missing",
                    "Overlay permission is required for app blocking.",
                    null
                )
                overlayAlertShown = true
            }
        } else {
            overlayAlertShown = false
        }
    }

    private fun updateForegroundNotification() {
        val contentText = buildString {
            append("Monitoring usage")
            if (!ProtectionStateManager.isAccessibilityEnabled(this@MonitoringService)) {
                append(" · accessibility disabled")
            }
            if (!ProtectionStateManager.isOverlayPermissionGranted(this@MonitoringService)) {
                append(" · overlay disabled")
            }
            if (currentApp != null && BlockStateManager.isBlocked(currentApp!!)) {
                append(" · blocking $currentApp")
            }
        }

        startForeground(
            NotificationHelper.FOREGROUND_NOTIFICATION_ID,
            notificationHelper.buildForegroundNotification(contentText)
        )
    }

    private fun logIncident(
        type: String,
        title: String,
        message: String,
        appPackage: String?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            incidentDao.insert(
                ProtectionIncidentEntity(
                    type = type,
                    title = title,
                    message = message,
                    appPackage = appPackage,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideBlockOverlay()
        currentSession?.let {
            it.endTime = System.currentTimeMillis()
            logSession(it)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
