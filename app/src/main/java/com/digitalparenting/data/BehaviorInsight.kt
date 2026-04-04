package com.digitalparenting.data

data class BehaviorInsight(
    val addictionScore: Int,   // 0–100
    val isBinge: Boolean,
    val frequentSessions: Boolean,
    val nightUsageMinutes: Long,
    val riskLevel: RiskLevel
)

enum class RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class BlockMode {
    NONE,
    WARNING,
    DELAY,
    BLOCKED
}