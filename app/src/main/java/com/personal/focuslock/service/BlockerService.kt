package com.personal.focuslock.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.personal.focuslock.Constants
import com.personal.focuslock.MainActivity
import com.personal.focuslock.R
import com.personal.focuslock.data.BlockerRepository
import com.personal.focuslock.data.ForegroundTracker
import com.personal.focuslock.data.StatsRepository
import com.personal.focuslock.data.WebMonitor
import com.personal.focuslock.ui.BlockOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BlockerService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var repo: BlockerRepository
    private lateinit var stats: StatsRepository
    private lateinit var tracker: ForegroundTracker
    private val mainHandler = Handler(Looper.getMainLooper())
    private val labelCache = HashMap<String, String>()
    private var lastTickMs: Long = 0L
    private var lastOverlayPackage: String? = null
    private var lastOverlayAtMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repo = BlockerRepository.get(this)
        stats = StatsRepository.get(this)
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        tracker = ForegroundTracker(usm)
        ensureChannel()
        startForegroundCompat()
        scheduleWatchdog()
        scope.launch { loop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scheduleWatchdog()
        return START_STICKY
    }

    /**
     * Called when the user swipes the app away from the recents tray. Default behavior would
     * leave the service running but the OS becomes more likely to reap it, so we proactively
     * schedule a short-delay restart via AlarmManager.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleRestart(delayMs = 500L)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // The service is going down (Samsung "Stop", low memory, etc.) — re-arm an alarm
        // that will bring it back unless the user force-stopped the whole app process.
        scheduleRestart(delayMs = 2_000L)
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Schedules a one-shot foreground service start in [delayMs]. Survives our own death
     * because the AlarmManager queue is per-package, not per-process. (A real force-stop
     * via Settings → Apps wipes the queue and the user has to open the app again.)
     */
    private fun scheduleRestart(delayMs: Long) {
        val am = getSystemService(AlarmManager::class.java) ?: return
        val pending = restartPendingIntent() ?: return
        am.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + delayMs,
            pending
        )
    }

    /**
     * Periodic inexact alarm (~every 10 min) that ensures the service comes back up if
     * Doze, the OOM killer, or Samsung's Active-Apps stop removed it. Inexact = no special
     * permission needed on API 31+.
     */
    private fun scheduleWatchdog() {
        val am = getSystemService(AlarmManager::class.java) ?: return
        val pending = restartPendingIntent() ?: return
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS,
            WATCHDOG_INTERVAL_MS,
            pending
        )
    }

    private fun restartPendingIntent(): PendingIntent? {
        val intent = Intent(this, BlockerService::class.java)
        // FLAG_IMMUTABLE required on API 31+. UPDATE_CURRENT replaces any existing pending intent.
        return PendingIntent.getForegroundService(
            this,
            RESTART_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private suspend fun loop() {
        lastTickMs = SystemClock.elapsedRealtime()
        while (true) {
            val nowReal = SystemClock.elapsedRealtime()
            val now = System.currentTimeMillis()
            val rawDelta = nowReal - lastTickMs
            lastTickMs = nowReal
            val delta = if (rawDelta in 0..3_000) rawDelta else 0L

            val enabled = runCatching { repo.enabledFlow.first() }.getOrDefault(true)
            val osFg = runCatching { tracker.currentForeground() }.getOrNull()
            val fg: String? = run {
                if (osFg != null && osFg in WebMonitor.KNOWN_BROWSERS) {
                    val host = WebMonitor.state.value.host
                    if (host != null) WebMonitor.keyFor(host) else osFg
                } else osFg
            }

            var effectiveBlockUntilMs = 0L
            var crossedWarning = false
            var crossedBlock = false

            runCatching {
                repo.replaceAllAndGroups { states, groups ->
                    // 1) Expire any state whose block has elapsed — but only solo entries.
                    //    Grouped entries don't carry their own block, so leave them untouched.
                    val expiredStates = states.map { s ->
                        if (s.groupId == null && s.blockUntilMs in 1..now) {
                            s.copy(usedMs = 0, blockUntilMs = 0, cycleStartedMs = 0)
                        } else s
                    }
                    // 2) Expire any group whose block has elapsed.
                    val expiredGroups = groups.map { g ->
                        if (g.blockUntilMs in 1..now) {
                            g.copy(usedMs = 0, blockUntilMs = 0, cycleStartedMs = 0)
                        } else g
                    }

                    val fgState = expiredStates.firstOrNull { it.packageName == fg }
                    if (fgState == null) {
                        return@replaceAllAndGroups Pair(expiredStates, expiredGroups)
                    }

                    val groupId = fgState.groupId
                    if (groupId == null) {
                        // Solo entry — tick its own counter.
                        val newStates = expiredStates.map { s ->
                            if (s.packageName != fg) return@map s
                            if (!enabled || delta <= 0L || s.blockUntilMs > now) {
                                effectiveBlockUntilMs = s.blockUntilMs
                                return@map s
                            }
                            val cycleStart = if (s.cycleStartedMs == 0L) now else s.cycleStartedMs
                            val newUsed = (s.usedMs + delta).coerceAtMost(Constants.USAGE_LIMIT_MS)
                            if (s.usedMs < Constants.WARN_THRESHOLD_MS &&
                                newUsed >= Constants.WARN_THRESHOLD_MS &&
                                newUsed < Constants.USAGE_LIMIT_MS
                            ) crossedWarning = true
                            var ns = s.copy(usedMs = newUsed, cycleStartedMs = cycleStart)
                            if (newUsed >= Constants.USAGE_LIMIT_MS) {
                                ns = ns.copy(blockUntilMs = now + Constants.BLOCK_DURATION_MS)
                                if (s.blockUntilMs <= now) crossedBlock = true
                            }
                            effectiveBlockUntilMs = ns.blockUntilMs
                            ns
                        }
                        Pair(newStates, expiredGroups)
                    } else {
                        // Grouped — tick the group's shared counter.
                        val newGroups = expiredGroups.map { g ->
                            if (g.id != groupId) return@map g
                            if (!enabled || delta <= 0L || g.blockUntilMs > now) {
                                effectiveBlockUntilMs = g.blockUntilMs
                                return@map g
                            }
                            val cycleStart = if (g.cycleStartedMs == 0L) now else g.cycleStartedMs
                            val newUsed = (g.usedMs + delta).coerceAtMost(Constants.USAGE_LIMIT_MS)
                            if (g.usedMs < Constants.WARN_THRESHOLD_MS &&
                                newUsed >= Constants.WARN_THRESHOLD_MS &&
                                newUsed < Constants.USAGE_LIMIT_MS
                            ) crossedWarning = true
                            var ng = g.copy(usedMs = newUsed, cycleStartedMs = cycleStart)
                            if (newUsed >= Constants.USAGE_LIMIT_MS) {
                                ng = ng.copy(blockUntilMs = now + Constants.BLOCK_DURATION_MS)
                                if (g.blockUntilMs <= now) crossedBlock = true
                            }
                            effectiveBlockUntilMs = ng.blockUntilMs
                            ng
                        }
                        Pair(expiredStates, newGroups)
                    }
                }
            }

            // Stats: only when not blocked (the overlay would be foreground otherwise).
            if (delta > 0L && fg != null && enabled && effectiveBlockUntilMs <= now) {
                stats.recordUsage(fg, delta)
            }
            if (crossedBlock && fg != null) {
                scope.launch { runCatching { stats.recordBlock(fg) } }
            }
            if (crossedWarning && fg != null) {
                showWarningToast(fg)
            }

            if (enabled && fg != null && effectiveBlockUntilMs > now) {
                maybeLaunchOverlay(fg, effectiveBlockUntilMs)
            } else if (fg == null) {
                lastOverlayPackage = null
            }

            delay(Constants.POLL_INTERVAL_MS)
        }
    }

    private fun maybeLaunchOverlay(pkg: String, blockUntilMs: Long) {
        val now = System.currentTimeMillis()
        if (pkg == lastOverlayPackage && now - lastOverlayAtMs < 3000) return
        scope.launch { runCatching { stats.recordAttempt(pkg) } }
        lastOverlayPackage = pkg
        lastOverlayAtMs = now

        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(home) }

        val overlay = Intent(this, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(BlockOverlayActivity.EXTRA_PACKAGE, pkg)
            putExtra(BlockOverlayActivity.EXTRA_BLOCK_UNTIL, blockUntilMs)
        }
        runCatching { startActivity(overlay) }
    }

    private fun showWarningToast(pkg: String) {
        val label = labelCache.getOrPut(pkg) { loadAppLabel(pkg) }
        mainHandler.post {
            Toast.makeText(
                this,
                "Noch 3 Min $label, dann Sperre.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun loadAppLabel(pkg: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val existing = nm.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID)
        // Recreate the channel if it exists with the wrong importance (upgrade from older builds).
        if (existing != null && existing.importance != NotificationManager.IMPORTANCE_MIN) {
            nm.deleteNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID)
        }
        if (existing == null || existing.importance != NotificationManager.IMPORTANCE_MIN) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_MIN // sinks the notification to the bottom of the shade
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun startForegroundCompat() {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setContentIntent(openApp)
            // PRIORITY_MIN + VISIBILITY_SECRET + no badge = as invisible as Android allows
            // for a foreground service notification (which must, by policy, exist).
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false)
            .setSilent(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Constants.NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Constants.NOTIFICATION_ID, notif)
        }
    }

    companion object {
        private const val RESTART_REQUEST_CODE = 9001
        private const val WATCHDOG_INTERVAL_MS = 10L * 60 * 1000  // 10 minutes

        fun start(context: Context) {
            val intent = Intent(context, BlockerService::class.java)
            context.startForegroundService(intent)
        }
    }
}
