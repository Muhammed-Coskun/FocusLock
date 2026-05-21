package com.personal.focuslock.data

import kotlinx.serialization.Serializable

@Serializable
data class AppState(
    val packageName: String,
    val usedMs: Long = 0,
    val blockUntilMs: Long = 0,
    val cycleStartedMs: Long = 0,
    /**
     * If non-null, this entry shares its 15/2h timer with the [MonitorGroup] of this id.
     * In that case [usedMs]/[blockUntilMs]/[cycleStartedMs] are ignored — the group's
     * fields are authoritative.
     */
    val groupId: String? = null
) {
    fun isBlocked(now: Long): Boolean = blockUntilMs > now
    fun remainingBlockMs(now: Long): Long = (blockUntilMs - now).coerceAtLeast(0)
}

@Serializable
data class MonitorGroup(
    val id: String,
    val name: String = "",
    val usedMs: Long = 0,
    val blockUntilMs: Long = 0,
    val cycleStartedMs: Long = 0
) {
    fun isBlocked(now: Long): Boolean = blockUntilMs > now
    fun remainingBlockMs(now: Long): Long = (blockUntilMs - now).coerceAtLeast(0)
}
