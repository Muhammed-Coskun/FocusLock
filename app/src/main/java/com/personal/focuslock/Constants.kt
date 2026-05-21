package com.personal.focuslock

object Constants {
    const val USAGE_LIMIT_MS: Long = 15L * 60 * 1000
    const val BLOCK_DURATION_MS: Long = 2L * 60 * 60 * 1000
    const val POLL_INTERVAL_MS: Long = 1000L

    // 3 minutes before block triggers, show a heads-up warning toast.
    const val WARN_BEFORE_BLOCK_MS: Long = 3L * 60 * 1000
    const val WARN_THRESHOLD_MS: Long = USAGE_LIMIT_MS - WARN_BEFORE_BLOCK_MS

    const val DEFAULT_MONITORED_PACKAGE = "com.instagram.android"

    const val NOTIFICATION_CHANNEL_ID = "focuslock_service"
    const val NOTIFICATION_ID = 1001

    // Keep at most this many days of stats history.
    const val STATS_HISTORY_DAYS = 60
}
