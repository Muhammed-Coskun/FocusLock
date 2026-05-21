package com.personal.focuslock.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.personal.focuslock.data.WebMonitor

/**
 * Reads the URL bar of known browsers via accessibility tree traversal and pushes the
 * currently visible host into [WebMonitor]. The BlockerService consults that on each tick
 * to decide whether a "web:host" timer should be ticking.
 *
 * No browser data is exfiltrated, written to disk, or used outside of WebMonitor's in-memory
 * StateFlow. Only the host (e.g. "twitter.com") is kept — never the full URL.
 */
class BrowserAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in WebMonitor.KNOWN_BROWSERS) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        val urlText = findUrl(root, pkg)
        val host = urlText?.let { extractHost(it) }
        WebMonitor.update(pkg, host)
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onServiceConnected() {
        super.onServiceConnected()
        WebMonitor.update(null, null)
    }

    private fun findUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        // First try the well-known URL bar resource IDs for this browser.
        for (resId in URL_BAR_RES_IDS_BY_PKG[pkg].orEmpty()) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(resId) }
                .getOrNull().orEmpty()
            for (node in nodes) {
                val text = node.text?.toString()
                if (!text.isNullOrBlank()) return text
            }
        }
        // Fallback: any node whose text looks like a URL/host.
        return findUrlInTree(root, depth = 0)
    }

    private fun findUrlInTree(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > MAX_DEPTH) return null
        val text = node.text?.toString()
        if (!text.isNullOrBlank() && looksLikeHost(text)) return text
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val found = findUrlInTree(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun extractHost(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // Some browsers display just the host; some include the scheme; some show the full URL.
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        return runCatching {
            val host = java.net.URI(withScheme).host
            host?.lowercase()?.removePrefix("www.")
        }.getOrNull()
    }

    private fun looksLikeHost(text: String): Boolean {
        // Quick heuristic: contains a dot, no spaces, has at least 2 letters of TLD.
        if (text.contains(' ') || text.length > 200) return false
        val cleaned = text.substringBefore('/').substringAfter("://")
        return HOST_REGEX.matches(cleaned)
    }

    companion object {
        private const val MAX_DEPTH = 12
        private val HOST_REGEX = Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*$")

        // Known URL-bar resource IDs per browser. Multiple per browser because layout changes
        // across versions. Order matters — most-specific first.
        private val URL_BAR_RES_IDS_BY_PKG: Map<String, List<String>> = mapOf(
            "com.android.chrome" to listOf(
                "com.android.chrome:id/url_bar"
            ),
            "com.chrome.beta" to listOf("com.chrome.beta:id/url_bar"),
            "com.chrome.dev" to listOf("com.chrome.dev:id/url_bar"),
            "com.chrome.canary" to listOf("com.chrome.canary:id/url_bar"),
            "com.sec.android.app.sbrowser" to listOf(
                "com.sec.android.app.sbrowser:id/location_bar_edit_text",
                "com.sec.android.app.sbrowser:id/url_bar_title"
            ),
            "com.sec.android.app.sbrowser.beta" to listOf(
                "com.sec.android.app.sbrowser.beta:id/location_bar_edit_text"
            ),
            "org.mozilla.firefox" to listOf(
                "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
                "org.mozilla.firefox:id/url_bar_title"
            ),
            "org.mozilla.firefox_beta" to listOf(
                "org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view"
            ),
            "org.mozilla.fenix" to listOf(
                "org.mozilla.fenix:id/mozac_browser_toolbar_url_view"
            ),
            "org.mozilla.focus" to listOf(
                "org.mozilla.focus:id/mozac_browser_toolbar_url_view"
            ),
            "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
            "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
            "com.opera.browser" to listOf(
                "com.opera.browser:id/url_field",
                "com.opera.browser:id/edit_url_field"
            ),
            "com.opera.mini.native" to listOf("com.opera.mini.native:id/url_field"),
            "com.duckduckgo.mobile.android" to listOf(
                "com.duckduckgo.mobile.android:id/omnibarTextInput"
            ),
            "com.vivaldi.browser" to listOf("com.vivaldi.browser:id/url_bar")
        )
    }
}
