package com.digitalparenting.service

import com.digitalparenting.data.local.ProtectionIncidentDao
import com.digitalparenting.data.local.ProtectionIncidentEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Persists local Child protection incidents off the main thread. */
internal class ChildIncidentRecorder(
    private val incidentDao: ProtectionIncidentDao
) {
    fun record(
        type: String,
        title: String,
        message: String,
        appPackage: String?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            incidentDao.insert(
                ProtectionIncidentEntity(
                    type = type,
                    title = title,
                    message = message,
                    appPackage = appPackage,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }
}
