package com.digitalparenting.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PredictionRecordDao {
    @Insert
    suspend fun insert(record: com.digitalparenting.data.PredictionRecordEntity): Long

    @Query("UPDATE prediction_records SET wasAccurate = :wasAccurate, actualOutcomeScore = :actualOutcomeScore WHERE id = :id")
    suspend fun updateOutcome(id: Int, wasAccurate: Boolean, actualOutcomeScore: Int)
}
