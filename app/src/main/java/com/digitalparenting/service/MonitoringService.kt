package com.digitalparenting.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import com.digitalparenting.data.BlockStateManager
import com.digitalparenting.data.local.AppDatabase
import com.digitalparenting.data.local.AppLimitDao
import com.digitalparenting.data.local.AppSessionDao
import com.digitalparenting.util.NotificationHelper
import com.digitalparenting.util.ProtectionStateManager

class MonitoringService : Service() {

    private lateinit var notificationHelper: NotificationHelper
    private val handler = Handler(Looper.getMainLooper())

    private val childStatusPublisher by lazy { ChildStatusPublisher(this) }
    private val fcmTokenRegistrar by lazy { ChildFcmTokenRegistrar() }
    private val blockingUiController by lazy { ChildBlockingUiController(this) }
    private val incidentRecorder by lazy {
        ChildIncidentRecorder(database.protectionIncidentDao())
    }
    private val sessionRecorder by lazy {
        ChildSessionRecorder(
            context = this,
            sessionDao = dao,
            limitDao = limitDao,
            onLimitExceeded = blockingUiController::launchBlockedScreen
        )
    }
    private val behaviorController: ChildBehaviorController by lazy {
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
            onIncident = incidentRecorder::record,
            onHideOverlay = blockingUiController::hideOverlay,
            onShowWarningOverlay = blockingUiController::showWarningOverlay,
            onShowBlockOverlay = blockingUiController::showBlockOverlay,
            currentAppProvider = { foregroundSessionTracker.currentApp() }
        )
    }
    private val foregroundSessionTracker: ChildForegroundSessionTracker by lazy {
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
            onIncident = incidentRecorder::record
        )
    }
    private val commandController by lazy {
        ChildCommandController(
            context = this,
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
                fcmTokenRegistrar.registerCurrentToken()
                timeRequestController.start()
                commandController.start()
            }
        )
    }

    private lateinit var database: AppDatabase
    private lateinit var dao: AppSessionDao
    private lateinit var limitDao: AppLimitDao

    override fun onCreate() {
        super.onCreate()

        notificationHelper = NotificationHelper(this)
        notificationHelper.createChannels()
        promoteToForeground("Monitoring usage and enforcing protections")

        database = AppDatabase.getDatabase(this)
        dao = database.appSessionDao()
        limitDao = database.appLimitDao()

        behaviorController.loadState()
        authCoordinator.start()

        ProtectionStateManager.hydrateBlockState(this)
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

        promoteToForeground(contentText)
    }

    private fun promoteToForeground(contentText: String) {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
        }

        ServiceCompat.startForeground(
            this,
            NotificationHelper.FOREGROUND_NOTIFICATION_ID,
            notificationHelper.buildForegroundNotification(contentText),
            serviceType
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
