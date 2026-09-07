package com.digitalparenting.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import android.app.usage.UsageStatsManager
import com.digitalparenting.util.NotificationHelper
import com.digitalparenting.util.ProtectionStateManager
import android.app.usage.UsageEvents
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
import android.app.ActivityManager
import com.digitalparenting.data.repository.UsageSyncRepository
import com.digitalparenting.data.local.AppLimitDao
import com.digitalparenting.data.local.AppSessionDao
import com.digitalparenting.data.local.AppSessionEntity
import com.digitalparenting.data.local.AppUsageStats
import com.digitalparenting.data.local.BehaviorRecordDao
import com.digitalparenting.data.local.UserProfileDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MonitoringService : Service() {
    // TODO(FCM): Register child device FCM token and store at children/{childUid}.fcmToken.
    // TODO(FCM): Add FirebaseMessagingService to handle parent approval/denial push notifications.
    // TODO(FCM): Add silent push wake-up hook to refresh pending commands when app process is idle.

    private lateinit var notificationHelper: NotificationHelper
    private val handler = Handler(Looper.getMainLooper())
    private val predictionEvaluationDelayMillis = 15 * 60 * 1000L // 15 minutes real-world evaluation window

    private var currentApp: String? = null
    private var currentSession: AppSession? = null
    private var currentBlockedPackage: String? = null
    private val usageSyncRepository = UsageSyncRepository()
    private val childStatusPublisher by lazy { ChildStatusPublisher(this) }
    private val blockingUiController by lazy { ChildBlockingUiController(this) }
    private val protectionHealthController by lazy {
        ChildProtectionHealthController(
            context = this,
            notificationHelper = notificationHelper,
            onIncident = ::logIncident
        )
    }
    private val commandController by lazy {
        ChildCommandController(
            limitDao = limitDao,
            onBlockRequested = blockingUiController::launchBlockedScreen
        )
    }
    private val timeRequestController by lazy {
        ChildTimeRequestController(
            limitDao = limitDao,
            onTimeApplied = { approvedMinutes, appName ->
                notificationHelper.showChildAlert(
                    "Extra Time Approved",
                    "$approvedMinutes minutes added for $appName"
                )
            }
        )
    }
    private val authCoordinator by lazy {
        ChildAuthCoordinator(
            onAuthenticated = {
                childStatusPublisher.publishInitialStatus()
                timeRequestController.start()
                commandController.start()
            }
        )
    }

    private lateinit var database: AppDatabase
    private lateinit var dao: AppSessionDao
    private lateinit var limitDao: AppLimitDao
    private lateinit var behaviorRecordDao: BehaviorRecordDao
    private lateinit var userProfileDao: UserProfileDao
    private lateinit var predictionRecordDao: com.digitalparenting.data.local.PredictionRecordDao
    private lateinit var incidentDao: com.digitalparenting.data.local.ProtectionIncidentDao
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

        behaviorPredictor.loadState(this)
        println("Loaded BehaviorPredictor state: ${behaviorPredictor.getMetrics()}")
        authCoordinator.start()

        restoreProtectionState()
        updateForegroundNotification()
        startMonitoring()
        childStatusPublisher.start()

    }

    override fun onDestroy() {
        super.onDestroy()
        timeRequestController.stop()
        commandController.stop()
        childStatusPublisher.stop()
        authCoordinator.stop()
        blockingUiController.hideOverlay()
        currentSession?.let {
            it.endTime = System.currentTimeMillis()
            logSession(it)
        }
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
            blockingUiController.showBlockOverlay(currentApp)
        } else {
            blockingUiController.hideOverlay()
        }

        protectionHealthController.verify()
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
            appName = getAppName(newApp),
            startTime = now
        )

        currentApp = newApp

        println("Switched to: $newApp")

        CoroutineScope(Dispatchers.IO).launch {
            val limit = limitDao.getLimit(newApp)?.let { if (it.enabled) it.maxMinutes * 60_000L else 0L } ?: 0L

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

            val prediction = behaviorPredictor.predict(newApp, sessionsForApp, profile, sessionWindow)

            val insight = behaviorAnalyzer.analyze(newApp, sessionsForApp, limit, baseline)
            
            var mode = interventionEngine.decide(insight)
            if (prediction.likelyToBinge && mode.ordinal < BlockMode.DELAY.ordinal) {
                mode = BlockMode.DELAY
            }
            if (prediction.riskScore >= 80 && mode.ordinal < BlockMode.BLOCKED.ordinal) {
                mode = BlockMode.BLOCKED
            }

            BlockStateManager.setBlockMode(newApp, mode)

            if (prediction.riskScore >= 80) {
                notificationHelper.showChildAlert(
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
                    handler.post { blockingUiController.hideOverlay() }
                }
                BlockMode.WARNING -> {
                    BlockStateManager.removeBlocked(newApp)
                    handler.post { blockingUiController.showWarningOverlay(currentApp) }
                }
                BlockMode.DELAY -> {
                    BlockStateManager.removeBlocked(newApp)
                    handler.postDelayed({
                        blockingUiController.showBlockOverlay(currentApp)
                    }, 5000)
                }
                BlockMode.BLOCKED -> {
                    BlockStateManager.setBlocked(setOf(newApp))
                    handler.post { blockingUiController.showBlockOverlay(currentApp) }
                    notificationHelper.showChildAlert(
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
                evaluatePredictionOutcome(predictionId, newApp, prediction, profile)
            }

            println("Prediction explainability: risk=${prediction.riskScore}, reason=${prediction.reason}")
            println("Behavior: Score=${insight.addictionScore} Risk=${insight.riskLevel} Mode=$mode App=$newApp")
            println("Prediction: Binge=${prediction.likelyToBinge} Risk=${prediction.riskScore} Next=${prediction.predictedNextApp} Reason=${prediction.reason}")
        }
    }

    private suspend fun isLimitExceeded(packageName: String): Boolean {
        val limit = limitDao.getLimit(packageName) ?: return false

        if (!limit.enabled) return false

        val usageStats = dao.getUsageStats()
        val usage = usageStats.find { it.packageName == packageName }

        val maxMillis = limit.maxMinutes * 60_000L
        return usage != null && usage.totalTime >= maxMillis
    }

    private suspend fun checkAndEnforceLimit(packageName: String, appName: String) {
        val limit = limitDao.getLimit(packageName) ?: return

        if (!limit.enabled) return

        val todayUsage = dao.getUsageStats().firstOrNull { it.packageName == packageName }?.totalTime ?: 0L
        val maxMillis = limit.maxMinutes * 60_000L

        if (todayUsage >= maxMillis) {
            currentBlockedPackage = packageName
            blockingUiController.launchBlockedScreen(appName, packageName, "Daily limit exceeded")

            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun logSession(session: AppSession) {
        val duration = session.getDuration()

        val entity = AppSessionEntity(
            packageName = session.packageName,
            appName = session.appName ?: getAppName(session.packageName),
            startTime = session.startTime,
            endTime = session.endTime,
            duration = duration
        )

        CoroutineScope(Dispatchers.IO).launch {
            dao.insertSession(entity)

            checkAndEnforceLimit(entity.packageName, entity.appName ?: "Unknown App")

            usageSyncRepository.syncSession(entity)

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

        val windowStart = now - 15 * 60 * 1000L
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

    private fun restoreProtectionState() {
        val snapshot = ProtectionStateManager.restoreBlockState(this)
        if (snapshot.blockedPackages.isNotEmpty()) {
            BlockStateManager.setBlocked(snapshot.blockedPackages)
            snapshot.blockModes.forEach { (pkg, mode) ->
                BlockStateManager.setBlockMode(pkg, mode)
            }
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

    override fun onBind(intent: Intent?): IBinder? = null
}
