package com.example.ui.viewmodels

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import com.example.blocker.ContentBlockerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder

enum class GoogleCategory(val label: String) {
    ALL("Web"),
    IMAGES("Images"),
    VIDEOS("Videos"),
    NEWS("News"),
    MAPS("Maps")
}

class BrowserViewModel(
    val blockerEngine: ContentBlockerEngine = ContentBlockerEngine()
) : ViewModel() {

    val defaultHomeUrl = "https://www.google.com"

    private val _currentUrl = MutableStateFlow(defaultHomeUrl)
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _pageTitle = MutableStateFlow("Google")
    val pageTitle: StateFlow<String> = _pageTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0)
    val loadingProgress: StateFlow<Int> = _loadingProgress.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError: StateFlow<Boolean> = _hasError.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow(GoogleCategory.ALL)
    val selectedCategory: StateFlow<GoogleCategory> = _selectedCategory.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun onPageStarted(url: String) {
        _currentUrl.value = url
        _isLoading.value = true
        _hasError.value = false
        _errorMessage.value = null
    }

    fun onPageFinished(url: String, title: String?, canBack: Boolean, canForward: Boolean) {
        _currentUrl.value = url
        _pageTitle.value = title ?: "Google Search"
        _isLoading.value = false
        _loadingProgress.value = 100
        _canGoBack.value = canBack
        _canGoForward.value = canForward
    }

    fun onProgressChanged(newProgress: Int) {
        _loadingProgress.value = newProgress
        _isLoading.value = newProgress < 100
    }

    fun onPageError(errorCode: Int, description: String, failingUrl: String) {
        _isLoading.value = false
        _hasError.value = true
        _errorMessage.value = "$description (Error $errorCode)"
    }

    /**
     * Executes Google Search or navigates directly if input looks like a valid URL
     */
    fun submitSearchOrUrl(input: String, category: GoogleCategory = _selectedCategory.value): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return defaultHomeUrl

        _selectedCategory.value = category

        // If it starts with http/https or contains domain-like pattern without spaces
        val targetUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (!trimmed.contains(" ") && (trimmed.contains(".com") || trimmed.contains(".org") || trimmed.contains(".net") || trimmed.contains(".io") || trimmed.contains(".edu") || trimmed.contains(".in"))) {
            "https://$trimmed"
        } else {
            buildGoogleSearchUrl(trimmed, category)
        }

        _searchQuery.value = trimmed
        _currentUrl.value = targetUrl
        return targetUrl
    }

    fun selectCategory(category: GoogleCategory): String {
        _selectedCategory.value = category
        val query = _searchQuery.value.ifBlank { "Google" }
        val newUrl = buildGoogleSearchUrl(query, category)
        _currentUrl.value = newUrl
        return newUrl
    }

    private fun buildGoogleSearchUrl(query: String, category: GoogleCategory): String {
        val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrDefault(query)
        return when (category) {
            GoogleCategory.ALL -> "https://www.google.com/search?q=$encoded"
            GoogleCategory.IMAGES -> "https://www.google.com/search?q=$encoded&tbm=isch"
            GoogleCategory.VIDEOS -> "https://www.google.com/search?q=$encoded&tbm=vid"
            GoogleCategory.NEWS -> "https://www.google.com/search?q=$encoded&tbm=nws"
            GoogleCategory.MAPS -> "https://www.google.com/maps/search/$encoded"
        }
    }

    /**
     * Complete privacy wipe: clears web storage, cookies, and cache in memory
     */
    fun clearSession(webView: WebView?) {
        webView?.let {
            it.clearCache(true)
            it.clearHistory()
            it.clearFormData()
            it.clearSslPreferences()
        }
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        blockerEngine.clearStats()
        _searchQuery.value = ""
        _selectedCategory.value = GoogleCategory.ALL
        _hasError.value = false
        _currentUrl.value = defaultHomeUrl
    }
}
