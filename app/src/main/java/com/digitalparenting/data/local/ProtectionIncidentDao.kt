package com.digitalparenting.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ProtectionIncidentDao {
    @Insert
    suspend fun insert(incident: ProtectionIncidentEntity): Long

    @Query("SELECT * FROM protection_incidents WHERE resolved = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentIncidents(limit: Int): List<ProtectionIncidentEntity>

    @Query("SELECT * FROM protection_incidents ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllIncidents(limit: Int): List<ProtectionIncidentEntity>

    @Query("UPDATE protection_incidents SET resolved = 1 WHERE id = :id")
    suspend fun markResolved(id: Int)

    @Query("DELETE FROM protection_incidents WHERE timestamp < :beforeTime")
    suspend fun deleteOlderThan(beforeTime: Long)
}
