package com.digitalparenting.data.local

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface BehaviorRecordDao {
    @Insert
    suspend fun insert(record: com.digitalparenting.data.BehaviorRecord)
}
