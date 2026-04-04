package com.digitalparenting.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val avgDailyUsageMinutes: Long,
    val avgSessionCount: Int,
    val preferredApps: String, // comma separated
    val nightUsageBaselineMinutes: Long
) {
    fun preferredAppsList(): List<String> {
        return if (preferredApps.isBlank()) emptyList() else preferredApps.split(",").map { it.trim() }
    }
}
