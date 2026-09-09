package com.example.receiptapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.receiptapp.data.model.UserGoals
import com.example.receiptapp.data.settings.UserGoalsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val goalsRepository: UserGoalsRepository
) : ViewModel() {

    val goals: StateFlow<UserGoals> = goalsRepository.goalsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserGoals())

    fun updateWeeklySnackBudget(value: Double) {
        viewModelScope.launch {
            goalsRepository.updateGoals(goals.value.copy(weeklySnackBudgetEuro = value))
        }
    }

    fun updateReduceProcessedMeat(value: Boolean) {
        viewModelScope.launch {
            goalsRepository.updateGoals(goals.value.copy(reduceProcessedMeat = value))
        }
    }
}
