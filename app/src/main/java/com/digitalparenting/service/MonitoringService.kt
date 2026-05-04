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
import android.util.Log
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
import com.digitalparenting.data.local.AppLimit
import android.app.ActivityManager
import com.digitalparenting.data.repository.UsageSyncRepository
import com.digitalparenting.data.local.AppLimitDao
import com.digitalparenting.data.local.AppSessionDao
import com.digitalparenting.data.local.AppSessionEntity
import com.digitalparenting.data.local.AppUsageStats
import com.digitalparenting.data.local.BehaviorRecordDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.digitalparenting.data.local.UserProfileDao
import com.digitalparenting.ui.BlockActivity
import com.digitalparenting.ui.BlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

class MonitoringService : Service() {

    private lateinit var notificationHelper: NotificationHelper
    private val handler = Handler(Looper.getMainLooper())
    private val predictionEvaluationDelayMillis = 15 * 60 * 1000L // 15 minutes real-world evaluation window

    private var currentApp: String? = null
    private var currentSession: AppSession? = null
    private var accessibilityAlertShown = false
    private var overlayAlertShown = false
    private var currentBlockedPackage: String? = null
    private var blockedScreenShowingFor: String? = null
    private var lastBlockedLaunchTime: Long = 0L
    private val blockedActivityIntent by lazy {
        Intent(this, BlockActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }
    private val usageSyncRepository = UsageSyncRepository()
    private var timeRequestListener: ListenerRegistration? = null
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val childUid: String by lazy {
        // Reuse the same device ID we already register
        Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-child"
    }
    private var firestoreListener: ListenerRegistration? = null
    private var commandListener: ListenerRegistration? = null

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
        
        // Log the Firebase UID for Firestore setup
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: "ERROR_NO_USER"
        Log.e("CHILD_UID_FIRESTORE", "👉 USE THIS UID IN FIRESTORE: $uid 👈")
        
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
        startApprovedTimeRequestListener()
        startRemoteCommandListener()

        restoreProtectionState()
        updateForegroundNotification()
        startMonitoring()

        // Set up Firebase listener for remote control
        setupFirebaseListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        timeRequestListener?.remove()
        timeRequestListener = null
        firestoreListener?.remove()
        commandListener?.remove()
        commandListener = null
        hideBlockOverlay()
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
            appName = getAppName(newApp),
            startTime = now
        )

        currentApp = newApp

        println("Switched to: $newApp")

        // 🔥 INSTANT enforcement (synchronous, no delay)
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

        if (!limit.enabled) return false

        val usageStats = dao.getUsageStats()
        val usage = usageStats.find { it.packageName == packageName }

        val maxMillis = limit.maxMinutes * 60_000L
        return usage != null && usage.totalTime >= maxMillis
    }

    private suspend fun checkAndEnforceLimit(packageName: String, appName: String) {
        val limit = limitDao.getLimit(packageName) ?: return  // no limit set = allowed

        if (!limit.enabled) return

        // Simple daily total check (you can make this more advanced later)
        val todayUsage = dao.getUsageStats().firstOrNull { it.packageName == packageName }?.totalTime ?: 0L
        val maxMillis = limit.maxMinutes * 60_000L

        if (todayUsage >= maxMillis) {
            currentBlockedPackage = packageName
            launchBlockedScreen(appName, packageName, "Daily limit exceeded")

            // Optional: force-stop the app
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
        }
    }

    private fun launchBlockedScreen(appName: String, appPackage: String, reason: String) {
        val now = System.currentTimeMillis()

        if (blockedScreenShowingFor == appPackage && now - lastBlockedLaunchTime < 3000) {
            return
        }

        blockedScreenShowingFor = appPackage
        lastBlockedLaunchTime = now

        val intent = Intent(this, BlockedActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("appName", appName)
            putExtra("appPackage", appPackage)
            putExtra("reason", reason)
        }

        startActivity(intent)
    }

    private fun clearBlockedScreenStateIfNeeded(currentApp: String?) {
        if (currentApp == null) return
        if (blockedScreenShowingFor != null && blockedScreenShowingFor != currentApp) {
            blockedScreenShowingFor = null
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

    private fun startApprovedTimeRequestListener() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.d("TimeRequest", "No Firebase user. Skipping time request listener.")
            return
        }

        timeRequestListener?.remove()

        timeRequestListener = firestore.collection("time_requests")
            .whereEqualTo("childUid", user.uid)
            .whereEqualTo("status", "approved")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("TimeRequest", "Listener failed", error)
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) return@addSnapshotListener

                for (doc in snapshots.documents) {
                    val appPackage = doc.getString("appPackage") ?: continue
                    val appName = doc.getString("appName") ?: appPackage
                    val approvedMinutesLong = doc.getLong("approvedMinutes") ?: doc.getLong("requestedMinutes") ?: 0L
                    val approvedMinutes = approvedMinutesLong.toInt()

                    if (approvedMinutes <= 0) continue

                    applyApprovedTimeRequest(
                        requestId = doc.id,
                        appPackage = appPackage,
                        appName = appName,
                        approvedMinutes = approvedMinutes
                    )
                }
            }
    }

    private fun applyApprovedTimeRequest(
        requestId: String,
        appPackage: String,
        appName: String,
        approvedMinutes: Int
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val existingLimit = limitDao.getLimit(appPackage)

                if (existingLimit != null) {
                    val newMinutes = existingLimit.maxMinutes + approvedMinutes

                    val updatedLimit = existingLimit.copy(
                        appName = if (existingLimit.appName.isBlank()) appName else existingLimit.appName,
                        maxMinutes = newMinutes,
                        enabled = true
                    )

                    limitDao.setLimit(updatedLimit)

                    Log.d(
                        "TimeRequest",
                        "Applied approved request: $appPackage +$approvedMinutes min -> $newMinutes min"
                    )
                } else {
                    val newLimit = AppLimit(
                        packageName = appPackage,
                        appName = appName,
                        maxMinutes = approvedMinutes,
                        enabled = true,
                        lastReset = System.currentTimeMillis()
                    )

                    limitDao.setLimit(newLimit)

                    Log.d(
                        "TimeRequest",
                        "Created new limit from approved request: $appPackage = $approvedMinutes min"
                    )
                }

                firestore.collection("time_requests")
                    .document(requestId)
                    .update(
                        mapOf(
                            "status" to "applied",
                            "appliedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                    )

                withContext(Dispatchers.Main) {
                    notificationHelper.showRiskAlert(
                        "Extra Time Approved",
                        "$approvedMinutes minutes added for $appName"
                    )
                }
            } catch (e: Exception) {
                Log.e("TimeRequest", "Failed to apply approved request", e)
            }
        }
    }

    private fun startRemoteCommandListener() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.d("RemoteCommand", "No Firebase user. Skipping command listener.")
            return
        }

        commandListener?.remove()

        commandListener = firestore
            .collection("children")
            .document(user.uid)
            .collection("commands")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("RemoteCommand", "Command listener failed", error)
                    return@addSnapshotListener
                }

                if (snapshots == null || snapshots.isEmpty) return@addSnapshotListener

                for (doc in snapshots.documents) {
                    val commandId = doc.id
                    val type = doc.getString("type") ?: continue
                    val appPackage = doc.getString("appPackage") ?: ""
                    val appName = doc.getString("appName") ?: appPackage
                    val reason = doc.getString("reason") ?: "Blocked by parent"
                    val maxMinutes = (doc.getLong("maxMinutes") ?: 0L).toInt()
                    val enabled = doc.getBoolean("enabled") ?: true

                    handleRemoteCommand(
                        childUid = user.uid,
                        commandId = commandId,
                        type = type,
                        appPackage = appPackage,
                        appName = appName,
                        reason = reason,
                        maxMinutes = maxMinutes,
                        enabled = enabled
                    )
                }
            }
    }

    private fun handleRemoteCommand(
        childUid: String,
        commandId: String,
        type: String,
        appPackage: String,
        appName: String,
        reason: String,
        maxMinutes: Int,
        enabled: Boolean
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (type) {
                    "block_app" -> {
                        BlockStateManager.setBlocked(setOf(appPackage))
                        launchBlockedScreen(
                            appName = appName,
                            appPackage = appPackage,
                            reason = reason
                        )
                    }

                    "unblock_app" -> {
                        BlockStateManager.removeBlocked(appPackage)
                    }

                    "set_limit" -> {
                        val existing = limitDao.getLimit(appPackage)

                        val updatedLimit = if (existing != null) {
                            existing.copy(
                                appName = if (existing.appName.isBlank()) appName else existing.appName,
                                maxMinutes = maxMinutes,
                                enabled = enabled
                            )
                        } else {
                            AppLimit(
                                packageName = appPackage,
                                appName = appName,
                                maxMinutes = maxMinutes,
                                enabled = enabled,
                                lastReset = System.currentTimeMillis()
                            )
                        }

                        limitDao.setLimit(updatedLimit)
                    }

                    else -> {
                        markCommandFailed(
                            childUid = childUid,
                            commandId = commandId,
                            errorMessage = "Unknown command type: $type"
                        )
                        return@launch
                    }
                }

                markCommandHandled(childUid, commandId)

            } catch (e: Exception) {
                Log.e("RemoteCommand", "Failed to handle command $commandId", e)
                markCommandFailed(
                    childUid = childUid,
                    commandId = commandId,
                    errorMessage = e.message ?: "Unknown error"
                )
            }
        }
    }

    private fun markCommandHandled(childUid: String, commandId: String) {
        firestore.collection("children")
            .document(childUid)
            .collection("commands")
            .document(commandId)
            .update(
                mapOf(
                    "status" to "handled",
                    "handledAt" to FieldValue.serverTimestamp()
                )
            )
    }

    private fun markCommandFailed(childUid: String, commandId: String, errorMessage: String) {
        firestore.collection("children")
            .document(childUid)
            .collection("commands")
            .document(commandId)
            .update(
                mapOf(
                    "status" to "failed",
                    "handledAt" to FieldValue.serverTimestamp(),
                    "errorMessage" to errorMessage
                )
            )
    }

    private fun setupFirebaseListener() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user != null) {
            firestoreListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("children")
                .document(user.uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("Firestore", "Listen failed", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val blockApp = snapshot.getString("blockApp")
                        if (blockApp != null) {
                            BlockStateManager.setBlocked(setOf(blockApp))
                            handler.post { showBlockOverlay() }
                        } else {
                            BlockStateManager.clearBlocked()
                            handler.post { hideBlockOverlay() }
                        }
                    }
                }
        }
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
            appName = session.appName ?: getAppName(session.packageName),
            startTime = session.startTime,
            endTime = session.endTime,
            duration = duration
        )

        CoroutineScope(Dispatchers.IO).launch {
            dao.insertSession(entity)

            // Add this right after dao.insertSession(entity)
            checkAndEnforceLimit(entity.packageName, entity.appName ?: "Unknown App")

            // Add right after the blocking check
            usageSyncRepository.syncSession(childUid, entity)

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

    override fun onBind(intent: Intent?): IBinder? = null
}
