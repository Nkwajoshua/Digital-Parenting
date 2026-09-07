package com.digitalparenting.service

import android.app.ActivityManager
import android.content.Context
import com.digitalparenting.data.AppSession
import com.digitalparenting.data.local.AppLimitDao
import com.digitalparenting.data.local.AppSessionDao
import com.digitalparenting.data.local.AppSessionEntity
import com.digitalparenting.data.repository.UsageSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Persists completed Child app sessions, enforces configured daily limits, and
 * syncs the recorded session to the authenticated Child cloud path.
 *
 * Behavior prediction/intervention remains outside this component.
 */
internal class ChildSessionRecorder(
    context: Context,
    private val sessionDao: AppSessionDao,
    private val limitDao: AppLimitDao,
    private val onLimitExceeded: (appName: String, appPackage: String, reason: String) -> Unit,
    private val usageSyncRepository: UsageSyncRepository = UsageSyncRepository()
) {
    private val appContext = context.applicationContext

    fun record(session: AppSession, resolvedAppName: String) {
        val duration = session.getDuration()
        val entity = AppSessionEntity(
            packageName = session.packageName,
            appName = resolvedAppName,
            startTime = session.startTime,
            endTime = session.endTime,
            duration = duration
        )

        CoroutineScope(Dispatchers.IO).launch {
            sessionDao.insertSession(entity)
            enforceDailyLimit(entity.packageName, entity.appName ?: "Unknown App")
            usageSyncRepository.syncSession(entity)

            val durationSeconds = duration / 1000
            println(
                "DB: App: ${session.packageName} Start: ${formatTime(session.startTime)} " +
                    "End: ${formatTime(session.endTime)} Duration: ${durationSeconds}s"
            )
        }
    }

    private suspend fun enforceDailyLimit(packageName: String, appName: String) {
        val limit = limitDao.getLimit(packageName) ?: return
        if (!limit.enabled) return

        val todayUsage = sessionDao.getUsageStats()
            .firstOrNull { it.packageName == packageName }
            ?.totalTime ?: 0L
        val maxMillis = limit.maxMinutes * 60_000L

        if (todayUsage >= maxMillis) {
            onLimitExceeded(appName, packageName, "Daily limit exceeded")
            val activityManager =
                appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.killBackgroundProcesses(packageName)
        }
    }

    private fun formatTime(timestamp: Long): String {
        return java.text.SimpleDateFormat(
            "HH:mm",
            java.util.Locale.getDefault()
        ).format(java.util.Date(timestamp))
    }
}
