package com.digitalparenting.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.digitalparenting.data.ProtectionStatus
import com.digitalparenting.data.local.AppDatabase
import com.digitalparenting.data.local.ProtectionIncidentEntity
import com.digitalparenting.util.ProtectionStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class ProtectionCenterState(
    val overallStatus: ProtectionStatus = ProtectionStatus.HEALTHY,
    val accessibilityEnabled: Boolean = false,
    val overlayGranted: Boolean = false,
    val monitoringActive: Boolean = false,
    val recentIncidents: List<ProtectionIncidentEntity> = emptyList()
)

class ProtectionCenterViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val incidentDao = database.protectionIncidentDao()

    private val _state = MutableLiveData<ProtectionCenterState>()
    val state: LiveData<ProtectionCenterState> = _state

    init {
        loadProtectionState()
    }

    fun loadProtectionState() {
        CoroutineScope(Dispatchers.IO).launch {
            val context = getApplication<Application>()
            val accessibilityEnabled = ProtectionStateManager.isAccessibilityEnabled(context)
            val overlayGranted = ProtectionStateManager.isOverlayPermissionGranted(context)
            val monitoringActive = true // We assume it's active if the app is running

            val incidents = incidentDao.getRecentIncidents(10)

            val overallStatus = when {
                !accessibilityEnabled || !overlayGranted -> ProtectionStatus.CRITICAL
                incidents.any { !it.resolved } -> ProtectionStatus.WARNING
                else -> ProtectionStatus.HEALTHY
            }

            val newState = ProtectionCenterState(
                overallStatus = overallStatus,
                accessibilityEnabled = accessibilityEnabled,
                overlayGranted = overlayGranted,
                monitoringActive = monitoringActive,
                recentIncidents = incidents
            )

            _state.postValue(newState)
        }
    }

    fun logIncident(type: String, title: String, message: String, appPackage: String? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val incident = ProtectionIncidentEntity(
                type = type,
                title = title,
                message = message,
                appPackage = appPackage,
                timestamp = System.currentTimeMillis()
            )
            incidentDao.insert(incident)
            loadProtectionState()
        }
    }

    fun clearOldIncidents() {
        CoroutineScope(Dispatchers.IO).launch {
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            incidentDao.deleteOlderThan(sevenDaysAgo)
        }
    }
}
