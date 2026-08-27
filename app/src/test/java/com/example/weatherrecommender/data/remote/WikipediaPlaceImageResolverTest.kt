package com.example.weatherrecommender.data.remote

import com.example.weatherrecommender.data.remote.dto.WikipediaImage
import com.example.weatherrecommender.data.remote.dto.WikipediaPage
import com.example.weatherrecommender.data.remote.dto.WikipediaQuery
import com.example.weatherrecommender.data.remote.dto.WikipediaResponse
import com.example.weatherrecommender.data.remote.dto.WikipediaSearchHit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WikipediaPlaceImageResolverTest {

    private val wikipediaApi: WikipediaApi = mockk()
    private lateinit var resolver: WikipediaPlaceImageResolver

    @Before
    fun setup() {
        resolver = WikipediaPlaceImageResolver(wikipediaApi)
    }

    @Test
    fun `resolve returns thumbnail when redirects yield Havana for La Habana`() = runTest {
        coEvery { wikipediaApi.getPageImage(titles = "La Habana") } returns pageWithThumb(
            title = "Havana",
            url = "https://example.com/havana.jpg"
        )

        assertEquals(
            "https://example.com/havana.jpg",
            resolver.resolve("La Habana", "Cuba")
        )
        coVerify(exactly = 1) { wikipediaApi.getPageImage(titles = "La Habana") }
        coVerify(exactly = 0) { wikipediaApi.searchPages(srsearch = any()) }
    }

    @Test
    fun `resolve falls back to name comma country when bare title has no image`() = runTest {
        coEvery { wikipediaApi.getPageImage(titles = "Roma") } returns emptyPage("Roma")
        coEvery { wikipediaApi.getPageImage(titles = "Roma, Italy") } returns pageWithThumb(
            title = "Rome",
            url = "https://example.com/rome.jpg"
        )

        assertEquals(
            "https://example.com/rome.jpg",
            resolver.resolve("Roma", "Italy")
        )
    }

    @Test
    fun `resolve uses search then pageimages when title variants miss`() = runTest {
        coEvery { wikipediaApi.getPageImage(titles = "La Habana") } returns emptyPage("La Habana")
        coEvery { wikipediaApi.getPageImage(titles = "La Habana, Cuba") } returns emptyPage("La Habana, Cuba")
        coEvery { wikipediaApi.searchPages(srsearch = "La Habana Cuba") } returns WikipediaResponse(
            query = WikipediaQuery(
                search = listOf(WikipediaSearchHit(title = "Havana", pageid = 49719))
            )
        )
        coEvery { wikipediaApi.getPageImage(titles = "Havana") } returns pageWithThumb(
            title = "Havana",
            url = "https://example.com/havana-search.jpg"
        )

        assertEquals(
            "https://example.com/havana-search.jpg",
            resolver.resolve("La Habana", "Cuba")
        )
    }

    @Test
    fun `resolve skips missing pages and pages without images`() = runTest {
        coEvery { wikipediaApi.getPageImage(titles = "Nowhereville") } returns WikipediaResponse(
            query = WikipediaQuery(
                pages = mapOf(
                    "-1" to WikipediaPage(
                        pageid = -1,
                        title = "Nowhereville",
                        missing = ""
                    )
                )
            )
        )
        coEvery { wikipediaApi.searchPages(srsearch = "Nowhereville") } returns WikipediaResponse(
            query = WikipediaQuery(search = emptyList())
        )

        assertNull(resolver.resolve("Nowhereville", country = null))
    }

    @Test
    fun `resolve prefers original when thumbnail absent`() = runTest {
        coEvery { wikipediaApi.getPageImage(titles = "Lisbon") } returns WikipediaResponse(
            query = WikipediaQuery(
                pages = mapOf(
                    "1" to WikipediaPage(
                        pageid = 1,
                        title = "Lisbon",
                        original = WikipediaImage(source = "https://example.com/lisbon-orig.jpg")
                    )
                )
            )
        )

        assertEquals(
            "https://example.com/lisbon-orig.jpg",
            resolver.resolve("Lisbon", "Portugal")
        )
    }

    @Test
    fun `resolve prefers thumbnail target over earlier original-only page`() = runTest {
        coEvery { wikipediaApi.getPageImage(titles = "La Habana") } returns WikipediaResponse(
            query = WikipediaQuery(
                pages = linkedMapOf(
                    "-1" to WikipediaPage(
                        pageid = -1,
                        title = "La Habana",
                        missing = ""
                    ),
                    "2" to WikipediaPage(
                        pageid = 2,
                        title = "Havana (disambiguation)",
                        original = WikipediaImage(source = "https://example.com/disambig.jpg")
                    ),
                    "49719" to WikipediaPage(
                        pageid = 49719,
                        title = "Havana",
                        thumbnail = WikipediaImage(source = "https://example.com/havana.jpg")
                    )
                )
            )
        )

        assertEquals(
            "https://example.com/havana.jpg",
            resolver.resolve("La Habana", "Cuba")
        )
    }

    @Test
    fun `resolve returns null for blank city name`() = runTest {
        assertNull(resolver.resolve("  ", "Cuba"))
        coVerify(exactly = 0) { wikipediaApi.getPageImage(titles = any()) }
    }

    private fun pageWithThumb(title: String, url: String) = WikipediaResponse(
        query = WikipediaQuery(
            pages = mapOf(
                "1" to WikipediaPage(
                    pageid = 1,
                    title = title,
                    thumbnail = WikipediaImage(source = url)
                )
            )
        )
    )

    private fun emptyPage(title: String) = WikipediaResponse(
        query = WikipediaQuery(
            pages = mapOf(
                "1" to WikipediaPage(pageid = 1, title = title)
            )
        )
    )
}
