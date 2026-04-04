package com.digitalparenting.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.digitalparenting.data.AppBehaviorModel
import com.digitalparenting.data.BehaviorPredictor
import com.digitalparenting.data.IntelligenceDashboardState
import com.digitalparenting.data.local.AppDatabase
import com.digitalparenting.data.repository.AppUsageRepository
import kotlinx.coroutines.launch

class IntelligenceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = AppUsageRepository(
        dao = database.appSessionDao(),
        limitDao = database.appLimitDao(),
        predictionRecordDao = database.predictionRecordDao()
    )

    private val behaviorModel = AppBehaviorModel()
    private val behaviorPredictor = BehaviorPredictor()

    private val _dashboardState = MutableLiveData(IntelligenceDashboardState())
    val dashboardState: LiveData<IntelligenceDashboardState> = _dashboardState

    fun loadDashboard() {
        viewModelScope.launch {
            behaviorPredictor.loadState(getApplication())
            behaviorModel.loadState(getApplication())

            val accuracy = behaviorPredictor.getAccuracy()
            val confidence = behaviorModel.getConfidence(accuracy)
            val weights = behaviorPredictor.getModelWeights()

            val modelWeight = confidence
            val ruleWeight = 1f - modelWeight

            val state = repository.getDashboardState(
                predictorAccuracy = accuracy,
                modelConfidence = confidence,
                modelWeights = weights,
                modelContribution = modelWeight,
                ruleContribution = ruleWeight
            )

            _dashboardState.postValue(state)
        }
    }
}
