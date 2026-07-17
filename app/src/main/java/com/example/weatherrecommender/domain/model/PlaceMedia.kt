package com.example.weatherrecommender.domain.model

/**
 * Best-effort city photo + blurb enriched from Wikipedia (not Open-Meteo / Nominatim).
 *
 * Open-Meteo Geocoding and Nominatim return coordinates and labels only — no images.
 * When lookup fails, all fields stay null and the UI hides the stamp / description.
 */
data class PlaceMedia(
    val imageUrl: String? = null,
    val description: String? = null,
    val attribution: String? = null
) {
    val hasImage: Boolean get() = !imageUrl.isNullOrBlank()
    val hasDescription: Boolean get() = !description.isNullOrBlank()
    val isEmpty: Boolean get() = !hasImage && !hasDescription
}

/** Copies Wikipedia media fields onto this [Location] for Room persistence and UI. */
fun Location.withPlaceMedia(media: PlaceMedia): Location = copy(
    imageUrl = media.imageUrl,
    description = media.description,
    imageAttribution = media.attribution
)
