package com.personal.focuslock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personal.focuslock.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.statsDataStore: DataStore<Preferences> by preferencesDataStore(name = "focuslock_stats")

/**
 * Stores per-day per-app usage statistics. Usage is batched in memory and flushed every
 * STATS_FLUSH_INTERVAL_MS to avoid hammering DataStore with one write per second.
 *
 * Counters (block / attempt / emergency unlock) are written immediately because they
 * are rare events.
 */
class StatsRepository private constructor(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val history = stringPreferencesKey("history_json")
    }

    private val flushScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingMutex = Mutex()
    private val pendingUsageMs = HashMap<String, Long>()

    init {
        flushScope.launch {
            while (true) {
                delay(STATS_FLUSH_INTERVAL_MS)
                runCatching { flush() }
            }
        }
    }

    val historyFlow: Flow<StatsHistory> = context.statsDataStore.data.map { prefs ->
        prefs[Keys.history]?.let { decode(it) } ?: StatsHistory()
    }

    /** Buffer foreground-usage delta. Cheap, never blocks the service loop. */
    fun recordUsage(pkg: String, deltaMs: Long) {
        if (deltaMs <= 0) return
        flushScope.launch {
            pendingMutex.withLock {
                pendingUsageMs[pkg] = (pendingUsageMs[pkg] ?: 0L) + deltaMs
            }
        }
    }

    suspend fun recordBlock(pkg: String) = bumpToday(pkg) { it.copy(blockCount = it.blockCount + 1) }
    suspend fun recordAttempt(pkg: String) = bumpToday(pkg) { it.copy(attemptCount = it.attemptCount + 1) }
    suspend fun recordEmergencyUnlock(pkg: String) =
        bumpToday(pkg) { it.copy(emergencyUnlockCount = it.emergencyUnlockCount + 1) }

    /** Days back from today with zero emergency unlocks across all apps. */
    suspend fun streak(): Int {
        flush() // make sure today's data is current
        val history = historyFlow.first()
        val today = LocalDate.now()
        var streak = 0
        for (i in 0 until Constants.STATS_HISTORY_DAYS) {
            val date = today.minusDays(i.toLong()).toString()
            val day = history.day(date)
            val unlocks = day?.totalEmergencyUnlocks() ?: 0
            if (unlocks > 0) break
            streak++
        }
        return streak
    }

    /** Last `n` days inclusive (oldest first), filling gaps with empty StatsDay entries. */
    suspend fun lastNDays(n: Int): List<StatsDay> {
        val history = historyFlow.first()
        val today = LocalDate.now()
        return (n - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong()).toString()
            history.day(date) ?: StatsDay(date = date)
        }
    }

    private suspend fun flush() {
        val snapshot = pendingMutex.withLock {
            if (pendingUsageMs.isEmpty()) return
            val copy = HashMap(pendingUsageMs)
            pendingUsageMs.clear()
            copy
        }
        val today = LocalDate.now().toString()
        context.statsDataStore.edit { prefs ->
            val existing = prefs[Keys.history]?.let { decode(it) } ?: StatsHistory()
            val mergedDays = mergeUsage(existing.days, today, snapshot)
            prefs[Keys.history] = encode(StatsHistory(days = trimOldDays(mergedDays)))
        }
    }

    private suspend fun bumpToday(pkg: String, mutate: (AppDayStats) -> AppDayStats) {
        val today = LocalDate.now().toString()
        context.statsDataStore.edit { prefs ->
            val existing = prefs[Keys.history]?.let { decode(it) } ?: StatsHistory()
            val updatedDays = updateDay(existing.days, today) { day ->
                val app = day.perApp[pkg] ?: AppDayStats()
                day.copy(perApp = day.perApp + (pkg to mutate(app)))
            }
            prefs[Keys.history] = encode(StatsHistory(days = trimOldDays(updatedDays)))
        }
    }

    private fun mergeUsage(
        days: List<StatsDay>,
        today: String,
        usageMs: Map<String, Long>
    ): List<StatsDay> = updateDay(days, today) { day ->
        val mergedPerApp = day.perApp.toMutableMap()
        for ((pkg, ms) in usageMs) {
            val current = mergedPerApp[pkg] ?: AppDayStats()
            val addedSeconds = (ms / 1000).toInt()
            if (addedSeconds <= 0) continue
            mergedPerApp[pkg] = current.copy(usedSeconds = current.usedSeconds + addedSeconds)
        }
        day.copy(perApp = mergedPerApp.toMap())
    }

    private fun updateDay(
        days: List<StatsDay>,
        date: String,
        update: (StatsDay) -> StatsDay
    ): List<StatsDay> {
        val existing = days.firstOrNull { it.date == date }
        return if (existing == null) {
            days + update(StatsDay(date = date))
        } else {
            days.map { if (it.date == date) update(it) else it }
        }
    }

    private fun trimOldDays(days: List<StatsDay>): List<StatsDay> {
        if (days.size <= Constants.STATS_HISTORY_DAYS) return days
        val sorted = days.sortedBy { it.date }
        return sorted.takeLast(Constants.STATS_HISTORY_DAYS)
    }

    private fun encode(h: StatsHistory): String =
        json.encodeToString(StatsHistory.serializer(), h)

    private fun decode(text: String): StatsHistory =
        runCatching { json.decodeFromString(StatsHistory.serializer(), text) }
            .getOrDefault(StatsHistory())

    companion object {
        private const val STATS_FLUSH_INTERVAL_MS = 15_000L

        @Volatile private var instance: StatsRepository? = null
        fun get(context: Context): StatsRepository =
            instance ?: synchronized(this) {
                instance ?: StatsRepository(context.applicationContext).also { instance = it }
            }
    }
}
