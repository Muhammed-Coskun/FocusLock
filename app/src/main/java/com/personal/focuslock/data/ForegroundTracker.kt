package com.personal.focuslock.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager

class ForegroundTracker(private val usm: UsageStatsManager) {

    private var lastPollTs: Long = 0L
    private var currentFg: String? = null

    fun currentForeground(): String? {
        val now = System.currentTimeMillis()
        val from = if (lastPollTs == 0L) now - 24L * 60 * 60 * 1000 else lastPollTs - 1500
        val events = usm.queryEvents(from, now + 1)
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.MOVE_TO_FOREGROUND -> currentFg = ev.packageName
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (ev.packageName == currentFg) currentFg = null
                }
            }
        }
        lastPollTs = now
        return currentFg
    }
}
