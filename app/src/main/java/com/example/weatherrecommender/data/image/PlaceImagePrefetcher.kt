package com.example.weatherrecommender.data.image

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort Coil disk/memory warm for city postcard URLs.
 *
 * Call after Room stores a Wikipedia thumbnail so the next detail hop or home card
 * paints from cache instead of waiting on Wikimedia on the UI thread path.
 */
@Singleton
class PlaceImagePrefetcher @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Enqueues a decode of [imageUrl] at hero width when non-blank; no-ops on null/blank.
     */
    fun prefetch(imageUrl: String?) {
        val url = imageUrl?.takeIf { it.isNotBlank() } ?: return
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(HERO_THUMB_WIDTH_PX)
            .build()
        context.imageLoader.enqueue(request)
    }

    private companion object {
        /** Matches WikipediaApi pithumbsize / 16:9 hero width. */
        const val HERO_THUMB_WIDTH_PX = 800
    }
}
