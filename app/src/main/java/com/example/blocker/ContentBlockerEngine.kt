package com.example.blocker

import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.example.data.model.BlockedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

private const val TAG = "ContentBlocker"

/**
 * Genuine Content-Blocking Engine modeled after Brave Shields principles.
 * Intercepts matching advertising requests, telemetry beacons, and tracking scripts
 * before network transmission.
 */
class ContentBlockerEngine {

    private val _isShieldEnabled = MutableStateFlow(true)
    val isShieldEnabled: StateFlow<Boolean> = _isShieldEnabled.asStateFlow()

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    private val _blockedItems = MutableStateFlow<List<BlockedItem>>(emptyList())
    val blockedItems: StateFlow<List<BlockedItem>> = _blockedItems.asStateFlow()

    fun toggleShield(enabled: Boolean) {
        _isShieldEnabled.value = enabled
    }

    fun clearStats() {
        _blockedCount.value = 0
        _blockedItems.value = emptyList()
    }

    /**
     * Inspects incoming web resource request.
     * Returns an empty response if the request matches ad or tracker rules, canceling the connection.
     * Returns null if allowed.
     */
    fun shouldBlock(request: WebResourceRequest?): WebResourceResponse? {
        if (!_isShieldEnabled.value || request == null) return null

        val uri: Uri = request.url ?: return null
        val host: String = uri.host?.lowercase() ?: return null

        // Safe domains allowlist
        if (FilterLists.SAFE_HOST_ALLOWLIST.contains(host)) {
            return null
        }

        var blockCategory: String? = null

        // 1. Check Ad domains
        if (isDomainMatched(host, FilterLists.AD_DOMAINS)) {
            blockCategory = "Ad Network"
        }
        // 2. Check Tracker domains
        else if (isDomainMatched(host, FilterLists.TRACKER_DOMAINS)) {
            blockCategory = "Tracker / Analytics"
        }
        // 3. Check blocked path patterns
        else {
            val path = uri.path ?: ""
            val query = uri.query ?: ""
            val fullPath = "$path?$query"
            for (sig in FilterLists.BLOCKED_PATH_SIGNATURES) {
                if (fullPath.contains(sig, ignoreCase = true)) {
                    blockCategory = "Telemetry Beacon"
                    break
                }
            }
        }

        if (blockCategory != null) {
            recordBlocked(uri.toString(), host, blockCategory)
            // Return empty 200 OK or empty input stream to cancel the resource fetch cleanly
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                ByteArrayInputStream(ByteArray(0))
            )
        }

        return null
    }

    private fun isDomainMatched(host: String, domainSet: Set<String>): Boolean {
        if (domainSet.contains(host)) return true

        // Check subdomain matching, e.g. "ad.doubleclick.net" ends with ".doubleclick.net"
        for (domain in domainSet) {
            if (host.endsWith(".$domain")) {
                return true
            }
        }
        return false
    }

    private fun recordBlocked(url: String, domain: String, category: String) {
        _blockedCount.value += 1
        val item = BlockedItem(url = url, domain = domain, category = category)
        val current = _blockedItems.value.toMutableList()
        // Keep up to 100 recent entries in memory
        if (current.size >= 100) {
            current.removeAt(current.size - 1)
        }
        current.add(0, item)
        _blockedItems.value = current
        Log.d(TAG, "Blocked [${category}]: $domain")
    }

    /**
     * Generates JavaScript code to inject cosmetic ad element hiding
     */
    fun getCosmeticScript(): String {
        if (!_isShieldEnabled.value) return ""
        val css = FilterLists.COSMETIC_CSS.replace("\n", " ").trim()
        return """
            (function() {
                var style = document.getElementById('nk-cosmetic-shield');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'nk-cosmetic-shield';
                    style.innerHTML = '$css';
                    (document.head || document.documentElement).appendChild(style);
                }
            })();
        """.trimIndent()
    }
}
