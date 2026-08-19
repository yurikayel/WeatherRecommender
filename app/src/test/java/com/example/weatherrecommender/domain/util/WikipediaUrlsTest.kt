package com.example.weatherrecommender.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WikipediaUrlsTest {

    @Test
    fun blankTitle_returnsNull() {
        assertNull(WikipediaUrls.articleUrl(null))
        assertNull(WikipediaUrls.articleUrl(""))
        assertNull(WikipediaUrls.articleUrl("   "))
    }

    @Test
    fun singleWordCity_usesCanonicalEnWikiPath() {
        assertEquals("https://en.wikipedia.org/wiki/London", WikipediaUrls.articleUrl("London"))
        assertEquals("https://en.wikipedia.org/wiki/Lisbon", WikipediaUrls.articleUrl(" Lisbon "))
    }

    @Test
    fun multiWordCity_usesUnderscores() {
        assertEquals(
            "https://en.wikipedia.org/wiki/New_York",
            WikipediaUrls.articleUrl("New York")
        )
    }

    @Test
    fun accentedTitle_isPercentEncoded() {
        assertEquals(
            "https://en.wikipedia.org/wiki/S%C3%A3o_Paulo",
            WikipediaUrls.articleUrl("São Paulo")
        )
    }
}
