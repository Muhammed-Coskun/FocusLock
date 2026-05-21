package com.personal.focuslock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personal.focuslock.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focuslock")

class BlockerRepository private constructor(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val states = stringPreferencesKey("app_states_json")
        val groups = stringPreferencesKey("groups_json")
    }

    val enabledFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.enabled] ?: true }

    val statesFlow: Flow<List<AppState>> = context.dataStore.data.map { prefs ->
        prefs[Keys.states]?.let { decodeStates(it) } ?: defaultStates()
    }

    val groupsFlow: Flow<List<MonitorGroup>> = context.dataStore.data.map { prefs ->
        prefs[Keys.groups]?.let { decodeGroups(it) } ?: emptyList()
    }

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.enabled] = value }
    }

    suspend fun snapshot(): List<AppState> = statesFlow.first()
    suspend fun snapshotGroups(): List<MonitorGroup> = groupsFlow.first()

    suspend fun addMonitored(pkg: String) = mutateStates { list ->
        if (list.any { it.packageName == pkg }) list else list + AppState(pkg)
    }

    /**
     * Removes a monitored entry. If it was a group member, also removes its membership and
     * dissolves the group if the entry was the last (or only) remaining member that made
     * it a "group" (a group needs ≥2 members to be meaningful — a 1-member group is auto-dissolved).
     */
    suspend fun removeMonitored(pkg: String) = replaceAllAndGroups { states, groups ->
        val target = states.firstOrNull { it.packageName == pkg }
        val newStates = states.filterNot { it.packageName == pkg }
        val (cleanedStates, cleanedGroups) = collapseEmptyGroups(newStates, groups, target?.groupId)
        Pair(cleanedStates, cleanedGroups)
    }

    /** Resets a single AppState. Service uses this for cycle expiry; puzzle uses it for unlock. */
    suspend fun replaceState(pkg: String, update: (AppState) -> AppState) = mutateStates { list ->
        list.map { if (it.packageName == pkg) update(it) else it }
    }

    suspend fun replaceGroup(groupId: String, update: (MonitorGroup) -> MonitorGroup) =
        mutateGroups { list -> list.map { if (it.id == groupId) update(it) else it } }

    /** Replace ONLY the states list (legacy callers). */
    suspend fun replaceAll(updater: (List<AppState>) -> List<AppState>) = mutateStates(updater)

    /** Atomic read+write of BOTH lists in a single Preferences edit. */
    suspend fun replaceAllAndGroups(
        updater: (List<AppState>, List<MonitorGroup>) -> Pair<List<AppState>, List<MonitorGroup>>
    ) {
        context.dataStore.edit { prefs ->
            val curStates = prefs[Keys.states]?.let { decodeStates(it) } ?: defaultStates()
            val curGroups = prefs[Keys.groups]?.let { decodeGroups(it) } ?: emptyList()
            val (newStates, newGroups) = updater(curStates, curGroups)
            prefs[Keys.states] = encodeStates(newStates)
            prefs[Keys.groups] = encodeGroups(newGroups)
        }
    }

    /**
     * Toggles whether [memberPkg] shares its timer with [withPkg].
     *
     * - If neither has a group, a new group is created with both as members.
     * - If withPkg already belongs to a group, memberPkg joins that group.
     * - If memberPkg is currently grouped (in any group) and `share == false`, memberPkg
     *   leaves its group. If that group then has <2 members, it is dissolved.
     *
     * `now` is used to refuse mutations while either side is blocked — the caller is
     * expected to gate this with the same predicate, but we double-check at the data layer
     * so a stale UI can't bypass the lock.
     */
    suspend fun setShareTimer(memberPkg: String, withPkg: String, share: Boolean, now: Long) {
        replaceAllAndGroups { states, groups ->
            val member = states.firstOrNull { it.packageName == memberPkg }
                ?: return@replaceAllAndGroups Pair(states, groups)
            val target = states.firstOrNull { it.packageName == withPkg }
                ?: return@replaceAllAndGroups Pair(states, groups)

            // Block-time guard: cannot edit grouping while either side is locked.
            if (isLockedNow(member, target, groups, now)) {
                return@replaceAllAndGroups Pair(states, groups)
            }

            if (share) {
                // Determine target group: prefer target's existing group, then member's, else new.
                val groupId = target.groupId ?: member.groupId ?: UUID.randomUUID().toString()
                val groupExists = groups.any { it.id == groupId }
                val newGroups = if (groupExists) groups else groups + MonitorGroup(id = groupId)

                val newStates = states.map { s ->
                    when (s.packageName) {
                        memberPkg, withPkg -> s.copy(groupId = groupId)
                        else -> s
                    }
                }
                // If member was in a different group before, that group might now be orphaned.
                collapseEmptyGroups(newStates, newGroups, member.groupId.takeIf { it != groupId })
            } else {
                // Pull memberPkg out of any shared group with withPkg.
                val sharedGroupId = member.groupId.takeIf { it != null && it == target.groupId }
                val newStates = states.map { s ->
                    if (s.packageName == memberPkg) s.copy(groupId = null) else s
                }
                collapseEmptyGroups(newStates, groups, sharedGroupId)
            }
        }
    }

    private fun isLockedNow(
        a: AppState,
        b: AppState,
        groups: List<MonitorGroup>,
        now: Long
    ): Boolean {
        val aLocked = effectiveBlockUntilMs(a, groups) > now
        val bLocked = effectiveBlockUntilMs(b, groups) > now
        return aLocked || bLocked
    }

    private fun effectiveBlockUntilMs(state: AppState, groups: List<MonitorGroup>): Long {
        val gid = state.groupId ?: return state.blockUntilMs
        val g = groups.firstOrNull { it.id == gid } ?: return state.blockUntilMs
        return g.blockUntilMs
    }

    /**
     * Drops any group whose member count fell below 2. The lone remaining member (if any)
     * gets its groupId cleared so it appears as a solo entry again.
     *
     * [touchedGroupId] limits the cleanup to a specific group when known — a small efficiency
     * but mostly to avoid surprising side-effects on unrelated groups.
     */
    private fun collapseEmptyGroups(
        states: List<AppState>,
        groups: List<MonitorGroup>,
        touchedGroupId: String?
    ): Pair<List<AppState>, List<MonitorGroup>> {
        if (touchedGroupId == null) return Pair(states, groups)
        val memberCount = states.count { it.groupId == touchedGroupId }
        return if (memberCount >= 2) {
            Pair(states, groups)
        } else {
            val newStates = states.map { s ->
                if (s.groupId == touchedGroupId) s.copy(groupId = null) else s
            }
            val newGroups = groups.filterNot { it.id == touchedGroupId }
            Pair(newStates, newGroups)
        }
    }

    private suspend fun mutateStates(block: (List<AppState>) -> List<AppState>) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.states]?.let { decodeStates(it) } ?: defaultStates()
            prefs[Keys.states] = encodeStates(block(current))
        }
    }

    private suspend fun mutateGroups(block: (List<MonitorGroup>) -> List<MonitorGroup>) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.groups]?.let { decodeGroups(it) } ?: emptyList()
            prefs[Keys.groups] = encodeGroups(block(current))
        }
    }

    private fun defaultStates(): List<AppState> =
        listOf(AppState(Constants.DEFAULT_MONITORED_PACKAGE))

    private fun encodeStates(list: List<AppState>): String =
        json.encodeToString(ListSerializer(AppState.serializer()), list)

    private fun decodeStates(text: String): List<AppState> =
        runCatching { json.decodeFromString(ListSerializer(AppState.serializer()), text) }
            .getOrDefault(emptyList())

    private fun encodeGroups(list: List<MonitorGroup>): String =
        json.encodeToString(ListSerializer(MonitorGroup.serializer()), list)

    private fun decodeGroups(text: String): List<MonitorGroup> =
        runCatching { json.decodeFromString(ListSerializer(MonitorGroup.serializer()), text) }
            .getOrDefault(emptyList())

    companion object {
        @Volatile private var instance: BlockerRepository? = null
        fun get(context: Context): BlockerRepository =
            instance ?: synchronized(this) {
                instance ?: BlockerRepository(context.applicationContext).also { instance = it }
            }
    }
}
