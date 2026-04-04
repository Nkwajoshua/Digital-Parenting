package com.digitalparenting.data

data class ProtectionIncident(
    val id: Int = 0,
    val type: String, // "app_blocked", "high_risk", "accessibility_disabled", "overlay_missing"
    val title: String,
    val message: String,
    val appPackage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val resolved: Boolean = false
)

enum class ProtectionStatus {
    HEALTHY,
    WARNING,
    CRITICAL
}
