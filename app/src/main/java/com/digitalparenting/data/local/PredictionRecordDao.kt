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

    @Query("SELECT COUNT(*) FROM prediction_records")
    suspend fun getTotalPredictionCount(): Int

    @Query("SELECT * FROM prediction_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestPrediction(): com.digitalparenting.data.PredictionRecordEntity?

    @Query("SELECT AVG(riskScore) FROM prediction_records WHERE timestamp >= :fromTime")
    suspend fun getAverageRiskSince(fromTime: Long): Float?

    @Query("SELECT * FROM prediction_records ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentPredictions(limit: Int): List<com.digitalparenting.data.PredictionRecordEntity>

    @Query("""
        SELECT * FROM prediction_records
            WHERE wasAccurate IS NOT NULL
                ORDER BY timestamp DESC
                    LIMIT :limit
        """)
    suspend fun getRecentEvaluatedPredictions(limit: Int): List<com.digitalparenting.data.PredictionRecordEntity>

    @Query("SELECT * FROM prediction_records WHERE timestamp >= :sinceTime ORDER BY timestamp ASC")
    suspend fun getPredictionsSince(sinceTime: Long): List<com.digitalparenting.data.PredictionRecordEntity>

    @Query("SELECT AVG(riskScore) as avgRisk, strftime('%Y-%m-%d', timestamp/1000, 'unixepoch') as date FROM prediction_records WHERE timestamp >= :sinceTime GROUP BY strftime('%Y-%m-%d', timestamp/1000, 'unixepoch') ORDER BY date ASC")
    suspend fun getDailyAverageRiskSince(sinceTime: Long): List<com.digitalparenting.data.DailyRiskStats>
}
