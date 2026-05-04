package com.digitalparenting.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.digitalparenting.data.local.AppDatabase
import com.digitalparenting.data.local.AppUsageStats
import com.digitalparenting.data.repository.AppUsageRepository
import kotlinx.coroutines.launch

class UsageViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionDao = AppDatabase.getDatabase(application).appSessionDao()
    private val limitDao = AppDatabase.getDatabase(application).appLimitDao()
    private val repository = AppUsageRepository(sessionDao, limitDao)

    private val _usageStats = MutableLiveData<List<AppUsageStats>>()
    val usageStats: LiveData<List<AppUsageStats>> = _usageStats

    fun loadUsageStats() {
        viewModelScope.launch {
            try {
                val data = repository.getUsageStats()
                _usageStats.postValue(data)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setLimit(packageName: String, maxMinutes: Int) {
        viewModelScope.launch {
            try {
                repository.setLimit(packageName, maxMinutes)
                println("Set limit for $packageName: $maxMinutes minutes")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
