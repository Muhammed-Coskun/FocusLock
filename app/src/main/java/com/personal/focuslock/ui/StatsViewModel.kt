package com.personal.focuslock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.focuslock.Constants
import com.personal.focuslock.data.AppDayStats
import com.personal.focuslock.data.StatsDay
import com.personal.focuslock.data.StatsRepository
import com.personal.focuslock.data.WebMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Collections

data class StatsAppRow(
    val packageName: String,
    val label: String,
    val iconBitmap: android.graphics.Bitmap?,
    val usedSeconds: Int,
    val blockCount: Int,
    val emergencyUnlockCount: Int
)

data class DayBar(
    val date: String,           // yyyy-MM-dd
    val weekdayLabel: String,   // "Mo" "Di" ...
    val usedMinutes: Int,
    val isToday: Boolean
)

data class StatsUi(
    val streak: Int = 0,
    val today: StatsDay = StatsDay(date = ""),
    val perAppToday: List<StatsAppRow> = emptyList(),
    val last7Days: List<DayBar> = emptyList()
)

class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val statsRepo = StatsRepository.get(app)
    private val infoCache = Collections.synchronizedMap(HashMap<String, InstalledApp?>())
    private val streakFlow = MutableStateFlow(0)
    private val tickFlow = MutableStateFlow(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            while (true) {
                streakFlow.value = runCatching { statsRepo.streak() }.getOrDefault(0)
                tickFlow.value = System.currentTimeMillis()
                kotlinx.coroutines.delay(10_000)
            }
        }
    }

    val ui: StateFlow<StatsUi> = combine(
        statsRepo.historyFlow,
        streakFlow,
        tickFlow
    ) { history, streak, _ ->
        val todayKey = LocalDate.now().toString()
        val today = history.day(todayKey) ?: StatsDay(date = todayKey)

        val rows = today.perApp.entries.map { (pkg, appStats) ->
            if (WebMonitor.isWebKey(pkg)) {
                StatsAppRow(
                    packageName = pkg,
                    label = WebMonitor.hostOf(pkg),
                    iconBitmap = null,
                    usedSeconds = appStats.usedSeconds,
                    blockCount = appStats.blockCount,
                    emergencyUnlockCount = appStats.emergencyUnlockCount
                )
            } else {
                val cached = synchronized(infoCache) {
                    if (infoCache.containsKey(pkg)) infoCache[pkg] else null
                }
                val info = cached ?: runCatching {
                    withContext(Dispatchers.IO) { AppInfoLoader.load(getApplication(), pkg) }
                }.getOrNull().also {
                    synchronized(infoCache) { infoCache[pkg] = it }
                }
                StatsAppRow(
                    packageName = pkg,
                    label = info?.label ?: pkg,
                    iconBitmap = info?.icon,
                    usedSeconds = appStats.usedSeconds,
                    blockCount = appStats.blockCount,
                    emergencyUnlockCount = appStats.emergencyUnlockCount
                )
            }
        }.sortedByDescending { it.usedSeconds }

        val last7 = build7DayBars(history.days)

        StatsUi(streak = streak, today = today, perAppToday = rows, last7Days = last7)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUi())

    private fun build7DayBars(days: List<StatsDay>): List<DayBar> {
        val today = LocalDate.now()
        val labels = arrayOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So") // ISO 1..7
        return (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val key = date.toString()
            val day = days.firstOrNull { it.date == key }
            DayBar(
                date = key,
                weekdayLabel = labels[date.dayOfWeek.value - 1],
                usedMinutes = ((day?.totalUsedSeconds() ?: 0) / 60),
                isToday = (offset == 0)
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun statsHistoryDays(): Int = Constants.STATS_HISTORY_DAYS
}
