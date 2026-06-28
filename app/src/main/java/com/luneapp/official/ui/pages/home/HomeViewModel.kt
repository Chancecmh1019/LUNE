package com.luneapp.official.ui.pages.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luneapp.official.domain.menstrual.CyclePhaseInfo
import com.luneapp.official.domain.menstrual.CycleState
import com.luneapp.official.domain.menstrual.MenstrualService
import com.luneapp.official.domain.settings.SettingsRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

class HomeViewModel(
    private val service: MenstrualService,
    private val settings: SettingsRepository,
) : ViewModel() {

    var cycleState by mutableStateOf<CycleState?>(null)
        private set
    
    var phaseInfo by mutableStateOf<CyclePhaseInfo?>(null)
        private set

    var isLoading by mutableStateOf(true)
        private set
    
    var homeMode by mutableStateOf(HomeMode.CALENDAR)
        private set

    var userStatus by mutableStateOf(com.luneapp.official.domain.settings.UserStatus.REGULAR)
        private set

    init {
        viewModelScope.launch {
            homeMode = HomeMode.fromKey(settings.getHomeMode())
            refresh()
        }
    }

    fun updateHomeMode(mode: HomeMode) {
        viewModelScope.launch {
            homeMode = mode
            settings.setHomeMode(mode.key)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val cycleLen = settings.getCycleLength()
            userStatus = settings.getUserStatus()  // Load userStatus BEFORE getCycleState
            val state = service.getCycleState(cycleLen, userStatus)  // Pass userStatus here!
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

            phaseInfo = CyclePhaseInfo.getPhaseInfo(today, state, cycleLen)
            cycleState = state
            isLoading = false
        }
    }
}
