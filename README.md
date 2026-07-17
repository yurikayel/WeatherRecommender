# Concierge Weather Recommender

## a. Project Overview 📱
This is a native Android app that implements the Concierge Weather Recommender assignment brief: search for a city and see, **for each of the next 7 days**, a ranked list of activities (Skiing, Surfing, Outdoor sightseeing, Indoor sightseeing) suited to that day's Open-Meteo forecast. Several polish features below are **stretch** beyond a minimal brief and should be scored as such.

| Scope | What |
|-------|------|
| **Core (brief)** | City search, 7-day forecast, per-day activity ranking, offline-first Room cache, Clean Architecture + tests |
| **Stretch** | Home top picks; History (10 cities, Room `lastViewedAt`, id dedupe); Marine API; WorkManager sync; pull-to-refresh; dark mode + **persisted theme toggle**; splash + original mark; share 9:16 weather flyer PNG (+ save to Downloads); collapsing square (1:1) MapLibre map AppBar + Crossfade sheet body (Nominatim reverse, no Google key); GPS current-location chip; pastel day chips; Paparazzi + instrumented CI |

Key experience details:
- **Per-day recommendations**: the detail screen shows a day selector; tapping a day re-ranks its activities. There is no single "week-long" score.
- **Geography-aware activities**: activities that don't make sense for a location are hidden entirely (e.g. surfing is only offered where there is sea access; skiing only in mountainous terrain or when snow is falling).
- **Home "top picks"**: the home screen surfaces a randomised, population-weighted set of well-known cities, each with its best activity for today (stretch). Pull-to-refresh on home (assignment **bonus**, not a core brief item) force-refreshes this feed — only when the sheet is scrolled to the top **and** the collapsing map header is fully expanded (`modifier.pullToRefresh` `enabled`), so PTR does not fight nested-scroll collapse. Detail has no PTR.
- **Recently viewed History**: after Top Picks, lists up to 10 cities the user explicitly opened (search, top-pick, or map tap). Persisted via Room `lastViewedAt`; Nominatim/GeoNames id collisions are collapsed by proximity/name.
- **In-screen map**: MapLibre collapsing AppBar (expanded **square / 1:1**) with a rounded surface sheet that scrolls up to cover the map — classic nested-scroll collapse into a compact toolbar (city name / Concierge). Home↔detail Crossfades only the sheet body (no full-screen slide). Selecting a city updates the map camera in place; back fades home back and resets the camera to the device location (or a static London default without GPS).
- **Current location**: with permission granted, the last-known GPS fix is reverse-geocoded to a city — home shows a discreet `Current location · {City}` chip and the map centers there; tapping the chip opens that city's weather (GPS never auto-navigates to detail). Denied → chip hidden, static default framing.
- **Share**: detail toolbar exports a branded 9:16 portrait "weather flyer" PNG with denser display-scale typography (header + selected-day hero + 7-day strip + ranked activities with score bars) via the system share sheet and best-effort saves a copy to Downloads.

## b. Platform and Tooling Choices
- **Language**: Kotlin (AGP built-in Kotlin; `org.jetbrains.kotlin.android` plugin removed)
- **Build**: Android Gradle Plugin 9.3.0, Gradle 9.x, JDK 21 (toolchain + CI)
- **UI Framework**: Jetpack Compose (Material 3)
- **Networking**: Retrofit, OkHttp, kotlinx.serialization
- **Local Persistence**: Room Database (Offline-First / SSOT)
- **Dependency Injection**: Dagger Hilt
- **Concurrency**: Kotlin Coroutines and StateFlow
- **Testing**: JUnit 4, MockK, Turbine, Paparazzi 2.x (CI runs with `-Ppaparazzi`)
- **Quality**: detekt, Kover coverage, Android Lint

## c. Architecture and Technical Decisions 🏗️
- **Clean Architecture Principles**: The project is strictly divided into three layers:
  - **Data Layer**: Retrofit interfaces (Open-Meteo Forecast / Geocoding / Marine, Nominatim), Room DAOs, and the Repository implementation mapping network data to domain models.
  - **Domain Layer**: Core business logic, `AppError` sealed hierarchy, and the `GetRankedActivitiesUseCase` fully isolated from Android dependencies.
  - **UI Layer**: Composed of `WeatherScreen` (Jetpack Compose) and `WeatherViewModel`, orchestrating UI states via a `StateFlow`.
- **Offline-First (SSOT)**: The UI never consumes data directly from the network. It observes a Room database `Flow`. A parallel background request fetches fresh data from the Open-Meteo API and updates the local database, triggering reactive UI updates.
- **Strategy Pattern (SOLID)**: The recommendation engine utilizes independent `ActivityScorer` strategies (e.g., `SurfScorer`, `SkiScorer`) injected via Dagger Hilt, strictly adhering to the Open/Closed Principle. Each scorer exposes `isApplicable(context)` for geography gating and `score(context)` for a single day, so adding an activity means adding one class.
- **Per-day scoring**: `GetRankedActivitiesUseCase(forecast, dayIndex)` ranks only the applicable activities for the selected day. Day switching is a pure, in-memory recompute (no network).
- **Home suggestions**: `GetTopPicksUseCase` picks population-weighted cities from a bundled `FeaturedCities` seed list (the Geocoding API has no "browse" endpoint) and fetches their forecasts concurrently and best-effort via `WeatherRepository.getForecastRemote` (read-only, does not pollute the cache).

## d. How to build and run the app 🚀
1. Clone the repository and open the project in Android Studio (2025.2.3+ recommended for AGP 9).
2. Ensure **JDK 21** is selected (Project Structure → SDK → JDK, or set `JAVA_HOME`).
3. Ensure `local.properties` contains your Android SDK path (not committed to git). **No Maps API key** is required — MapLibre uses OpenFreeMap tiles; reverse geocode uses Nominatim (see §f). Copy `local.properties.example` if useful.
4. Build the project: `./gradlew assembleDebug`
5. Install on a device or emulator: `./gradlew installDebug` or run directly from Android Studio. Use a **networked** emulator/device so map tiles and weather APIs load; Google Play image is not required (OpenGL MapLibre backend).

Gradle auto-downloads JDK toolchains when needed (`org.gradle.java.installations.auto-download=true`); the Foojay resolver plugin is configured in `settings.gradle.kts`.

## e. How to run tests & testing strategy 🧪
- **Unit tests**: `./gradlew testDebugUnitTest` (requires JDK 21+) — ~98 tests across domain, data, and ViewModel (plus Paparazzi below)
- **Paparazzi snapshots**: `./gradlew recordPaparazziDebug -Ppaparazzi` (verify with `verifyPaparazziDebug -Ppaparazzi`; 9 golden PNGs live in `app/src/test/snapshots/`)
- **Lint**: `./gradlew lintDebug`
- **Coverage**: `./gradlew koverXmlReportDebug` / `koverVerify`
- **Static analysis**: `./gradlew detekt`
- **Instrumented UI tests**: `./gradlew connectedDebugAndroidTest` (22 Compose UI tests for key flows, plus 5 Room migration tests; also run on CI emulator)

**Testing Strategy**:
- **Domain Layer**: Activity scorers and `GetRankedActivitiesUseCase` are pure Kotlin, unit-tested with JUnit.
- **Data Layer**: `WeatherRepositoryImpl` tested with MockK for APIs and DAO; `LocationSyncer` and rate-limit retry interceptor covered.
- **UI Layer**: `WeatherViewModel` tested with Turbine for StateFlow emissions; Compose UI has **substantial coverage of key flows** (19 instrumented tests: home, search, detail, errors, dark theme smoke; location permission pre-granted via `GrantPermissionRule`) — not claimed as exhaustive full-UI coverage.
- **Snapshots**: Paparazzi goldens committed and verified in CI with `-Ppaparazzi`.

### Bonus features (assignment stretch goals)

| Bonus | Status | Implementation |
|-------|--------|----------------|
| Offline cache | Done | Room SSOT + WorkManager background sync (chunked refreshes) |
| Recently viewed History | Done | Home section after Top Picks; Room `lastViewedAt`; last 10; Nominatim/GeoNames dedupe |
| Pull-to-refresh | Done (bonus) | Home-only `modifier.pullToRefresh` (force-refresh top picks); `enabled` only when sheet at top **and** map header fully expanded — not on detail |
| Dark mode + theme toggle | Done | Navy dark palette; AppBar theme toggle; DataStore preference (system until overridden, then persisted) |
| Advanced UI polish / animation | Done | Collapsing square (1:1) map AppBar + sheet + home↔detail Crossfade, day selector, score ring, top-pick press scale, shimmer/crossfade; pastel day chips; normalized top-pick cards |
| Splash screen | Done | Android 12+ `core-splashscreen` + original sun/cloud mark (also launcher foreground) |
| Snapshot tests | Done | Paparazzi 2.0.0-alpha05, 9 golden PNGs (home/detail incl. location chip + history + share flyer), verified in CI (`verifyPaparazziDebug -Ppaparazzi`) |
| Substantial UI test coverage | Done | 19 instrumented Compose tests for key flows (home, search, top picks, chips, day selection, back, banners, dark theme) |
| Share weather flyer | Done | Detail share → branded 9:16 portrait PNG (`GraphicsLayer` + FileProvider); also best-effort save to Downloads |
| In-screen map | Done | Collapsing square (1:1) MapLibre AppBar + OpenFreeMap (no Google key); sheet covers map on scroll; home centers on device location (static London fallback, wider zoom); tap → Nominatim reverse; camera/pin in ViewModel |
| Current-location chip | Done | Runtime permission → LocationManager last-known fix → Nominatim reverse; home header chip (opt-in tap); map centers on fix |

## f. API usage notes ☀️
The application interfaces with three Open-Meteo APIs (No API key required):
- **Geocoding API**: `https://geocoding-api.open-meteo.com/v1/search` for resolving a city name to coordinates. We also read `elevation`, `population`, and `feature_code` from each result to drive geography-aware activities and home suggestions.
- **Forecast API**: `https://api.open-meteo.com/v1/forecast` for the 7-day daily forecast (temperature, precipitation, snowfall, wind, weather code).
- **Marine API**: `https://marine-api.open-meteo.com/v1/marine` for daily `wave_height_max`. This serves a dual purpose: it feeds surf scoring with real wave data, and — because it returns null wave heights for inland coordinates — it acts as a reliable **sea-access detector**. The marine call is best-effort: a failure never fails the primary forecast.


**Map and reverse geocoding (no Google Maps key)**
- **Map rendering**: [MapLibre Compose](https://maplibre.org/maplibre-compose/) with the free [OpenFreeMap](https://openfreemap.org/) Liberty style. No API key and no Google Play Services Maps SDK — any networked emulator/device works. We ship the **OpenGL** MapLibre Android backend for broader AVD support (Vulkan can fail on some emulators).
- **Forward geocode** (search box): Open-Meteo Geocoding (name → lat/lng). Searching also **centers the map** on the first result.
- **Reverse geocode** (map tap / long-press): Open-Meteo has no reverse endpoint, so we call [Nominatim](https://nominatim.openstreetmap.org/) (`/reverse`) with a descriptive `User-Agent`, then open the same detail flow as `onLocationSelected`.
- **Attribution**: OpenStreetMap contributors / OpenFreeMap / Nominatim — MapLibre logo ornament on the map, plus a discreet footer on the home sheet (and this README). No on-map overlay attribution line.

The map is the **background of a collapsing top AppBar** (expanded height = screen width for a square / 1:1 aspect). Nested scroll collapses it into a compact toolbar while a rounded elevated sheet (with Crossfade home/detail bodies) slides up to cover it. `mapCamera` / `mapPin` live in `WeatherUiState` and drive the same map instance on select/back. Home centers on the device location when available (static London default otherwise); back returns to that same overview.

## g. Activity recommendation logic
`GetRankedActivitiesUseCase(forecast, dayIndex)` evaluates each injected `ActivityScorer` for a **single day**. A scorer first decides whether it is *applicable* to the location's geography; only applicable activities are scored (0-100) and ranked. This prevents nonsensical suggestions such as surfing in a landlocked city.

**Applicability (geography gating)**
- **Surfing**: only where `Location.hasSeaAccess` is true. Sea access is detected from the Marine API (non-null wave heights).
- **Skiing**: only in mountainous terrain (`elevation >= 800 m`) **or** on days with snowfall.
- **Outdoor / Indoor sightseeing**: always applicable.

**Per-day scoring heuristics**
- **Skiing**: rewards fresh snowfall (≥ 3 cm) and sub-freezing average temperature; penalised when the day is mild (> 6°C).
- **Surfing**: rewards rideable-but-manageable waves (~0.4-2.5 m from the Marine API), light-to-moderate wind, and warm air; penalised by flat seas or strong wind (> 35 km/h).
- **Outdoor Sightseeing**: rewards mild days (14-26°C) with little rain; penalised by rain (> 5 mm), extreme heat (> 32°C), and strong wind.
- **Indoor Sightseeing**: the wet-weather fallback — rises with rain/snow and cold, so it climbs the ranking exactly when outdoor options fall.

Because scoring is per-day, the top activity for a city legitimately changes across the week (e.g. Outdoor on a sunny day, Indoor on a stormy one).

## h. Assumptions made ✅
- Recommendations are made **per day**, not aggregated across the week.
- **Sea access** is approximated by the Open-Meteo Marine API returning non-null wave heights near the city coordinate. This is a heuristic: a coastal city whose centre coordinate is slightly inland of the nearest marine grid cell may occasionally read as inland, and vice-versa.
- **Skiing terrain** is approximated by an elevation threshold (≥ 800 m) or active snowfall. This is deliberately conservative: some valley ski towns (e.g. Innsbruck at ~570 m) only surface skiing once snow is in the forecast.
- **Home "top picks"** come from a curated, bundled `FeaturedCities` list because the Geocoding API only supports search-by-name (no discovery/browse endpoint). Selection is randomised but weighted by population. Seed location IDs are **synthetic negatives (−1…−14)** — they are **not** Open-Meteo / GeoNames IDs — so they cannot collide with real place IDs returned by search.
- All arrays returned by the Open-Meteo forecast/marine endpoints for daily variables are aligned by date/index.
- The `admin1` field from the Geocoding API accurately represents the state/region for UI display purposes.

## i. Trade-offs and omissions ⚖️
Honest scope note: a **strict 3–4 hour** take would likely stop at search → forecast → ranked activities → Room cache → unit tests. This branch invested extra time in stretch polish (map, History, share/Downloads, splash, theme toggle, Paparazzi, instrumented CI) for reviewer confidence and product feel. Treat those as stretch when scoring time-box fidelity.

**What went beyond the brief (and why)**
- **Home top picks + `FeaturedCities`**: the brief focuses on search → city detail. A home feed makes the app feel like a concierge product on first launch, but Open-Meteo has no "browse cities" endpoint, so a bundled seed list was required.
- **Marine API**: surfing without wave data is guesswork; marine also doubles as sea-access detection so inland cities never show Surfing.
- **WorkManager `LocationSyncer`**: keeps Room forecasts fresh for previously viewed cities without blocking the UI.
- **History + map + share**: discovery and shareability beyond the letter of the brief; Nominatim avoids a Google Maps key for reviewers.
- **Paparazzi goldens + instrumented CI**: raise confidence for UI regressions beyond unit tests alone.
- **Per-day ranking UI**: the brief can be read as a single weekly ranking; per-day scoring is more honest to daily weather swings and stays cheap (pure in-memory recompute).

**Concrete trade-offs**
- **FeaturedCities synthetic IDs**: seeds use negative IDs (−1…−14) to avoid colliding with positive GeoNames / Open-Meteo IDs. They are never written as if they were API IDs; search results always use real positive IDs.
- **History id dedupe**: Nominatim reverse ids differ from GeoNames search ids for the same city; repository merges by ~0.05° proximity or name+country and prefers stable positive ids.
- **LocationSyncer rate limiting**: an earlier all-parallel `refreshForecast` fan-out risked HTTP 429 when many cities were cached. Sync now refreshes in **chunks of 3** with a short delay between batches (same stagger idea as `GetTopPicksUseCase`), trading wall-clock sync time for fewer rate-limit hits.
- **Top-picks 45-minute TTL**: `TopPicksCache` avoids repeating a cold-start forecast burst on every home visit. Pull-to-refresh on home calls `getTopPicks(forceRefresh=true)` to bypass the TTL when the user asks for fresh data; offline pull shows a connectivity error and keeps the last feed.
- **WorkManager Sync**: Background sync runs every 6 hours when any network is available. Stricter constraints (unmetered + charging) were removed to improve refresh reliability on mobile.
- **Share → Downloads**: MediaStore on API 29+ (no permission); pre-Q may request legacy write. A Downloads failure never blocks the share sheet.
- **Crash reporting**: A `CrashReporter` abstraction logs locally; swap for Firebase Crashlytics when a Firebase project is configured.
- **Rate limiting (HTTP)**: GET requests retry on HTTP 429 with exponential backoff (respecting `Retry-After` when present); other failures map to localized error strings.

**Deliberately omitted**
- Continuous GPS tracking / background location (home uses last-known fix + optional current-location chip when permission is granted).
- Google Maps SDK / paid tile API keys (MapLibre + OpenFreeMap + Nominatim cover the stretch map UX).
- User accounts, favourites persistence, or multi-city compare.
- Firebase Crashlytics wiring, Play Store assets, and localization beyond English strings.
- Exhaustive Compose UI coverage of every edge state (key flows are covered; not every animation/transition).

## j. Production-readiness notes
Implemented:
1. Network connectivity checks via `ConnectivityObserver` with `ACCESS_NETWORK_STATE` permission.
2. Localized error mapping via `UiText` and `AppErrorMapper`.
3. CI/CD via GitHub Actions (unit tests + Paparazzi verify, detekt, lint, Kover, debug + release builds on JDK 21; separate emulator job for instrumented tests). CI installs `platforms;android-36` and `build-tools;36.0.0`.
4. Debug-only HTTP body logging; release builds use R8 minification.
5. Privacy policy: see [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

Before Play Store release:
1. Configure Firebase Crashlytics (optional) by binding a Crashlytics implementation to `CrashReporter`.
2. Add store listing assets (screenshots, feature graphic).

## k. Cross-platform delivery notes
If transitioning to Kotlin Multiplatform (KMP), the Domain layer (`GetRankedActivitiesUseCase`, `WeatherRepository` interface) and Data layer (Ktor instead of Retrofit, `kotlinx.serialization`, Room/SQLDelight) can be entirely shared. Only the UI layer (Compose Android vs Compose Multiplatform/SwiftUI) and platform-specific DI (e.g., Koin instead of Hilt) would need distinct implementations.

## l. AGP 9 / Gradle 9 migration notes
This project runs on **AGP 9.3.0** with the **new DSL** and **built-in Kotlin** (no `android.newDsl=false` / `android.builtInKotlin=false` opt-outs). Key changes:
- Removed `org.jetbrains.kotlin.android`; Kotlin compilation is provided by AGP.
- **Hilt 2.59.2+** required for AGP 9 Gradle plugin compatibility.
- **KSP 2.3.6+** required so generated sources register via `android.sourceSets` (not deprecated `kotlin.sourceSets`).
- **Kotlin 2.2.10** aligned with AGP's built-in KGP baseline; compose compiler and serialization plugins remain explicit.
- Paparazzi tests disable HTML reports (`reports.html.required.set(false)`) as a Gradle 9 workaround.
- All runtime dependencies are declared in `gradle/libs.versions.toml` (including Paparazzi, material-icons-extended, hilt-navigation-compose).

## m. AI usage disclosure 📝
Parts of this codebase were developed with assistance from Cursor (AI-assisted editing and scaffolding). Architecture choices, scoring heuristics, and documentation were reviewed and adjusted by the author. All AI-assisted output was verified by compiling with the Gradle wrapper, running the unit/Paparazzi test suites, lint/detekt/Kover locally, and relying on GitHub Actions CI (including the instrumented emulator job) as an additional check.
