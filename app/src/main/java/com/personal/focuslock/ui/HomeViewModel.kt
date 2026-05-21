package com.personal.focuslock.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.focuslock.data.AppState
import com.personal.focuslock.data.BlockerRepository
import com.personal.focuslock.data.MonitorGroup
import com.personal.focuslock.data.WebMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

data class AppRow(
    val state: AppState,
    val label: String,
    val iconBitmap: android.graphics.Bitmap?
)

/** A solo monitored entry — uses its own state. */
data class SoloRow(val app: AppRow)

/** A group of monitored entries that share a single timer. */
data class GroupRow(
    val group: MonitorGroup,
    val members: List<AppRow>
)

sealed class HomeRow {
    abstract val key: String
    data class Solo(val row: SoloRow) : HomeRow() {
        override val key: String = "solo:${row.app.state.packageName}"
    }
    data class Group(val row: GroupRow) : HomeRow() {
        override val key: String = "group:${row.group.id}"
    }
}

data class HomeUi(
    val enabled: Boolean = true,
    val rows: List<HomeRow> = emptyList(),
    val now: Long = System.currentTimeMillis()
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BlockerRepository.get(app)
    private val tick = MutableStateFlow(System.currentTimeMillis())
    private val infoCache = Collections.synchronizedMap(HashMap<String, InstalledApp?>())

    init {
        viewModelScope.launch {
            while (true) {
                tick.value = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    val ui: StateFlow<HomeUi> = combine(
        repo.enabledFlow,
        repo.statesFlow,
        repo.groupsFlow,
        tick
    ) { enabled, states, groups, now ->
        val appRows = states.map { s -> s to buildAppRow(s) }

        val groupedById = appRows
            .filter { it.first.groupId != null }
            .groupBy { it.first.groupId!! }

        val homeRows = mutableListOf<HomeRow>()
        // Solos first
        for ((s, row) in appRows) {
            if (s.groupId == null) {
                homeRows += HomeRow.Solo(SoloRow(row))
            }
        }
        // Groups (only if the group record actually exists)
        for (g in groups) {
            val membersWithStates = groupedById[g.id].orEmpty()
            if (membersWithStates.size < 2) continue
            homeRows += HomeRow.Group(GroupRow(group = g, members = membersWithStates.map { it.second }))
        }

        HomeUi(enabled = enabled, rows = homeRows, now = now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUi())

    private suspend fun buildAppRow(s: AppState): AppRow {
        val key = s.packageName
        return if (WebMonitor.isWebKey(key)) {
            AppRow(s, WebMonitor.hostOf(key), null)
        } else {
            val cached = synchronized(infoCache) {
                if (infoCache.containsKey(key)) infoCache[key] else null
            }
            val info = cached ?: runCatching {
                withContext(Dispatchers.IO) { AppInfoLoader.load(getApplication(), key) }
            }.getOrNull().also {
                synchronized(infoCache) { infoCache[key] = it }
            }
            AppRow(s, info?.label ?: key, info?.icon)
        }
    }

    fun setEnabled(v: Boolean) = viewModelScope.launch { repo.setEnabled(v) }
    fun addApp(pkg: String) = viewModelScope.launch { repo.addMonitored(pkg) }
    fun addWebHost(host: String) = viewModelScope.launch {
        val normalized = host.trim().lowercase().removePrefix("http://").removePrefix("https://")
            .removePrefix("www.").substringBefore('/').substringBefore('?')
        if (normalized.isNotEmpty() && normalized.contains('.')) {
            repo.addMonitored(WebMonitor.keyFor(normalized))
        }
    }
    fun removeApp(pkg: String) = viewModelScope.launch {
        repo.removeMonitored(pkg)
        synchronized(infoCache) { infoCache.remove(pkg) }
    }
    fun resetApp(pkg: String) = viewModelScope.launch {
        repo.replaceState(pkg) { it.copy(usedMs = 0, blockUntilMs = 0, cycleStartedMs = 0) }
    }
    fun setShareTimer(memberPkg: String, withPkg: String, share: Boolean) = viewModelScope.launch {
        repo.setShareTimer(memberPkg, withPkg, share, System.currentTimeMillis())
    }
}
