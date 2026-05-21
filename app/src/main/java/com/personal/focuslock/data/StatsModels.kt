package com.personal.focuslock.data

import kotlinx.serialization.Serializable

/** Per-app stats for a single calendar day. Times are in seconds (kept small in JSON). */
@Serializable
data class AppDayStats(
    val usedSeconds: Int = 0,
    val blockCount: Int = 0,         // how many times block kicked in
    val attemptCount: Int = 0,       // how many times overlay shown while blocked (= you tried to open)
    val emergencyUnlockCount: Int = 0 // puzzle-solved unlocks
)

@Serializable
data class StatsDay(
    val date: String,                                // yyyy-MM-dd
    val perApp: Map<String, AppDayStats> = emptyMap()
) {
    fun totalUsedSeconds(): Int = perApp.values.sumOf { it.usedSeconds }
    fun totalBlocks(): Int = perApp.values.sumOf { it.blockCount }
    fun totalAttempts(): Int = perApp.values.sumOf { it.attemptCount }
    fun totalEmergencyUnlocks(): Int = perApp.values.sumOf { it.emergencyUnlockCount }
}

@Serializable
data class StatsHistory(
    val days: List<StatsDay> = emptyList()
) {
    fun day(date: String): StatsDay? = days.firstOrNull { it.date == date }
}
