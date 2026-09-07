package com.digitalparenting.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PredictionRecordDao {
    @Insert
    suspend fun insert(record: com.digitalparenting.data.PredictionRecordEntity): Long

    @Update
    suspend fun update(record: com.digitalparenting.data.PredictionRecordEntity)

    @Query("UPDATE prediction_records SET wasAccurate = :wasAccurate, actualOutcomeScore = :actualOutcomeScore WHERE id = :id")
    suspend fun updateOutcome(id: Int, wasAccurate: Boolean, actualOutcomeScore: Int)

    @Query("SELECT * FROM prediction_records ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecent(): List<com.digitalparenting.data.PredictionRecordEntity>

    @Query("SELECT COUNT(*) FROM prediction_records WHERE wasAccurate = 1")
    suspend fun getAccuratePredictionCount(): Int

    @Query("SELECT COUNT(*) FROM prediction_records WHERE wasAccurate = 0")
    suspend fun getInaccuratePredictionCount(): Int

    @Query("SELECT AVG(riskScore) FROM prediction_records WHERE timestamp >= :fromTime")
    suspend fun getAverageRiskSince(fromTime: Long): Float?
}
