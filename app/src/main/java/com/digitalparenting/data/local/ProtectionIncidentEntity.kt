package com.digitalparenting.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "protection_incidents")
data class ProtectionIncidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,
    val title: String,
    val message: String,
    val appPackage: String? = null,
    val timestamp: Long,
    val resolved: Boolean = false
)
