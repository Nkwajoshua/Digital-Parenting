package com.digitalparenting.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.digitalparenting.data.AppSession

/**
 * Observes the foreground app and owns the active Child app-session boundary.
 *
 * This component only detects app switches and opens/closes AppSession values.
 * Persistence, limit enforcement, and behavior/prediction policy remain in
 * dedicated downstream components.
 */
internal class ChildForegroundSessionTracker(
    context: Context,
    private val onSessionCompleted: (session: AppSession, resolvedAppName: String) -> Unit,
    private val onAppChanged: (packageName: String) -> Unit
) {
    private val appContext = context.applicationContext
    private var currentApp: String? = null
    private var currentSession: AppSession? = null

    fun checkForForegroundApp() {
        val usageStatsManager =
            appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(now - 5000L, now)
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

    fun currentApp(): String? = currentApp

    fun finishCurrentSession() {
        currentSession?.let { session ->
            session.endTime = System.currentTimeMillis()
            onSessionCompleted(
                session,
                session.appName ?: resolveAppName(session.packageName)
            )
        }
    }

    private fun handleAppSwitch(newApp: String) {
        val now = System.currentTimeMillis()

        currentSession?.let { session ->
            session.endTime = now
            onSessionCompleted(
                session,
                session.appName ?: resolveAppName(session.packageName)
            )
        }

        currentSession = AppSession(
            packageName = newApp,
            appName = resolveAppName(newApp),
            startTime = now
        )
        currentApp = newApp

        println("Switched to: $newApp")
        onAppChanged(newApp)
    }

    private fun resolveAppName(packageName: String): String {
        return try {
            val packageManager = appContext.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (error: Exception) {
            packageName
        }
    }
}
