package com.digitalparenting.data

import java.util.*

class BehaviorAnalyzer {

    fun calculateBaseline(sessions: List<AppSession>, days: Int = 7): Double {
        if (sessions.isEmpty()) return 0.0

        val now = System.currentTimeMillis()
        val earliest = now - days * 24L * 60L * 60L * 1000L
        val windowSessions = sessions.filter { it.startTime >= earliest }

        if (windowSessions.isEmpty()) return 0.0

        val totalMinutes = windowSessions.sumOf { it.getDuration() } / (1000.0 * 60.0)
        val daysInWindow = days.coerceAtLeast(1)

        return totalMinutes / daysInWindow
    }

    fun deriveUserProfile(sessions: List<AppSession>, days: Int = 7): UserProfile {
        val baselineUsage = calculateBaseline(sessions, days)

        val now = System.currentTimeMillis()
        val earliest = now - days * 24L * 60L * 60L * 1000L
        val windowSessions = sessions.filter { it.startTime >= earliest }

        val avgSessionCount = if (days > 0) (windowSessions.size / days.toDouble()).toInt() else 0

        val preferredApps = windowSessions.groupBy { it.packageName }
            .mapValues { entry -> entry.value.sumOf { it.getDuration() } }
            .entries.sortedByDescending { it.value }
            .take(5)
            .map { it.key }
            .joinToString(",")

        val nightUsageBaseline = windowSessions.filter {
            val hour = Calendar.getInstance().apply { timeInMillis = it.startTime }.get(Calendar.HOUR_OF_DAY)
            hour in 0..5
        }.sumOf { it.getDuration() } / (1000L * 60L)

        return UserProfile(
            avgDailyUsageMinutes = baselineUsage.toLong(),
            avgSessionCount = avgSessionCount,
            preferredApps = preferredApps,
            nightUsageBaselineMinutes = nightUsageBaseline
        )
    }

    fun analyze(app: String, sessions: List<AppSession>, dailyLimitMillis: Long, baselineDailyUsageMinutes: Double): BehaviorInsight {
        val totalUsageMinutes = sessions.sumOf { it.getDuration() } / (1000.0 * 60.0)

        val usageRatio = if (baselineDailyUsageMinutes == 0.0) 1.0 else totalUsageMinutes / baselineDailyUsageMinutes

        val isBinge = usageRatio > 1.5

        val frequentSessions = sessions.count { it.getDuration() < 60_000 } > 10

        val nightUsageMinutes = sessions.filter { session ->
            val hour = Calendar.getInstance().apply { timeInMillis = session.startTime }.get(Calendar.HOUR_OF_DAY)
            hour in 0..5
        }.sumOf { it.getDuration() } / (1000.0 * 60.0)

        val rapidSwitching = calculateRapidSwitching(sessions)

        val score = calculateScore(
            isBinge = isBinge,
            frequentSessions = frequentSessions,
            nightUsageMinutes = nightUsageMinutes,
            rapidSwitching = rapidSwitching,
            usageRatio = usageRatio,
            limitMinutes = dailyLimitMillis / (1000.0 * 60.0)
        )

        val riskLevel = when {
            score < 30 -> RiskLevel.LOW
            score < 60 -> RiskLevel.MEDIUM
            score < 80 -> RiskLevel.HIGH
            else -> RiskLevel.CRITICAL
        }

        return BehaviorInsight(
            addictionScore = score,
            isBinge = isBinge,
            frequentSessions = frequentSessions,
            nightUsageMinutes = nightUsageMinutes.toLong(),
            riskLevel = riskLevel
        )
    }

    private fun calculateRapidSwitching(sessions: List<AppSession>): Boolean {
        if (sessions.size < 2) return false
        val sortedSessions = sessions.sortedBy { it.startTime }
        val gaps = mutableListOf<Long>()

        for (i in 1 until sortedSessions.size) {
            val gap = sortedSessions[i].startTime - sortedSessions[i - 1].endTime
            gaps.add(gap)
        }

        return gaps.average() < 10_000
    }

    private fun calculateScore(
        isBinge: Boolean,
        frequentSessions: Boolean,
        nightUsageMinutes: Double,
        rapidSwitching: Boolean,
        usageRatio: Double,
        limitMinutes: Double
    ): Int {
        var score = 0

        if (isBinge) score += 30
        if (frequentSessions) score += 20
        if (nightUsageMinutes > 30) score += 20
        if (rapidSwitching) score += 15

        if (usageRatio > 1.2) score += 10
        if (usageRatio > 1.5) score += 15
        if (usageRatio > 2.0) score += 15

        if (limitMinutes > 0 && limitMinutes > 120) score += 10

        return score.coerceAtMost(100)
    }
}
