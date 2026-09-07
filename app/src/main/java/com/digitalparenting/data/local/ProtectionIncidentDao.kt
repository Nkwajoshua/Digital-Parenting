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
}
