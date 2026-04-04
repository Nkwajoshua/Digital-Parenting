package com.digitalparenting.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BehaviorRecordDao {
    @Insert
    suspend fun insert(record: com.digitalparenting.data.BehaviorRecord)

    @Query("SELECT * FROM behavior_records ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecentRecords(): List<com.digitalparenting.data.BehaviorRecord>
}
