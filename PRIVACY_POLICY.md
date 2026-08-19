# Privacy Policy — Concierge Weather Recommender

**Last updated:** August 19, 2026

## Overview

Concierge Weather Recommender ("the App") helps users search for cities and view weather-based activity recommendations. This policy describes what data the App processes and how it is used.

## Data We Collect

The App does **not** require account registration and does **not** collect personally identifiable information such as name, email, or phone number.

When you use the App, the following data may be processed:

| Data | Purpose | Stored locally? |
|------|---------|-----------------|
| City search queries | Sent to Open-Meteo Geocoding API to resolve locations | Search history is not persisted beyond the current session UI state |
| Map tap coordinates | Sent to Nominatim (OpenStreetMap) for reverse geocoding when you tap the map | Not stored beyond resolving the place name for the selected city |
| Selected location coordinates | Sent to Open-Meteo Forecast API for 7-day weather data | Yes — cached in on-device Room database for offline access |
| Forecast data | Display weather and activity recommendations | Yes — cached on device |
| Last-known GPS coordinates (when location permission is granted) | First-run day/night theme and optional current-location chip / map center — never auto-opens detail | Last-known fix is read on device only; coordinates are sent to Nominatim only if reverse-geocoding the chip city |
| Wikipedia page title | Thumbnail for city cards/hero; header **W** opens `https://en.wikipedia.org/wiki/{title}` in the system browser | Thumbnail URL cached on device (Room v8 `placeMetadataUpdatedAt`, 30-day reuse) |

## Third-Party Services

The App uses [Open-Meteo](https://open-meteo.com/) APIs for geocoding and weather forecasts. Requests to Open-Meteo include search terms and geographic coordinates. Refer to [Open-Meteo's terms](https://open-meteo.com/en/terms) for their data handling practices.

Map tiles are loaded from [OpenFreeMap](https://openfreemap.org/) via MapLibre. Map taps are reverse-geocoded with [Nominatim](https://nominatim.openstreetmap.org/) (OpenStreetMap). City thumbnails and the header Wikipedia link use [Wikimedia](https://www.mediawiki.org/wiki/API:Main_page) / English Wikipedia. Tile, geocoding, and Wikipedia providers receive place names or coordinates as needed. See [OSM Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/) and OpenStreetMap attribution requirements.


No advertising networks or analytics SDKs are included in the current release.

## Data Storage and Security

- Forecast and location data are stored locally on your device using Android Room.
- Backup is disabled at the application level (`allowBackup=false`).
- Network requests use HTTPS.

## Data Sharing

We do not sell or share user data with third parties beyond the API calls required to provide weather and geocoding services.

## Children's Privacy

The App is not directed at children under 13.

## Changes

We may update this policy. Material changes will be reflected in the "Last updated" date above.

## Contact

For privacy questions, contact the app maintainer through the project repository issue tracker.
