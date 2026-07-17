# Concierge Weather Recommender

## a. Project Overview
This is a native Android application built in Kotlin that acts as a Concierge service for premium users. It allows users to search for a city and see a ranked list of activities (Skiing, Surfing, Outdoor sightseeing, Indoor sightseeing) based on the 7-day weather forecast.

## b. Platform and Tooling Choices
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Networking**: Retrofit, OkHttp, kotlinx.serialization
- **Local Persistence**: Room Database (Offline-First / SSOT)
- **Dependency Injection**: Dagger Hilt
- **Concurrency**: Kotlin Coroutines and StateFlow
- **Testing**: JUnit 4, MockK, Turbine, Paparazzi (optional, `-Ppaparazzi`)
- **Quality**: detekt, Kover coverage, Android Lint

## c. Architecture and Technical Decisions
- **Clean Architecture Principles**: The project is strictly divided into three layers:
  - **Data Layer**: Retrofit interfaces, Room DAOs, and the Repository implementation mapping network data to domain models.
  - **Domain Layer**: Core business logic, `AppError` sealed hierarchy, and the `GetRankedActivitiesUseCase` fully isolated from Android dependencies.
  - **UI Layer**: Composed of `WeatherScreen` (Jetpack Compose) and `WeatherViewModel`, orchestrating UI states via a `StateFlow`.
- **Offline-First (SSOT)**: The UI never consumes data directly from the network. It observes a Room database `Flow`. A parallel background request fetches fresh data from the Open-Meteo API and updates the local database, triggering reactive UI updates.
- **Strategy Pattern (SOLID)**: The recommendation engine utilizes independent `ActivityScorer` strategies (e.g., `SurfScorer`, `SkiScorer`) injected via Dagger Hilt, strictly adhering to the Open/Closed Principle.

## d. How to build and run the app
1. Clone the repository and open the project in Android Studio.
2. Ensure `local.properties` contains your Android SDK path (not committed to git).
3. Build the project: `./gradlew assembleDebug`
4. Install on a device or emulator: `./gradlew installDebug` or run directly from Android Studio.

## e. How to run tests & testing strategy
- **Unit tests**: `./gradlew testDebugUnitTest`
- **Paparazzi snapshots** (requires Android SDK platform 37): `./gradlew recordPaparazziDebug -Ppaparazzi`
- **Lint**: `./gradlew lintDebug`
- **Coverage**: `./gradlew koverXmlReportDebug`
- **Static analysis**: `./gradlew detekt`
- **Instrumented UI tests**: `./gradlew connectedDebugAndroidTest`

**Testing Strategy**:
- **Domain Layer**: Activity scorers and `GetRankedActivitiesUseCase` are pure Kotlin, unit-tested with JUnit.
- **Data Layer**: `WeatherRepositoryImpl` tested with MockK for APIs and DAO.
- **UI Layer**: `WeatherViewModel` tested with Turbine for StateFlow emissions; Compose UI tested with instrumented tests.

## f. API usage notes
The application interfaces with two Open-Meteo APIs (No API key required):
- **Geocoding API**: `https://geocoding-api.open-meteo.com/v1/search` for fetching coordinate data from a city name search query.
- **Forecast API**: `https://api.open-meteo.com/v1/forecast` for retrieving the 7-day weather forecast.

## g. Activity recommendation logic
The `GetRankedActivitiesUseCase` relies on injected `ActivityScorer` classes that assign a score (0-100) based on the 7-day forecast aggregates:
- **Skiing**: Scores high if significant snowfall (> 10mm) and freezing temperatures (< 0°C). Penalized by high temperatures.
- **Surfing**: Prioritizes low max wind speeds (< 15km/h for glassy waves) combined with warm temperatures (> 20°C). Severely penalized by gale winds (> 30km/h).
- **Outdoor Sightseeing**: Thrives in moderate temperatures (15°C - 25°C) with low precipitation. Penalized by heavy rain and extreme heat.
- **Indoor Sightseeing**: Acts as a fallback for poor weather. Ranks highest when precipitation is high or temperatures are extremely cold (< 5°C).

## h. Assumptions made
- The app aggregates the 7-day forecast into a single "week-long" recommendation score rather than recommending different activities for different days of the week.
- All arrays returned by the Open-Meteo forecast endpoint for daily variables are aligned by index (same size).
- The `admin1` field from the Geocoding API accurately represents the state/region for UI display purposes.

## i. Trade-offs and omissions
- **WorkManager Sync**: Background sync runs every 6 hours when any network is available. Stricter constraints (unmetered + charging) were removed to improve refresh reliability on mobile.
- **Crash reporting**: A `CrashReporter` abstraction logs locally; swap for Firebase Crashlytics when a Firebase project is configured.
- **Rate limiting**: HTTP 429 responses map to localized error strings; no client-side retry backoff yet.

## j. Production-readiness notes
Implemented:
1. Network connectivity checks via `ConnectivityObserver` with `ACCESS_NETWORK_STATE` permission.
2. Localized error mapping via `UiText` and `AppErrorMapper`.
3. CI/CD via GitHub Actions (unit tests, detekt, lint, Kover, debug + release builds).
4. Debug-only HTTP body logging; release builds use R8 minification.
5. Privacy policy: see [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

Before Play Store release:
1. Configure Firebase Crashlytics (optional) by binding a Crashlytics implementation to `CrashReporter`.
2. Add store listing assets (screenshots, feature graphic).
3. Record and commit Paparazzi golden images if visual regression testing is desired in CI.

## k. Cross-platform delivery notes
If transitioning to Kotlin Multiplatform (KMP), the Domain layer (`GetRankedActivitiesUseCase`, `WeatherRepository` interface) and Data layer (Ktor instead of Retrofit, `kotlinx.serialization`, Room/SQLDelight) can be entirely shared. Only the UI layer (Compose Android vs Compose Multiplatform/SwiftUI) and platform-specific DI (e.g., Koin instead of Hilt) would need distinct implementations.

## l. AI usage disclosure
This codebase was generated and structured with the assistance of an advanced AI agent (Google Deepmind Antigravity). The AI orchestrated the transition from a basic MVVM app to a robust Senior-level architecture involving Dagger Hilt, Offline-First/Room SSOT, Strategy Pattern heuristics, and Turbine reactive tests. All AI-generated code is verified by compiling the project using the Gradle wrapper and running the unit test suite.
