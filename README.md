# Concierge Weather Recommender

## a. Project Overview 📱
This is a native Android app that implements the Concierge Weather Recommender assignment brief: search for a city and see, **for each of the next 7 days**, a ranked list of activities (Skiing, Surfing, Outdoor sightseeing, Indoor sightseeing) suited to that day's Open-Meteo forecast. Home-screen top picks, the Marine API sea-access path, and WorkManager background sync are conscious stretch goals beyond the core brief.

| Scope | What |
|-------|------|
| **Core (brief)** | City search, 7-day forecast, per-day activity ranking, offline-first Room cache, Clean Architecture + tests |
| **Stretch** | Home top picks (`FeaturedCities` + TTL cache), Marine API (surf + sea access), WorkManager location sync, Paparazzi goldens, instrumented CI |

Key experience details:
- **Per-day recommendations**: the detail screen shows a day selector; tapping a day re-ranks its activities. There is no single "week-long" score.
- **Geography-aware activities**: activities that don't make sense for a location are hidden entirely (e.g. surfing is only offered where there is sea access; skiing only in mountainous terrain or when snow is falling).
- **Home "top picks"**: the home screen surfaces a randomised, population-weighted set of well-known cities, each with its best activity for today (stretch). Pull-to-refresh on home force-refreshes this feed.

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
  - **Data Layer**: Retrofit interfaces, Room DAOs, and the Repository implementation mapping network data to domain models.
  - **Domain Layer**: Core business logic, `AppError` sealed hierarchy, and the `GetRankedActivitiesUseCase` fully isolated from Android dependencies.
  - **UI Layer**: Composed of `WeatherScreen` (Jetpack Compose) and `WeatherViewModel`, orchestrating UI states via a `StateFlow`.
- **Offline-First (SSOT)**: The UI never consumes data directly from the network. It observes a Room database `Flow`. A parallel background request fetches fresh data from the Open-Meteo API and updates the local database, triggering reactive UI updates.
- **Strategy Pattern (SOLID)**: The recommendation engine utilizes independent `ActivityScorer` strategies (e.g., `SurfScorer`, `SkiScorer`) injected via Dagger Hilt, strictly adhering to the Open/Closed Principle. Each scorer exposes `isApplicable(context)` for geography gating and `score(context)` for a single day, so adding an activity means adding one class.
- **Per-day scoring**: `GetRankedActivitiesUseCase(forecast, dayIndex)` ranks only the applicable activities for the selected day. Day switching is a pure, in-memory recompute (no network).
- **Home suggestions**: `GetTopPicksUseCase` picks population-weighted cities from a bundled `FeaturedCities` seed list (the Geocoding API has no "browse" endpoint) and fetches their forecasts concurrently and best-effort via `WeatherRepository.getForecastRemote` (read-only, does not pollute the cache).

## d. How to build and run the app 🚀
1. Clone the repository and open the project in Android Studio (2025.2.3+ recommended for AGP 9).
2. Ensure **JDK 21** is selected (Project Structure → SDK → JDK, or set `JAVA_HOME`).
3. Ensure `local.properties` contains your Android SDK path (not committed to git).
4. Build the project: `./gradlew assembleDebug`
5. Install on a device or emulator: `./gradlew installDebug` or run directly from Android Studio.

Gradle auto-downloads JDK toolchains when needed (`org.gradle.java.installations.auto-download=true`); the Foojay resolver plugin is configured in `settings.gradle.kts`.

## e. How to run tests & testing strategy 🧪
- **Unit tests**: `./gradlew testDebugUnitTest` (requires JDK 21+) — ~50+ tests across domain, data, and ViewModel (plus Paparazzi below)
- **Paparazzi snapshots**: `./gradlew recordPaparazziDebug -Ppaparazzi` (verify with `verifyPaparazziDebug -Ppaparazzi`; 6 golden PNGs live in `app/src/test/snapshots/`)
- **Lint**: `./gradlew lintDebug`
- **Coverage**: `./gradlew koverXmlReportDebug` / `koverVerify`
- **Static analysis**: `./gradlew detekt`
- **Instrumented UI tests**: `./gradlew connectedDebugAndroidTest` (~17 Compose UI tests for key flows, plus Room migration tests; also run on CI emulator)

**Testing Strategy**:
- **Domain Layer**: Activity scorers and `GetRankedActivitiesUseCase` are pure Kotlin, unit-tested with JUnit.
- **Data Layer**: `WeatherRepositoryImpl` tested with MockK for APIs and DAO; `LocationSyncer` and rate-limit retry interceptor covered.
- **UI Layer**: `WeatherViewModel` tested with Turbine for StateFlow emissions; Compose UI has **substantial coverage of key flows** (~17 instrumented tests: home, search, detail, errors, dark theme smoke) — not claimed as exhaustive full-UI coverage.
- **Snapshots**: Paparazzi goldens committed and verified in CI with `-Ppaparazzi`.

### Bonus features (assignment stretch goals)

| Bonus | Status | Implementation |
|-------|--------|----------------|
| Offline cache | Done | Room SSOT + WorkManager background sync |
| Pull-to-refresh | Done | `PullToRefreshBox` on city detail **and** home (force-refresh top picks) |
| Dark mode | Done | Navy-tinted dark palette, primary containers, system bar icon contrast; light + dark Paparazzi goldens |
| Advanced UI polish / animation | Done | Home↔detail slide transition, animated day selector, score ring sweep, top-pick press scale, shimmer + crossfade loading |
| Snapshot tests | Done | Paparazzi 2.0.0-alpha05, 6 golden PNGs, verified in CI (`verifyPaparazziDebug -Ppaparazzi`) |
| Substantial UI test coverage | Done | ~17 instrumented Compose tests for key flows (home greeting, search/clear, top picks, geography chips, day selection, back nav, sync/error banners, dark theme) |

## f. API usage notes ☀️
The application interfaces with three Open-Meteo APIs (No API key required):
- **Geocoding API**: `https://geocoding-api.open-meteo.com/v1/search` for resolving a city name to coordinates. We also read `elevation`, `population`, and `feature_code` from each result to drive geography-aware activities and home suggestions.
- **Forecast API**: `https://api.open-meteo.com/v1/forecast` for the 7-day daily forecast (temperature, precipitation, snowfall, wind, weather code).
- **Marine API**: `https://marine-api.open-meteo.com/v1/marine` for daily `wave_height_max`. This serves a dual purpose: it feeds surf scoring with real wave data, and — because it returns null wave heights for inland coordinates — it acts as a reliable **sea-access detector**. The marine call is best-effort: a failure never fails the primary forecast.

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
This solution intentionally went beyond a strict 3–4 hour brief where it improved correctness, polish, or reviewer confidence; the trade-offs below explain those choices and what was left out.

**What went beyond the brief (and why)**
- **Home top picks + `FeaturedCities`**: the brief focuses on search → city detail. A home feed makes the app feel like a concierge product on first launch, but Open-Meteo has no "browse cities" endpoint, so a bundled seed list was required.
- **Marine API**: surfing without wave data is guesswork; marine also doubles as sea-access detection so inland cities never show Surfing.
- **WorkManager `LocationSyncer`**: keeps Room forecasts fresh for previously viewed cities without blocking the UI.
- **Paparazzi goldens + instrumented CI**: raise confidence for UI regressions beyond unit tests alone.
- **Per-day ranking UI**: the brief can be read as a single weekly ranking; per-day scoring is more honest to daily weather swings and stays cheap (pure in-memory recompute).

**Concrete trade-offs**
- **FeaturedCities synthetic IDs**: seeds use negative IDs (−1…−14) to avoid colliding with positive GeoNames / Open-Meteo IDs. They are never written as if they were API IDs; search results always use real positive IDs.
- **LocationSyncer rate limiting**: an earlier all-parallel `refreshForecast` fan-out risked HTTP 429 when many cities were cached. Sync now refreshes in **chunks of 3** with a short delay between batches (same stagger idea as `GetTopPicksUseCase`), trading wall-clock sync time for fewer rate-limit hits.
- **Top-picks 45-minute TTL**: `TopPicksCache` avoids repeating a cold-start forecast burst on every home visit. Pull-to-refresh on home calls `getTopPicks(forceRefresh=true)` to bypass the TTL when the user asks for fresh data; offline pull shows a connectivity error and keeps the last feed.
- **WorkManager Sync**: Background sync runs every 6 hours when any network is available. Stricter constraints (unmetered + charging) were removed to improve refresh reliability on mobile.
- **Crash reporting**: A `CrashReporter` abstraction logs locally; swap for Firebase Crashlytics when a Firebase project is configured.
- **Rate limiting (HTTP)**: GET requests retry on HTTP 429 with exponential backoff (respecting `Retry-After` when present); other failures map to localized error strings.

**Deliberately omitted**
- Device GPS / current-location weather (brief is city search).
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
