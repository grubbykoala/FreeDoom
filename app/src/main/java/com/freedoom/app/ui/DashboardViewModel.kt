package com.freedoom.app.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freedoom.app.data.AppDatabase
import com.freedoom.app.data.DayRange
import com.freedoom.app.usage.DayUsage
import com.freedoom.app.usage.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppRow(val packageName: String, val label: String, val ms: Long)

data class UsageUi(val totalMs: Long, val unlocks: Int?, val apps: List<AppRow>)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = UsageRepository(app)
    private val videoDao = AppDatabase.get(app).shortVideoDao()
    private val day = MutableStateFlow(DayRange.today())

    private val _usage = MutableStateFlow<UsageUi?>(null)
    val usage: StateFlow<UsageUi?> = _usage

    val reelsToday: StateFlow<Int> = day
        .flatMapLatest { videoDao.observeCount(it.start, it.end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val averageWatchMs: StateFlow<Double?> = day
        .flatMapLatest { videoDao.observeAverageWatchMs(it.start, it.end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() {
        val today = DayRange.today()
        if (today != day.value) day.value = today
        viewModelScope.launch {
            val usage = withContext(Dispatchers.IO) { repo.refresh(today)?.let(::toUi) }
            _usage.value = usage
        }
    }

    private fun toUi(usage: DayUsage): UsageUi {
        val pm = getApplication<Application>().packageManager
        return UsageUi(
            totalMs = usage.totalMs,
            unlocks = usage.unlocks,
            apps = usage.perApp.map { (pkg, ms) -> AppRow(pkg, labelOf(pm, pkg), ms) },
        )
    }

    private fun labelOf(pm: PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
