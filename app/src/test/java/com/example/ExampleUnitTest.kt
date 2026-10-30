package com.example

import com.example.blocker.ContentBlockerEngine
import com.example.blocker.FilterLists
import com.example.ui.viewmodels.BrowserViewModel
import com.example.ui.viewmodels.GoogleCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun filterLists_containsCoreAdAndTrackerDomains() {
        assertTrue(FilterLists.AD_DOMAINS.contains("doubleclick.net"))
        assertTrue(FilterLists.AD_DOMAINS.contains("googlesyndication.com"))
        assertTrue(FilterLists.AD_DOMAINS.contains("criteo.com"))
        assertTrue(FilterLists.TRACKER_DOMAINS.contains("google-analytics.com"))
        assertTrue(FilterLists.TRACKER_DOMAINS.contains("hotjar.com"))
        assertTrue(FilterLists.SAFE_HOST_ALLOWLIST.contains("google.com"))
        assertTrue(FilterLists.SAFE_HOST_ALLOWLIST.contains("wikipedia.org"))
    }

    @Test
    fun contentBlockerEngine_initialState() {
        val engine = ContentBlockerEngine()
        assertTrue(engine.isShieldEnabled.value)
        assertEquals(0, engine.blockedCount.value)
        assertEquals(0, engine.blockedItems.value.size)
    }

    @Test
    fun contentBlockerEngine_toggleShield() {
        val engine = ContentBlockerEngine()
        engine.toggleShield(false)
        assertFalse(engine.isShieldEnabled.value)

        engine.toggleShield(true)
        assertTrue(engine.isShieldEnabled.value)
    }

    @Test
    fun contentBlockerEngine_cosmeticScriptGenerated() {
        val engine = ContentBlockerEngine()
        val script = engine.getCosmeticScript()
        assertTrue(script.contains("nk-cosmetic-shield"))
        assertTrue(script.contains("display: none !important"))

        engine.toggleShield(false)
        assertEquals("", engine.getCosmeticScript())
    }

    @Test
    fun browserViewModel_buildsCorrectGoogleCategoryUrls() {
        val vm = BrowserViewModel()
        val webUrl = vm.submitSearchOrUrl("Kotlin compose", GoogleCategory.ALL)
        assertTrue(webUrl.startsWith("https://www.google.com/search?q="))
        assertTrue(webUrl.contains("Kotlin"))

        val imgUrl = vm.selectCategory(GoogleCategory.IMAGES)
        assertTrue(imgUrl.contains("tbm=isch"))

        val vidUrl = vm.selectCategory(GoogleCategory.VIDEOS)
        assertTrue(vidUrl.contains("tbm=vid"))

        val newsUrl = vm.selectCategory(GoogleCategory.NEWS)
        assertTrue(newsUrl.contains("tbm=nws"))

        val mapsUrl = vm.selectCategory(GoogleCategory.MAPS)
        assertTrue(mapsUrl.startsWith("https://www.google.com/maps/search/"))
    }

    @Test
    fun browserViewModel_detectsDirectUrls() {
        val vm = BrowserViewModel()
        val directUrl = vm.submitSearchOrUrl("https://android.com")
        assertEquals("https://android.com", directUrl)

        val domainUrl = vm.submitSearchOrUrl("developer.android.com")
        assertEquals("https://developer.android.com", domainUrl)
    }

    @Test
    fun geminiRepository_modelConstantsAreSupported() {
        assertEquals("gemini-3.5-flash-lite", com.example.data.GeminiRepository.PRIMARY_MODEL)
        assertEquals("gemini-3.8-flash", com.example.data.GeminiRepository.FALLBACK_MODEL)
    }
}
