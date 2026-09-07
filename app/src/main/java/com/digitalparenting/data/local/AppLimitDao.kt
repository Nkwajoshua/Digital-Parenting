package com.digitalparenting.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppLimitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setLimit(limit: AppLimit)

    @Query("SELECT * FROM app_limits WHERE packageName = :pkg")
    suspend fun getLimit(pkg: String): AppLimit?
}
