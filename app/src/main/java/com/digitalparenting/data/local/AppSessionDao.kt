package com.digitalparenting.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AppSessionDao {
    @Insert
    suspend fun insertSession(session: AppSessionEntity)

    @Query("SELECT * FROM app_sessions ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<AppSessionEntity>

    @Query("SELECT * FROM app_sessions WHERE packageName = :packageName ORDER BY startTime DESC")
    suspend fun getSessionsForApp(packageName: String): List<AppSessionEntity>

    @Query("SELECT * FROM app_sessions WHERE startTime >= :from AND startTime <= :to ORDER BY startTime ASC")
    suspend fun getSessionsBetween(from: Long, to: Long): List<AppSessionEntity>

    @Query("""
        SELECT packageName, SUM(duration) as totalTime
        FROM app_sessions
        GROUP BY packageName
    """)
    suspend fun getUsageStats(): List<AppUsageStats>
}
