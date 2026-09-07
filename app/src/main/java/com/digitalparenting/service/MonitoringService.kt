package com.digitalparenting.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import com.digitalparenting.util.NotificationHelper
import com.digitalparenting.util.ProtectionStateManager
import com.digitalparenting.data.BlockStateManager
import com.digitalparenting.data.local.AppDatabase
import com.digitalparenting.data.local.ProtectionIncidentEntity
import com.digitalparenting.data.local.AppLimitDao
import com.digitalparenting.data.local.AppSessionDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MonitoringService : Service() {
    // TODO(FCM): Register child device FCM token and store at children/{childUid}.fcmToken.
    // TODO(FCM): Add FirebaseMessagingService to handle parent approval/denial push notifications.
    // TODO(FCM): Add silent push wake-up hook to refresh pending commands when app process is idle.

    private lateinit var notificationHelper: NotificationHelper
    private val handler = Handler(Looper.getMainLooper())

    private val childStatusPublisher by lazy { ChildStatusPublisher(this) }
    private val blockingUiController by lazy { ChildBlockingUiController(this) }
    private val sessionRecorder by lazy {
        ChildSessionRecorder(
            context = this,
            sessionDao = dao,
            limitDao = limitDao,
            onLimitExceeded = blockingUiController::launchBlockedScreen
        )
    }
    private val behaviorController by lazy {
        ChildBehaviorController(
            context = this,
            sessionDao = dao,
            limitDao = limitDao,
            behaviorRecordDao = database.behaviorRecordDao(),
            userProfileDao = database.userProfileDao(),
            predictionRecordDao = database.predictionRecordDao(),
            onChildAlert = { title, message ->
                notificationHelper.showChildAlert(title, message)
            },
            onIncident = ::logIncident,
            onHideOverlay = blockingUiController::hideOverlay,
            onShowWarningOverlay = blockingUiController::showWarningOverlay,
            onShowBlockOverlay = blockingUiController::showBlockOverlay,
            currentAppProvider = { foregroundSessionTracker.currentApp() }
        )
    }
    private val foregroundSessionTracker by lazy {
        ChildForegroundSessionTracker(
            context = this,
            onSessionCompleted = { session, appName ->
                sessionRecorder.record(session, appName)
            },
            onAppChanged = { packageName ->
                behaviorController.evaluate(packageName)
            }
        )
    }
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
    private lateinit var incidentDao: com.digitalparenting.data.local.ProtectionIncidentDao

    override fun onCreate() {
        super.onCreate()

        notificationHelper = NotificationHelper(this)
        notificationHelper.createChannels()
        startForeground(
            NotificationHelper.FOREGROUND_NOTIFICATION_ID,
            notificationHelper.buildForegroundNotification(
                "Monitoring usage and enforcing protections"
            )
        )

        database = AppDatabase.getDatabase(this)
        dao = database.appSessionDao()
        limitDao = database.appLimitDao()
        incidentDao = database.protectionIncidentDao()

        behaviorController.loadState()
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
        foregroundSessionTracker.finishCurrentSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        val restartServiceIntent = Intent(
            applicationContext,
            MonitoringService::class.java
        )
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
                foregroundSessionTracker.checkForForegroundApp()
                enforceBlockState()
                handler.postDelayed(this, 2000)
            }
        })
    }

    private fun enforceBlockState() {
        val currentApp = foregroundSessionTracker.currentApp()
        if (currentApp != null && BlockStateManager.isBlocked(currentApp)) {
            blockingUiController.showBlockOverlay(currentApp)
        } else {
            blockingUiController.hideOverlay()
        }

        protectionHealthController.verify()
        updateForegroundNotification()
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
        val currentApp = foregroundSessionTracker.currentApp()
        val contentText = buildString {
            append("Monitoring usage")
            if (!ProtectionStateManager.isAccessibilityEnabled(this@MonitoringService)) {
                append(" · accessibility disabled")
            }
            if (!ProtectionStateManager.isOverlayPermissionGranted(this@MonitoringService)) {
                append(" · overlay disabled")
            }
            if (currentApp != null && BlockStateManager.isBlocked(currentApp)) {
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
