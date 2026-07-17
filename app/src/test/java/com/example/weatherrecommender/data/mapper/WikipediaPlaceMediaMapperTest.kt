package com.example.weatherrecommender.data.mapper

import com.example.weatherrecommender.data.remote.dto.WikipediaSummaryDto
import com.example.weatherrecommender.data.remote.dto.WikipediaThumbnailDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaPlaceMediaMapperTest {

    @Test
    fun `candidateTitles prefers bare name then city with country`() {
        val titles = WikipediaPlaceMediaMapper.candidateTitles("Lisbon", "Portugal")
        assertEquals(listOf("Lisbon", "Lisbon, Portugal"), titles)
    }

    @Test
    fun `candidateTitles skips blank country and blank name`() {
        assertEquals(listOf("Paris"), WikipediaPlaceMediaMapper.candidateTitles("Paris", "  "))
        assertTrue(WikipediaPlaceMediaMapper.candidateTitles("  ", "France").isEmpty())
    }

    @Test
    fun `encodeTitle percent-encodes spaces`() {
        assertEquals("New%20York", WikipediaPlaceMediaMapper.encodeTitle("New York"))
    }

    @Test
    fun `toPlaceMedia maps thumbnail and extract`() {
        val media = WikipediaPlaceMediaMapper.toPlaceMedia(
            WikipediaSummaryDto(
                type = "standard",
                title = "Lisbon",
                extract = "Lisbon is the capital of Portugal.",
                thumbnail = WikipediaThumbnailDto(source = "https://upload.wikimedia.org/lisbon.jpg")
            )
        )
        assertEquals("https://upload.wikimedia.org/lisbon.jpg", media?.imageUrl)
        assertEquals("Lisbon is the capital of Portugal.", media?.description)
        assertEquals("Lisbon", media?.attribution)
    }

    @Test
    fun `toPlaceMedia returns null for disambiguation pages`() {
        val media = WikipediaPlaceMediaMapper.toPlaceMedia(
            WikipediaSummaryDto(
                type = "disambiguation",
                title = "Paris",
                extract = "Paris may refer to…"
            )
        )
        assertNull(media)
    }

    @Test
    fun `toPlaceMedia returns null when both image and extract missing`() {
        assertNull(
            WikipediaPlaceMediaMapper.toPlaceMedia(
                WikipediaSummaryDto(type = "standard", title = "Nowhere")
            )
        )
    }
}
