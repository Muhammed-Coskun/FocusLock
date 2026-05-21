package com.personal.focuslock.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-process bus between the AccessibilityService (URL detector) and the BlockerService.
 *
 * The AccessibilityService writes the currently active browser package + URL host whenever
 * the user navigates inside a known browser; the BlockerService reads it once per tick.
 *
 * Both services run in the same app process, so a Kotlin singleton with a StateFlow is enough.
 */
object WebMonitor {

    data class State(val browserPackage: String? = null, val host: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun update(browserPackage: String?, host: String?) {
        val normalizedHost = host?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        _state.value = State(browserPackage, normalizedHost)
    }

    /** Synthetic AppState ID for a web entry. */
    fun keyFor(host: String): String = WEB_PREFIX + host

    fun isWebKey(key: String): Boolean = key.startsWith(WEB_PREFIX)
    fun hostOf(key: String): String = key.removePrefix(WEB_PREFIX)

    private const val WEB_PREFIX = "web:"

    /**
     * Browser packages whose URL bar we know how to read. If the OS foreground is one of these,
     * the BlockerService treats `WebMonitor.state.host` as the effective foreground identity.
     */
    val KNOWN_BROWSERS: Set<String> = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.sec.android.app.sbrowser",          // Samsung Internet
        "com.sec.android.app.sbrowser.beta",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "com.microsoft.emmx",                     // Edge
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser"
    )
}
