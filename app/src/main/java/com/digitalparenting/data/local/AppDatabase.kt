package com.digitalparenting.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AppSessionEntity::class, AppLimit::class, com.digitalparenting.data.UserProfile::class, com.digitalparenting.data.BehaviorRecord::class, com.digitalparenting.data.PredictionRecordEntity::class, ProtectionIncidentEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appSessionDao(): AppSessionDao
    abstract fun appLimitDao(): AppLimitDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun behaviorRecordDao(): BehaviorRecordDao
    abstract fun predictionRecordDao(): com.digitalparenting.data.local.PredictionRecordDao
    abstract fun protectionIncidentDao(): ProtectionIncidentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "digital_parenting_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
